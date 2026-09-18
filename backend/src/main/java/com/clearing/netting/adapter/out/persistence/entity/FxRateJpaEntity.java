package com.clearing.netting.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fx_rates", uniqueConstraints = @UniqueConstraint(
        name = "uk_fx_pair_date", columnNames = {"baseCurrency", "quoteCurrency", "effectiveDate"}))
public class FxRateJpaEntity {

    @Id
    @Column(length = 64)
    private String rateId;

    @Column(nullable = false, length = 8)
    private String baseCurrency;

    @Column(nullable = false, length = 8)
    private String quoteCurrency;

    @Column(nullable = false, precision = 28, scale = 10)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    public String getRateId() {
        return rateId;
    }

    public void setRateId(String rateId) {
        this.rateId = rateId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
