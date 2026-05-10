package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.job.MarketDataSyncJob;
import org.jds.edgar4j.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MarketDataControllerTest {

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private MarketDataSyncJob marketDataSyncJob;

    private MarketDataController marketDataController;

    @BeforeEach
    void setUp() {
        marketDataController = new MarketDataController(marketDataService, marketDataSyncJob);
    }

    @Test
    @DisplayName("backfillMarketCaps should allow zero maxTickers and delegate to job")
    void backfillMarketCapsAllowsZeroMaxTickers() {
        MarketCapBackfillResponse expected = MarketCapBackfillResponse.builder()
                .trackedTickers(1)
                .candidateTickers(1)
                .processedTickers(1)
                .updatedTickers(1)
                .build();
        when(marketDataSyncJob.triggerMarketCapBackfill(0, 30)).thenReturn(expected);

        ResponseEntity<ApiResponse<MarketCapBackfillResponse>> response = marketDataController.backfillMarketCaps(0, 30);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Market-cap backfill completed", response.getBody().getMessage());
        verify(marketDataSyncJob).triggerMarketCapBackfill(0, 30);
    }

    @Test
    @DisplayName("backfillMarketCaps should reject requests while the sync job is already running")
    void backfillMarketCapsShouldRejectWhenSyncRunning() {
        when(marketDataSyncJob.triggerMarketCapBackfill(250, 30))
            .thenThrow(new IllegalStateException("Market data sync job is already running"));

        ResponseEntity<ApiResponse<MarketCapBackfillResponse>> response = marketDataController.backfillMarketCaps(250, 30);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Market data sync job is already running", response.getBody().getMessage());
    }

    @Test
    @DisplayName("backfillMarketCaps should return the backfill summary")
    void backfillMarketCapsShouldReturnSummary() {
        MarketCapBackfillResponse expected = MarketCapBackfillResponse.builder()
                .trackedTickers(42)
                .candidateTickers(5)
                .processedTickers(5)
                .updatedTickers(4)
                .unresolvedTickersCount(1)
                .build();

        when(marketDataSyncJob.triggerMarketCapBackfill(250, 30)).thenReturn(expected);

        ResponseEntity<ApiResponse<MarketCapBackfillResponse>> response = marketDataController.backfillMarketCaps(250, 30);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertSame(expected, response.getBody().getData());
        assertEquals("Market-cap backfill completed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("getDailyPrices should delegate when date range is valid")
    void getDailyPricesShouldDelegateWhenDateRangeIsValid() {
        String ticker = "AAPL";
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 5, 10);
        MarketDataResponse expected = MarketDataResponse.builder()
                .ticker(ticker)
                .provider("unit-test")
                .startDate(startDate)
                .endDate(endDate)
                .prices(List.of())
                .build();

        when(marketDataService.getDailyPrices(ticker, startDate, endDate)).thenReturn(expected);

        ResponseEntity<ApiResponse<MarketDataResponse>> response =
                marketDataController.getDailyPrices(ticker, startDate, endDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(expected, response.getBody().getData());
        verify(marketDataService).getDailyPrices(ticker, startDate, endDate);
    }

    @Test
    @DisplayName("getDailyPrices should reject invalid date range")
    void getDailyPricesShouldRejectInvalidDateRange() {
        String ticker = "AAPL";
        LocalDate startDate = LocalDate.of(2026, 5, 10);
        LocalDate endDate = LocalDate.of(2026, 5, 1);

        ResponseEntity<ApiResponse<MarketDataResponse>> response =
                marketDataController.getDailyPrices(ticker, startDate, endDate);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("startDate must be before or equal to endDate", response.getBody().getMessage());
        verify(marketDataService, never()).getDailyPrices(ticker, startDate, endDate);
    }
}
