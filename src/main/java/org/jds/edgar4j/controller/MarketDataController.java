package org.jds.edgar4j.controller;

import java.time.LocalDate;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

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
}
