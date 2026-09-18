package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.ConvertedNetLine;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.model.NetPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure domain service: converts net positions into a target currency using the FX rate book.
 * Same-currency amounts pass through at rate 1 (identity, no book entry needed); every
 * cross-currency amount requires an explicit rate effective on or before the settle date.
 * A missing rate is an error naming the pair — never silently defaulted to 1.
 */
public class FxConversionService {

    public List<ConvertedNetLine> convert(
            String targetCurrency,
            LocalDate settleDate,
            List<NetPosition> positions,
            List<FxRate> rateBook) {

        String target = targetCurrency.toUpperCase();
        Map<String, BigDecimal> resolved = resolveRates(currenciesOf(positions), target, settleDate, rateBook);

        List<ConvertedNetLine> lines = new ArrayList<>();
        for (NetPosition p : positions) {
            String from = p.getCurrency().toUpperCase();
            BigDecimal rate = from.equals(target)
                    ? BigDecimal.ONE.setScale(10, RoundingMode.HALF_UP)
                    : resolved.get(from);
            BigDecimal converted = p.getNetAmount().multiply(rate).setScale(8, RoundingMode.HALF_UP);
            lines.add(new ConvertedNetLine(p.getMemberId(), from, p.getNetAmount(), rate, converted));
        }
        lines.sort(Comparator.comparing(ConvertedNetLine::memberId).thenComparing(ConvertedNetLine::currency));
        return lines;
    }

    private Set<String> currenciesOf(List<NetPosition> positions) {
        Set<String> currencies = new TreeSet<>();
        for (NetPosition p : positions) {
            currencies.add(p.getCurrency().toUpperCase());
        }
        return currencies;
    }

    private Map<String, BigDecimal> resolveRates(
            Set<String> fromCurrencies, String target, LocalDate settleDate, List<FxRate> rateBook) {
        Map<String, BigDecimal> resolved = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String from : fromCurrencies) {
            if (from.equals(target)) {
                continue;
            }
            Optional<FxRate> rate = latestRate(rateBook, from, target, settleDate);
            if (rate.isPresent()) {
                resolved.put(from, rate.get().getRate());
            } else {
                missing.add(from + "->" + target);
            }
        }
        if (!missing.isEmpty()) {
            throw new DomainException(
                    "FX_RATE_MISSING",
                    "missing FX rate for pair(s) " + String.join(", ", missing)
                            + " effective on/before " + settleDate);
        }
        return resolved;
    }

    private Optional<FxRate> latestRate(List<FxRate> rateBook, String base, String quote, LocalDate onOrBefore) {
        return rateBook.stream()
                .filter(r -> r.getBaseCurrency().equals(base) && r.getQuoteCurrency().equals(quote))
                .filter(r -> !r.getEffectiveDate().isAfter(onOrBefore))
                .max(Comparator.comparing(FxRate::getEffectiveDate));
    }
}
