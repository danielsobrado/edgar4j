package org.jds.edgar4j.controller;

import java.util.Date;
import java.util.List;

import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.FilingDetailResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.service.DashboardService;
import org.jds.edgar4j.service.FilingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/filings")
@RequiredArgsConstructor
public class FilingController {

    private final FilingService filingService;
    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getFilings(
            @RequestParam(required = false) String cik,
            @RequestParam(required = false) String formType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "fillingDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        log.info("GET /api/filings?cik={}&formType={}&page={}&size={}", cik, formType, page, size);

        FilingSearchRequest request = FilingSearchRequest.builder()
                .cik(cik)
                .formTypes(formType != null ? List.of(formType) : null)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        PaginatedResponse<FilingResponse> filings = filingService.searchFilings(request);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> searchFilings(
            @RequestBody FilingSearchRequest request) {
        log.info("POST /api/filings/search: {}", request);

        PaginatedResponse<FilingResponse> filings = filingService.searchFilings(request);

        String searchQuery = buildSearchQuery(request);
        dashboardService.recordSearch(searchQuery, "filing", (int) filings.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(filings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FilingDetailResponse>> getFilingById(@PathVariable String id) {
        log.info("GET /api/filings/{}", id);
        return filingService.getFilingById(id)
                .map(filing -> ResponseEntity.ok(ApiResponse.success(filing)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<ApiResponse<FilingDetailResponse>> getFilingByAccessionNumber(
            @PathVariable String accessionNumber) {
        log.info("GET /api/filings/accession/{}", accessionNumber);
        return filingService.getFilingByAccessionNumber(accessionNumber)
                .map(filing -> ResponseEntity.ok(ApiResponse.success(filing)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<FilingResponse>>> getRecentFilings(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/filings/recent?limit={}", limit);
        List<FilingResponse> filings = filingService.getRecentFilings(limit);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }

    private String buildSearchQuery(FilingSearchRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getCompanyName() != null) {
            sb.append("company:").append(request.getCompanyName()).append(" ");
        }
        if (request.getCik() != null) {
            sb.append("cik:").append(request.getCik()).append(" ");
        }
        if (request.getFormTypes() != null && !request.getFormTypes().isEmpty()) {
            sb.append("forms:").append(String.join(",", request.getFormTypes()));
        }
        return sb.toString().trim();
    }
}
