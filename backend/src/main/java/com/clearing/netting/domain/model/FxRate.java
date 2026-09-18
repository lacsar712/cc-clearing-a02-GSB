package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Foreign-exchange rate for a directed currency pair (base -> quote),
 * effective from {@code effectiveDate}.
 *
 * <p>Rate semantics: one unit of base currency = {@code rate} units of quote currency.
 */
public class FxRate {
    private final String rateId;
    private final String baseCurrency;
    private final String quoteCurrency;
    private final BigDecimal rate;
    private final LocalDate effectiveDate;

    public FxRate(String rateId, String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        this.rateId = Objects.requireNonNull(rateId);
        this.baseCurrency = normalize(baseCurrency);
        this.quoteCurrency = normalize(quoteCurrency);
        if (this.baseCurrency.equals(this.quoteCurrency)) {
            throw new IllegalArgumentException("base and quote currency must differ");
        }
        this.rate = Objects.requireNonNull(rate).setScale(8, RoundingMode.HALF_UP);
        if (this.rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        this.effectiveDate = Objects.requireNonNull(effectiveDate);
    }

    public static FxRate of(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        return new FxRate(UUID.randomUUID().toString(), baseCurrency, quoteCurrency, rate, effectiveDate);
    }

    /**
     * Returns the factor to multiply an amount in base currency by to obtain quote currency.
     */
    public BigDecimal toQuoteFactor() {
        return rate;
    }

    /**
     * Returns the factor to multiply an amount in quote currency by to obtain base currency
     * (the inverse rate).
     */
    public BigDecimal toBaseFactor() {
        return BigDecimal.ONE.divide(rate, 16, RoundingMode.HALF_UP);
    }

    private static String normalize(String ccy) {
        Objects.requireNonNull(ccy, "currency is required");
        String trimmed = ccy.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("currency is required");
        }
        return trimmed.toUpperCase();
    }

    public String getRateId() {
        return rateId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
}
