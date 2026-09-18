package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.service.FxConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FxReportApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final MemberRepositoryPort memberRepository;
    private final FxConversionService conversionService;

    public FxReportApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            MemberRepositoryPort memberRepository,
            FxConversionService conversionService) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.memberRepository = memberRepository;
        this.conversionService = conversionService;
    }

    @Transactional(readOnly = true)
    public FxReport build(LocalDate settleDate, String targetCurrency) {
        if (settleDate == null) {
            throw new DomainException("INVALID_DATE", "settleDate is required");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "targetCurrency is required");
        }
        String target = targetCurrency.trim().toUpperCase();

        List<NettingRun> runs = runRepository.findBySettleDateAndStatus(settleDate, NettingRunStatus.COMPLETED);
        List<String> runIds = runs.stream().map(NettingRun::getRunId).toList();
        List<NetPosition> positions = positionRepository.findByRunIdIn(runIds);

        Set<String> memberIds = new HashSet<>();
        for (NetPosition p : positions) {
            memberIds.add(p.getMemberId());
        }
        Map<String, Member> members = new HashMap<>();
        for (Member m : memberRepository.findByIds(memberIds)) {
            members.put(m.getMemberId(), m);
        }

        // Aggregate net positions per member + source currency across the day's completed runs.
        Map<String, BigDecimal> agg = new LinkedHashMap<>();
        Map<String, String> keyMeta = new HashMap<>();
        for (NetPosition p : positions) {
            String key = p.getMemberId() + "|" + p.getCurrency();
            agg.merge(key, p.getNetAmount(), BigDecimal::add);
            keyMeta.putIfAbsent(key, p.getCurrency());
        }

        List<FxConversionService.PositionAmount> input = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : agg.entrySet()) {
            String memberId = e.getKey().substring(0, e.getKey().indexOf('|'));
            String currency = keyMeta.get(e.getKey());
            Member m = members.get(memberId);
            input.add(new FxConversionService.PositionAmount(
                    e.getKey(), memberId, m == null ? null : m.getName(), currency,
                    e.getValue().setScale(8, RoundingMode.HALF_UP)));
        }
        input.sort(Comparator.comparing(FxConversionService.PositionAmount::memberId)
                .thenComparing(FxConversionService.PositionAmount::currency));

        // Fails with FX_RATE_NOT_FOUND listing every missing pair — never defaults to 1.
        List<FxConversionService.ResolvedPosition> rows = conversionService.convertAll(input, target, settleDate);

        Map<String, BigDecimal> totalsBySource = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        List<FxReportLine> lines = new ArrayList<>();
        for (FxConversionService.ResolvedPosition r : rows) {
            BigDecimal net = r.netAmount().setScale(8, RoundingMode.HALF_UP);
            BigDecimal converted = r.convertedAmount().setScale(8, RoundingMode.HALF_UP);
            totalsBySource.merge(r.sourceCurrency(), net, BigDecimal::add);
            grandTotal = grandTotal.add(converted);
            FxConversionService.Conversion c = r.conversion();
            lines.add(new FxReportLine(
                    r.memberId(), r.memberName(),
                    r.sourceCurrency(), net,
                    target, converted,
                    c.factor().setScale(8, RoundingMode.HALF_UP),
                    c.direction().name(),
                    c.rateEffectiveDate(),
                    c.rateId()));
        }

        List<CurrencyTotal> currencyTotals = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : totalsBySource.entrySet()) {
            currencyTotals.add(new CurrencyTotal(e.getKey(),
                    e.getValue().setScale(8, RoundingMode.HALF_UP)));
        }

        return new FxReport(
                settleDate,
                target,
                runIds.size(),
                lines,
                currencyTotals,
                grandTotal.setScale(8, RoundingMode.HALF_UP));
    }

    public record FxReportLine(
            String memberId,
            String memberName,
            String sourceCurrency,
            BigDecimal netAmount,
            String targetCurrency,
            BigDecimal convertedAmount,
            BigDecimal rateFactor,
            String rateDirection,
            LocalDate rateEffectiveDate,
            String rateId) {
    }

    public record CurrencyTotal(String currency, BigDecimal netAmount) {
    }

    public record FxReport(
            LocalDate settleDate,
            String targetCurrency,
            int runCount,
            List<FxReportLine> lines,
            List<CurrencyTotal> currencyTotals,
            BigDecimal grandTotalConverted) {
    }
}
