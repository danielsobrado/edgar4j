package org.jds.edgar4j.controller;

import java.time.LocalDate;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.job.MarketDataSyncJob;
import org.jds.edgar4j.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Historical price and company market-data operations")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final MarketDataSyncJob marketDataSyncJob;

    @Operation(summary = "Get historical daily prices for a ticker")
    @GetMapping("/prices/{ticker}")
    public ResponseEntity<ApiResponse<MarketDataResponse>> getDailyPrices(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            MarketDataResponse response = marketDataService.getDailyPrices(
                    ticker,
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Failed to get market data for {}: {}", ticker, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "Run a dedicated market-cap backfill pass",
            description = "Refreshes tracked tickers that are still missing market-cap data. "
                    + "The pass uses the configured market-data providers and is safe to rerun until deferred and unresolved counts reach zero.")
    @PostMapping("/backfill/market-cap")
    public ResponseEntity<ApiResponse<MarketCapBackfillResponse>> backfillMarketCaps(
            @Parameter(description = "Maximum number of candidate tickers to refresh in this pass")
            @RequestParam(defaultValue = "250") int maxTickers,
            @Parameter(description = "Lookback window used to include insider-active tickers")
            @RequestParam(defaultValue = "30") int lookbackDays) {
        log.info("POST /api/market-data/backfill/market-cap?maxTickers={}&lookbackDays={}", maxTickers, lookbackDays);

        if (maxTickers < 1) {
            return ResponseEntity.badRequest().body(ApiResponse.error("maxTickers must be greater than 0"));
        }

        if (lookbackDays < 1) {
            return ResponseEntity.badRequest().body(ApiResponse.error("lookbackDays must be greater than 0"));
        }

        try {
            MarketCapBackfillResponse response = marketDataSyncJob.triggerMarketCapBackfill(maxTickers, lookbackDays);
            return ResponseEntity.ok(ApiResponse.success(response, "Market-cap backfill completed"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
