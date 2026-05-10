package org.jds.edgar4j.controller;

import java.util.List;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DashboardStatsResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.RecentSearchResponse;
import org.jds.edgar4j.service.DashboardService;
import org.jds.edgar4j.util.PaginationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Validated
@Tag(name = "Dashboard", description = "Dashboard statistics and recent activity endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "Get dashboard statistics",
            description = "Returns aggregated statistics including total companies, filings, and form type counts"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        log.info("GET /api/dashboard/stats");
        DashboardStatsResponse stats = dashboardService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(
            summary = "Get recent searches",
            description = "Returns the most recent company/filing searches performed"
    )
    @GetMapping("/recent-searches")
    public ResponseEntity<ApiResponse<List<RecentSearchResponse>>> getRecentSearches(
            @Parameter(description = "Maximum number of searches to return", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        int safeLimit = PaginationUtils.normalizeSize(limit);
        log.info("GET /api/dashboard/recent-searches?limit={}", safeLimit);
        List<RecentSearchResponse> searches = dashboardService.getRecentSearches(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(searches));
    }

    @Operation(
            summary = "Get recent filings",
            description = "Returns the most recently added SEC filings"
    )
    @GetMapping("/recent-filings")
    public ResponseEntity<ApiResponse<List<FilingResponse>>> getRecentFilings(
            @Parameter(description = "Maximum number of filings to return", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        int safeLimit = PaginationUtils.normalizeSize(limit);
        log.info("GET /api/dashboard/recent-filings?limit={}", safeLimit);
        List<FilingResponse> filings = dashboardService.getRecentFilings(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }
}
