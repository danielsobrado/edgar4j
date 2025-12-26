package org.jds.edgar4j.controller;

import java.util.List;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DashboardStatsResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.RecentSearchResponse;
import org.jds.edgar4j.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        log.info("GET /api/dashboard/stats");
        DashboardStatsResponse stats = dashboardService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/recent-searches")
    public ResponseEntity<ApiResponse<List<RecentSearchResponse>>> getRecentSearches(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/dashboard/recent-searches?limit={}", limit);
        List<RecentSearchResponse> searches = dashboardService.getRecentSearches(limit);
        return ResponseEntity.ok(ApiResponse.success(searches));
    }

    @GetMapping("/recent-filings")
    public ResponseEntity<ApiResponse<List<FilingResponse>>> getRecentFilings(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/dashboard/recent-filings?limit={}", limit);
        List<FilingResponse> filings = dashboardService.getRecentFilings(limit);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }
}
