package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.port.out.FxRateRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FxRateApplicationService {

    private final FxRateRepositoryPort rateRepository;

    public FxRateApplicationService(FxRateRepositoryPort rateRepository) {
        this.rateRepository = rateRepository;
    }

    @Transactional(readOnly = true)
    public List<FxRate> list() {
        return rateRepository.findAllOrderByEffectiveDateDesc();
    }

    @Transactional
    public FxRate create(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        validate(baseCurrency, quoteCurrency, rate, effectiveDate);
        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();
        if (rateRepository.findByPairAndEffectiveDate(base, quote, effectiveDate).isPresent()) {
            throw new DomainException("FX_RATE_DUPLICATE",
                    "rate already exists for pair " + base + "/" + quote + " on " + effectiveDate);
        }
        return rateRepository.save(FxRate.of(base, quote, rate, effectiveDate));
    }

    @Transactional
    public FxRate update(String rateId, BigDecimal rate, LocalDate effectiveDate) {
        FxRate existing = rateRepository.findById(rateId)
                .orElseThrow(() -> new DomainException("FX_RATE_NOT_FOUND", "fx rate not found: " + rateId));
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_RATE", "rate must be positive");
        }
        if (effectiveDate == null) {
            throw new DomainException("INVALID_DATE", "effectiveDate is required");
        }
        rateRepository.findByPairAndEffectiveDate(
                        existing.getBaseCurrency(), existing.getQuoteCurrency(), effectiveDate)
                .filter(other -> !other.getRateId().equals(rateId))
                .ifPresent(other -> {
                    throw new DomainException("FX_RATE_DUPLICATE",
                            "rate already exists for pair " + existing.getBaseCurrency() + "/"
                                    + existing.getQuoteCurrency() + " on " + effectiveDate);
                });
        return rateRepository.save(new FxRate(
                existing.getRateId(),
                existing.getBaseCurrency(),
                existing.getQuoteCurrency(),
                rate,
                effectiveDate));
    }

    private void validate(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "baseCurrency is required");
        }
        if (quoteCurrency == null || quoteCurrency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "quoteCurrency is required");
        }
        if (baseCurrency.trim().equalsIgnoreCase(quoteCurrency.trim())) {
            throw new DomainException("INVALID_CURRENCY", "base and quote currency must differ");
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_RATE", "rate must be positive");
        }
        if (effectiveDate == null) {
            throw new DomainException("INVALID_DATE", "effectiveDate is required");
        }
    }
}
