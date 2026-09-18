package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * FX rate book entry: 1 unit of baseCurrency = rate units of quoteCurrency,
 * effective from effectiveDate. Maintained by operators; read-only for viewers.
 */
public class FxRate {
    private final String rateId;
    private final String baseCurrency;
    private final String quoteCurrency;
    private final BigDecimal rate;
    private final LocalDate effectiveDate;

    public FxRate(String rateId, String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        this.rateId = Objects.requireNonNull(rateId);
        this.baseCurrency = Objects.requireNonNull(baseCurrency).toUpperCase();
        this.quoteCurrency = Objects.requireNonNull(quoteCurrency).toUpperCase();
        if (this.baseCurrency.equals(this.quoteCurrency)) {
            throw new IllegalArgumentException("base and quote currency must differ");
        }
        this.rate = Objects.requireNonNull(rate).setScale(10, RoundingMode.HALF_UP);
        if (this.rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        this.effectiveDate = Objects.requireNonNull(effectiveDate);
    }

    public static FxRate of(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        return new FxRate(UUID.randomUUID().toString(), baseCurrency, quoteCurrency, rate, effectiveDate);
    }

    public FxRate withRate(BigDecimal newRate) {
        return new FxRate(this.rateId, this.baseCurrency, this.quoteCurrency, newRate, this.effectiveDate);
    }

    public String pairKey() {
        return baseCurrency + "->" + quoteCurrency;
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
