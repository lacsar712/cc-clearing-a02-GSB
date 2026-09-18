package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.FxRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FxRateRepositoryPort {
    FxRate save(FxRate rate);

    List<FxRate> findAllOrderByEffectiveDateDesc();

    Optional<FxRate> findById(String rateId);

    /**
     * Most recent rate for the exact directed pair whose effectiveDate is on/before the given date.
     */
    Optional<FxRate> findLatestEffective(String baseCurrency, String quoteCurrency, LocalDate asOfDate);

    /**
     * Lookup by the natural key (pair + effective date); used to keep seed idempotent.
     */
    Optional<FxRate> findByPairAndEffectiveDate(String baseCurrency, String quoteCurrency, LocalDate effectiveDate);
}
