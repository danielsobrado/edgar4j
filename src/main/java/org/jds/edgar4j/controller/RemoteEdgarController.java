package org.jds.edgar4j.controller;

import java.util.List;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.RemoteSubmissionResponse;
import org.jds.edgar4j.dto.response.RemoteTickerResponse;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/remote-edgar")
@RequiredArgsConstructor
@Tag(name = "Remote EDGAR", description = "Live SEC EDGAR exploration endpoints")
public class RemoteEdgarController {

    private final RemoteEdgarService remoteEdgarService;

    @Operation(summary = "Search live SEC tickers", description = "Fetches ticker data from SEC endpoints without using local database")
    @GetMapping("/tickers")
    public ResponseEntity<ApiResponse<List<RemoteTickerResponse>>> getRemoteTickers(
            @Parameter(description = "Source: all, exchanges, mf", example = "all") @RequestParam(defaultValue = "all") String source,
            @Parameter(description = "Free-text search across ticker, name, and CIK") @RequestParam(required = false) String search,
            @Parameter(description = "Max rows returned (1-500)") @RequestParam(defaultValue = "100") int limit) {
        log.info("GET /api/remote-edgar/tickers?source={}&search={}&limit={}", source, search, limit);
        List<RemoteTickerResponse> tickers = remoteEdgarService.getRemoteTickers(source, search, limit);
        return ResponseEntity.ok(ApiResponse.success(tickers));
    }

    @Operation(summary = "Get live SEC submissions by CIK", description = "Fetches company submission feed from SEC for a CIK")
    @GetMapping("/submissions/{cik}")
    public ResponseEntity<ApiResponse<RemoteSubmissionResponse>> getRemoteSubmission(
            @Parameter(description = "SEC CIK number", example = "0000789019") @PathVariable String cik,
            @Parameter(description = "Max recent filings returned (1-200)") @RequestParam(defaultValue = "50") int filingsLimit) {
        log.info("GET /api/remote-edgar/submissions/{}?filingsLimit={}", cik, filingsLimit);
        RemoteSubmissionResponse submission = remoteEdgarService.getRemoteSubmission(cik, filingsLimit);
        return ResponseEntity.ok(ApiResponse.success(submission));
    }
}

