package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.port.out.FxRateRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxConversionServiceTest {

    private FakeFxRateRepository repository;
    private FxConversionService service;
    private static final LocalDate D = LocalDate.of(2026, 9, 17);

    @BeforeEach
    void setUp() {
        repository = new FakeFxRateRepository();
        service = new FxConversionService(repository);
    }

    @Test
    void sameCurrencyIsIdentityNotDefaultedRate() {
        FxConversionService.Conversion c = service.resolve("USD", "USD", D);
        assertEquals(FxConversionService.Direction.SAME, c.direction());
        assertEquals(0, c.factor().compareTo(BigDecimal.ONE));
    }

    @Test
    void directRateUsed() {
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.10"), D.minusDays(2)));
        FxConversionService.Conversion c = service.resolve("EUR", "USD", D);
        assertEquals(FxConversionService.Direction.DIRECT, c.direction());
        assertEquals(0, c.factor().compareTo(new BigDecimal("1.10")));
    }

    @Test
    void inverseRateUsedWhenOnlyReversePairExists() {
        repository.save(FxRate.of("USD", "CNY", new BigDecimal("7.20"), D.minusDays(1)));
        FxConversionService.Conversion c = service.resolve("CNY", "USD", D);
        assertEquals(FxConversionService.Direction.INVERSE, c.direction());
        // 1 / 7.2
        assertEquals(0, c.factor().compareTo(BigDecimal.ONE.divide(new BigDecimal("7.20"), 16,
                java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void missingPairFailsHardAndNamesPairNeverAssumesOne() {
        DomainException ex = assertThrows(DomainException.class, () -> service.resolve("EUR", "USD", D));
        assertEquals("MISSING_FX_RATE", ex.getCode());
        assertTrue(ex.getMessage().contains("EUR/USD"), "message must name the missing pair: " + ex.getMessage());
    }

    @Test
    void futureEffectiveRateIsNotUsed() {
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.10"), D.plusDays(1)));
        DomainException ex = assertThrows(DomainException.class, () -> service.resolve("EUR", "USD", D));
        assertEquals("MISSING_FX_RATE", ex.getCode());
    }

    @Test
    void mostRecentRateOnOrBeforeDateWins() {
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.08"), D.minusDays(10)));
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.12"), D.minusDays(1)));
        FxConversionService.Conversion c = service.resolve("EUR", "USD", D);
        assertEquals(0, c.factor().compareTo(new BigDecimal("1.12")));
    }

    @Test
    void convertAllFailsAndListsEveryMissingPair() {
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.10"), D));
        List<FxConversionService.PositionAmount> positions = List.of(
                pos("M1", "EUR", "100"),
                pos("M2", "JPY", "5000"),
                pos("M3", "GBP", "20"));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.convertAll(positions, "USD", D));
        assertEquals("MISSING_FX_RATE", ex.getCode());
        assertTrue(ex.getMessage().contains("JPY/USD"), ex.getMessage());
        assertTrue(ex.getMessage().contains("GBP/USD"), ex.getMessage());
        // EUR/USD exists and must not be reported missing
        assertTrue(!ex.getMessage().contains("EUR/USD"), ex.getMessage());
    }

    @Test
    void convertAllConvertsMixedCurrenciesAndKeepsSigns() {
        repository.save(FxRate.of("EUR", "USD", new BigDecimal("1.10"), D));
        repository.save(FxRate.of("USD", "CNY", new BigDecimal("7.20"), D));
        List<FxConversionService.PositionAmount> positions = List.of(
                pos("M1", "USD", "-60"),
                pos("M2", "EUR", "40"),
                pos("M3", "CNY", "72"));

        List<FxConversionService.ResolvedPosition> rows = service.convertAll(positions, "USD", D);
        assertEquals(3, rows.size());
        // -60 USD stays -60
        assertEquals(0, rows.get(0).convertedAmount().compareTo(new BigDecimal("-60.00000000")));
        // 40 EUR * 1.10 = 44
        assertEquals(0, rows.get(1).convertedAmount().compareTo(new BigDecimal("44.00000000")));
        // 72 CNY / 7.20 = 10
        assertEquals(0, rows.get(2).convertedAmount().compareTo(new BigDecimal("10.00000000")));
        // no silent 1: the CNY row went through an inverse rate
        assertEquals(FxConversionService.Direction.INVERSE, rows.get(2).conversion().direction());
    }

    private FxConversionService.PositionAmount pos(String member, String ccy, String amount) {
        return new FxConversionService.PositionAmount(
                member + "|" + ccy, member, member + " name", ccy, new BigDecimal(amount));
    }

    /** Minimal in-memory implementation of the FX rate port. */
    private static class FakeFxRateRepository implements FxRateRepositoryPort {
        private final List<FxRate> rates = new ArrayList<>();
        private final Map<String, Integer> index = new HashMap<>();

        @Override
        public FxRate save(FxRate rate) {
            String key = rate.getBaseCurrency() + "/" + rate.getQuoteCurrency() + "@" + rate.getEffectiveDate();
            Integer i = index.get(key);
            if (i == null) {
                index.put(key, rates.size());
                rates.add(rate);
            } else {
                rates.set(i, rate);
            }
            return rate;
        }

        @Override
        public List<FxRate> findAllOrderByEffectiveDateDesc() {
            return List.copyOf(rates);
        }

        @Override
        public Optional<FxRate> findById(String rateId) {
            return rates.stream().filter(r -> r.getRateId().equals(rateId)).findFirst();
        }

        @Override
        public Optional<FxRate> findLatestEffective(String base, String quote, LocalDate asOf) {
            return rates.stream()
                    .filter(r -> r.getBaseCurrency().equals(base)
                            && r.getQuoteCurrency().equals(quote)
                            && !r.getEffectiveDate().isAfter(asOf))
                    .max((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()));
        }

        @Override
        public Optional<FxRate> findByPairAndEffectiveDate(String base, String quote, LocalDate effectiveDate) {
            return rates.stream()
                    .filter(r -> r.getBaseCurrency().equals(base)
                            && r.getQuoteCurrency().equals(quote)
                            && r.getEffectiveDate().equals(effectiveDate))
                    .findFirst();
        }
    }
}
