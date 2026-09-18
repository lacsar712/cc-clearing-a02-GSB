package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.ConvertedNetLine;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.port.out.FxRateRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.service.FxConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FxReportApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final FxRateRepositoryPort fxRateRepository;
    private final FxConversionService conversionService;

    public FxReportApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            FxRateRepositoryPort fxRateRepository) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.fxRateRepository = fxRateRepository;
        this.conversionService = new FxConversionService();
    }

    @Transactional(readOnly = true)
    public ConvertedNetReport convertedNet(LocalDate settleDate, String targetCurrency) {
        if (settleDate == null) {
            throw new DomainException("INVALID_DATE", "settleDate is required");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "targetCurrency is required");
        }
        String target = targetCurrency.trim().toUpperCase();

        List<NettingRun> runs = runRepository.findBySettleDateAndStatus(settleDate, NettingRunStatus.COMPLETED);
        List<NetPosition> positions = new ArrayList<>();
        for (NettingRun run : runs) {
            positions.addAll(positionRepository.findByRunId(run.getRunId()));
        }

        List<ConvertedNetLine> lines =
                conversionService.convert(target, settleDate, positions, fxRateRepository.findAll());

        Map<String, BigDecimal> totalsByMember = new TreeMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        for (ConvertedNetLine line : lines) {
            totalsByMember.merge(line.memberId(), line.convertedAmount(), BigDecimal::add);
            grandTotal = grandTotal.add(line.convertedAmount());
        }
        List<MemberTotal> memberTotals = totalsByMember.entrySet().stream()
                .map(e -> new MemberTotal(e.getKey(), e.getValue().setScale(8, RoundingMode.HALF_UP)))
                .toList();

        return new ConvertedNetReport(
                settleDate, target, lines, memberTotals, grandTotal.setScale(8, RoundingMode.HALF_UP));
    }

    public record MemberTotal(String memberId, BigDecimal totalConverted) {
    }

    public record ConvertedNetReport(
            LocalDate settleDate,
            String targetCurrency,
            List<ConvertedNetLine> lines,
            List<MemberTotal> memberTotals,
            BigDecimal grandTotal) {
    }
}
