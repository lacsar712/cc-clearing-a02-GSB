package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.FxRateJpaRepository;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.port.out.FxRateRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class FxRateRepositoryAdapter implements FxRateRepositoryPort {

    private final FxRateJpaRepository repository;

    public FxRateRepositoryAdapter(FxRateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public FxRate save(FxRate rate) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(rate)));
    }

    @Override
    public List<FxRate> findAllOrderByEffectiveDateDesc() {
        return repository.findAllByOrderByEffectiveDateDescBaseCurrencyAscQuoteCurrencyAsc().stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<FxRate> findById(String rateId) {
        return repository.findById(rateId).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<FxRate> findLatestEffective(String baseCurrency, String quoteCurrency, LocalDate asOfDate) {
        return repository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        baseCurrency, quoteCurrency, asOfDate)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<FxRate> findByPairAndEffectiveDate(String baseCurrency, String quoteCurrency, LocalDate effectiveDate) {
        return repository
                .findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(baseCurrency, quoteCurrency, effectiveDate)
                .map(PersistenceMapper::toDomain);
    }
}
