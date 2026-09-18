package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.FxRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FxRateRepositoryPort {
    FxRate save(FxRate rate);

    List<FxRate> findAll();

    Optional<FxRate> findByPairAndEffectiveDate(String baseCurrency, String quoteCurrency, LocalDate effectiveDate);
}
