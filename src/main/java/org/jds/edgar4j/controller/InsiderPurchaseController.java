package org.jds.edgar4j.controller;

import java.util.List;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseSummary;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.service.InsiderPurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/insider-purchases")
@RequiredArgsConstructor
@Tag(name = "Insider Purchases", description = "Recent insider purchase activity with price change tracking")
public class InsiderPurchaseController {

    private final InsiderPurchaseService insiderPurchaseService;

    @Operation(
            summary = "Get recent insider purchases",
            description = "Paginated list of recent open-market insider purchases enriched with current price, percent change, and market cap.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Insider purchases retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<InsiderPurchaseResponse>>> getInsiderPurchases(
            @Parameter(description = "Days to look back")
            @RequestParam(defaultValue = "30") int lookbackDays,
            @Parameter(description = "Minimum market cap in USD")
            @RequestParam(required = false) Double minMarketCap,
            @Parameter(description = "Restrict results to S&P 500 constituents")
            @RequestParam(defaultValue = "false") boolean sp500Only,
            @Parameter(description = "Minimum transaction value in USD")
            @RequestParam(required = false) Double minTransactionValue,
            @Parameter(description = "Sort field: percentChange|ticker|transactionDate|transactionValue|marketCap")
            @RequestParam(defaultValue = "percentChange") String sortBy,
            @Parameter(description = "Sort direction: asc|desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/insider-purchases?lookbackDays={}&sp500Only={}&page={}&size={}",
                lookbackDays, sp500Only, page, size);

        return ResponseEntity.ok(ApiResponse.success(
                insiderPurchaseService.getRecentInsiderPurchases(
                        lookbackDays,
                        minMarketCap,
                        sp500Only,
                        minTransactionValue,
                        sortBy,
                        sortDir,
                        page,
                        size)));
    }

    @Operation(
            summary = "Get top insider purchases",
            description = "Returns the top insider purchases sorted by best price change since purchase date.")
    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<InsiderPurchaseResponse>>> getTopInsiderPurchases(
            @Parameter(description = "Number of results to return")
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/insider-purchases/top?limit={}", limit);
        return ResponseEntity.ok(ApiResponse.success(insiderPurchaseService.getTopInsiderPurchases(limit)));
    }

    @Operation(
            summary = "Get insider purchase summary",
            description = "Returns aggregate summary statistics for recent insider purchases.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<InsiderPurchaseSummary>> getSummary(
            @Parameter(description = "Days to look back")
            @RequestParam(defaultValue = "30") int lookbackDays) {

        log.info("GET /api/insider-purchases/summary?lookbackDays={}", lookbackDays);
        return ResponseEntity.ok(ApiResponse.success(insiderPurchaseService.getSummary(lookbackDays)));
    }
}
