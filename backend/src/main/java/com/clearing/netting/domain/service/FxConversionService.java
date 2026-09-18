package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.port.out.FxRateRepositoryPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts amounts between currencies using FX rates effective on/before a given date.
 *
 * <p>Resolution order for {@code from -> to} on {@code asOfDate}:
 * <ol>
 *   <li>same currency: factor 1 (identity, not a default)</li>
 *   <li>direct rate {@code from -> to}: factor = rate</li>
 *   <li>inverse rate {@code to -> from}: factor = 1 / rate</li>
 * </ol>
 *
 * <p>Missing pairs fail hard with {@code FX_RATE_NOT_FOUND}; conversion never
 * silently assumes 1 for two different currencies.
 */
@Service
public class FxConversionService {

    private final FxRateRepositoryPort rateRepository;

    public FxConversionService(FxRateRepositoryPort rateRepository) {
        this.rateRepository = rateRepository;
    }

    /**
     * Resolves the conversion factor for a single currency pair.
     */
    public Conversion resolve(String fromCurrency, String toCurrency, LocalDate asOfDate) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (asOfDate == null) {
            throw new DomainException("INVALID_DATE", "asOfDate is required");
        }

        if (from.equals(to)) {
            return Conversion.identity(from, to);
        }

        FxRate direct = rateRepository.findLatestEffective(from, to, asOfDate).orElse(null);
        if (direct != null) {
            return new Conversion(from, to, direct.getRate(), direct.getEffectiveDate(),
                    direct.getRateId(), Direction.DIRECT);
        }

        FxRate inverse = rateRepository.findLatestEffective(to, from, asOfDate).orElse(null);
        if (inverse != null) {
            BigDecimal factor = BigDecimal.ONE.divide(inverse.getRate(), 16, RoundingMode.HALF_UP);
            return new Conversion(from, to, factor, inverse.getEffectiveDate(),
                    inverse.getRateId(), Direction.INVERSE);
        }

        throw new DomainException("MISSING_FX_RATE",
                "missing FX rate for currency pair: " + pairLabel(from, to)
                        + " effective on/before " + asOfDate);
    }

    /**
     * Resolves every distinct pair first; if any pair lacks a rate, fails once and lists
     * ALL missing pairs (sorted, de-duplicated) instead of failing on the first row.
     */
    public List<ResolvedPosition> convertAll(List<PositionAmount> positions, String targetCurrency, LocalDate asOfDate) {
        String target = normalize(targetCurrency);
        if (asOfDate == null) {
            throw new DomainException("INVALID_DATE", "settleDate is required");
        }
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }

        Set<String> distinctPairs = new LinkedHashSet<>();
        for (PositionAmount p : positions) {
            String from = normalize(p.currency());
            if (!from.equals(target)) {
                distinctPairs.add(pairLabel(from, target));
            }
        }

        List<String> missing = new ArrayList<>();
        for (String label : distinctPairs) {
            String[] parts = label.split("/");
            if (rateRepository.findLatestEffective(parts[0], parts[1], asOfDate).isEmpty()
                    && rateRepository.findLatestEffective(parts[1], parts[0], asOfDate).isEmpty()) {
                missing.add(label);
            }
        }
        if (!missing.isEmpty()) {
            throw new DomainException("MISSING_FX_RATE",
                    "missing FX rate for currency pair(s): " + String.join(", ", missing)
                            + " effective on/before " + asOfDate);
        }

        List<ResolvedPosition> rows = new ArrayList<>();
        for (PositionAmount p : positions) {
            Conversion c = resolve(p.currency(), target, asOfDate);
            BigDecimal converted = p.amount().multiply(c.factor()).setScale(8, RoundingMode.HALF_UP);
            rows.add(new ResolvedPosition(p.key(), p.memberId(), p.memberName(),
                    normalize(p.currency()), p.amount(), target, converted, c));
        }
        return rows;
    }

    private static String normalize(String ccy) {
        if (ccy == null || ccy.trim().isEmpty()) {
            throw new DomainException("INVALID_CURRENCY", "currency is required");
        }
        return ccy.trim().toUpperCase();
    }

    private static String pairLabel(String from, String to) {
        return from + "/" + to;
    }

    public enum Direction {
        SAME,
        DIRECT,
        INVERSE
    }

    /**
     * A net position in a single source currency for one member.
     */
    public record PositionAmount(String key, String memberId, String memberName,
                                 String currency, BigDecimal amount) {
    }

    public record Conversion(String fromCurrency, String toCurrency, BigDecimal factor,
                             LocalDate rateEffectiveDate, String rateId, Direction direction) {
        static Conversion identity(String from, String to) {
            return new Conversion(from, to, BigDecimal.ONE, null, null, Direction.SAME);
        }
    }

    public record ResolvedPosition(String key, String memberId, String memberName,
                                   String sourceCurrency, BigDecimal netAmount,
                                   String targetCurrency, BigDecimal convertedAmount,
                                   Conversion conversion) {
    }
}
