package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.ConvertedNetLine;
import com.clearing.netting.domain.model.FxRate;
import com.clearing.netting.domain.model.NetPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxConversionServiceTest {

    private FxConversionService service;
    private LocalDate settleDate;

    @BeforeEach
    void setUp() {
        service = new FxConversionService();
        settleDate = LocalDate.of(2026, 9, 17);
    }

    @Test
    void convertsPositionsWithBookRates() {
        List<NetPosition> positions = List.of(
                position("A", "USD", "-60"),
                position("A", "EUR", "20"),
                position("B", "USD", "60"),
                position("B", "EUR", "-20"));
        List<FxRate> book = List.of(FxRate.of("EUR", "USD", new BigDecimal("1.08"), settleDate));

        List<ConvertedNetLine> lines = service.convert("USD", settleDate, positions, book);
        Map<String, BigDecimal> byKey = lines.stream().collect(Collectors.toMap(
                l -> l.memberId() + "/" + l.currency(), ConvertedNetLine::convertedAmount));

        assertEquals(0, byKey.get("A/USD").compareTo(new BigDecimal("-60.00000000")));
        assertEquals(0, byKey.get("A/EUR").compareTo(new BigDecimal("21.60000000")));
        assertEquals(0, byKey.get("B/USD").compareTo(new BigDecimal("60.00000000")));
        assertEquals(0, byKey.get("B/EUR").compareTo(new BigDecimal("-21.60000000")));

        ConvertedNetLine usdLine = lines.stream().filter(l -> l.currency().equals("USD")).findFirst().orElseThrow();
        assertEquals(0, usdLine.rate().compareTo(new BigDecimal("1.0000000000")));
    }

    @Test
    void sameCurrencyNeedsNoRateRow() {
        List<NetPosition> positions = List.of(position("A", "USD", "100"));
        List<ConvertedNetLine> lines = service.convert("USD", settleDate, positions, List.of());
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).convertedAmount().compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void missingRateFailsAndNamesPair() {
        List<NetPosition> positions = List.of(position("A", "EUR", "20"));

        DomainException ex = assertThrows(DomainException.class, () ->
                service.convert("USD", settleDate, positions, List.of()));
        assertEquals("FX_RATE_MISSING", ex.getCode());
        assertTrue(ex.getMessage().contains("EUR->USD"));
    }

    @Test
    void missingRateNamesEveryMissingPair() {
        List<NetPosition> positions = List.of(
                position("A", "EUR", "20"),
                position("B", "CNY", "700"));

        DomainException ex = assertThrows(DomainException.class, () ->
                service.convert("USD", settleDate, positions, List.of()));
        assertTrue(ex.getMessage().contains("EUR->USD"));
        assertTrue(ex.getMessage().contains("CNY->USD"));
    }

    @Test
    void usesLatestRateEffectiveOnOrBeforeSettleDate() {
        List<NetPosition> positions = List.of(position("A", "EUR", "10"));
        List<FxRate> book = List.of(
                FxRate.of("EUR", "USD", new BigDecimal("1.05"), settleDate.minusDays(10)),
                FxRate.of("EUR", "USD", new BigDecimal("1.08"), settleDate.minusDays(1)),
                FxRate.of("EUR", "USD", new BigDecimal("9.99"), settleDate.plusDays(1)));

        List<ConvertedNetLine> lines = service.convert("USD", settleDate, positions, book);
        assertEquals(0, lines.get(0).rate().compareTo(new BigDecimal("1.0800000000")));
        assertEquals(0, lines.get(0).convertedAmount().compareTo(new BigDecimal("10.80000000")));
    }

    @Test
    void futureDatedRateAloneCountsAsMissing() {
        List<NetPosition> positions = List.of(position("A", "EUR", "10"));
        List<FxRate> book = List.of(FxRate.of("EUR", "USD", new BigDecimal("1.08"), settleDate.plusDays(1)));

        DomainException ex = assertThrows(DomainException.class, () ->
                service.convert("USD", settleDate, positions, book));
        assertEquals("FX_RATE_MISSING", ex.getCode());
    }

    @Test
    void changedRateChangesConvertedAmount() {
        List<NetPosition> positions = List.of(position("A", "EUR", "10"));
        List<ConvertedNetLine> before = service.convert(
                "USD", settleDate, positions,
                List.of(FxRate.of("EUR", "USD", new BigDecimal("1.08"), settleDate)));
        List<ConvertedNetLine> after = service.convert(
                "USD", settleDate, positions,
                List.of(FxRate.of("EUR", "USD", new BigDecimal("1.20"), settleDate)));

        assertEquals(0, before.get(0).convertedAmount().compareTo(new BigDecimal("10.80000000")));
        assertEquals(0, after.get(0).convertedAmount().compareTo(new BigDecimal("12.00000000")));
    }

    private NetPosition position(String memberId, String currency, String amount) {
        return NetPosition.of("run-1", memberId, currency, new BigDecimal(amount));
    }
}
