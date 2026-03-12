package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketDataServiceImplTest {

    @Test
    @DisplayName("Missing range detection ignores weekends and US market holidays")
    void shouldIgnoreWeekendsAndUsMarketHolidaysWhenFindingGaps() {
        List<MarketDataResponse.PriceBar> cachedPrices = List.of(
                bar("2026-01-16"),
                bar("2026-01-21"));

        List<MarketDataServiceImpl.DateRange> missingRanges = MarketDataServiceImpl.findMissingTradingRanges(
                cachedPrices,
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 21),
                LocalDate.of(2026, 1, 21));

        assertEquals(
                List.of(new MarketDataServiceImpl.DateRange(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 20))),
                missingRanges);
    }

    @Test
    @DisplayName("Future end dates do not create fake cache misses")
    void shouldIgnoreFutureDatesWhenFindingMissingRanges() {
        List<MarketDataResponse.PriceBar> cachedPrices = List.of(
                bar("2026-03-10"),
                bar("2026-03-11"),
                bar("2026-03-12"));

        List<MarketDataServiceImpl.DateRange> missingRanges = MarketDataServiceImpl.findMissingTradingRanges(
                cachedPrices,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 4, 12),
                LocalDate.of(2026, 3, 12));

        assertTrue(missingRanges.isEmpty());
    }

    @Test
    @DisplayName("Expected trading day recognises observed holidays")
    void shouldRecognizeObservedHolidayAsNonTradingDay() {
        assertFalse(MarketDataServiceImpl.isExpectedTradingDay(LocalDate.of(2021, 12, 31)));
        assertFalse(MarketDataServiceImpl.isExpectedTradingDay(LocalDate.of(2026, 1, 19)));
        assertTrue(MarketDataServiceImpl.isExpectedTradingDay(LocalDate.of(2026, 1, 20)));
    }

    private static MarketDataResponse.PriceBar bar(String date) {
        return MarketDataResponse.PriceBar.builder()
                .date(LocalDate.parse(date))
                .open(1.0)
                .high(1.0)
                .low(1.0)
                .close(1.0)
                .volume(1.0)
                .build();
    }
}
