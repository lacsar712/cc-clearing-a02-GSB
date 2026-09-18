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

    private final FxRateRepositoryPort fxRateRepository;

    public FxRateApplicationService(FxRateRepositoryPort fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    @Transactional(readOnly = true)
    public List<FxRate> list() {
        return fxRateRepository.findAll();
    }

    @Transactional
    public FxRate upsert(String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate effectiveDate) {
        if (baseCurrency == null || baseCurrency.isBlank() || quoteCurrency == null || quoteCurrency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "baseCurrency and quoteCurrency are required");
        }
        if (baseCurrency.trim().equalsIgnoreCase(quoteCurrency.trim())) {
            throw new DomainException("INVALID_CURRENCY_PAIR", "base and quote currency must differ");
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_FX_RATE", "rate must be positive");
        }
        if (effectiveDate == null) {
            throw new DomainException("INVALID_DATE", "effectiveDate is required");
        }
        FxRate candidate = FxRate.of(baseCurrency, quoteCurrency, rate, effectiveDate);
        return fxRateRepository
                .findByPairAndEffectiveDate(candidate.getBaseCurrency(), candidate.getQuoteCurrency(), effectiveDate)
                .map(existing -> fxRateRepository.save(existing.withRate(candidate.getRate())))
                .orElseGet(() -> fxRateRepository.save(candidate));
    }
}
