package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.FxRateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FxRateJpaRepository extends JpaRepository<FxRateJpaEntity, String> {

    Optional<FxRateJpaEntity> findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
            String baseCurrency, String quoteCurrency, LocalDate effectiveDate);

    List<FxRateJpaEntity> findAllByOrderByBaseCurrencyAscQuoteCurrencyAscEffectiveDateDesc();
}
