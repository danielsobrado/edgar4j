package org.jds.edgar4j.controller;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.FilingService;
import org.jds.edgar4j.util.PaginationUtils;
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
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Companies", description = "Company search and management endpoints")
public class CompanyController {

    private final CompanyService companyService;
    private final FilingService  filingService;

    // ─── Company list / search ────────────────────────────────────────────────

    @Operation(summary = "Search companies", description = "Search and list companies with pagination and sorting")
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CompanyListResponse>>> getCompanies(
            @Parameter(description = "Search term for company name or ticker") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {

        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        log.info("GET /api/companies?search={}&page={}&size={}", search, page, size);
        CompanySearchRequest request = CompanySearchRequest.builder()
                .searchTerm(search).page(safePage).size(safeSize).sortBy(sortBy).sortDir(sortDir)
                .build();
        return ResponseEntity.ok(ApiResponse.success(companyService.searchCompanies(request)));
    }

    // ─── Full company detail lookups ──────────────────────────────────────────

    @Operation(summary = "Get company by ID", description = "Retrieve a company by its internal MongoDB ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyById(
            @Parameter(description = "Company ID") @PathVariable String id) {
        log.info("GET /api/companies/{}", id);
        return companyService.getCompanyById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get company by CIK",
               description = "Retrieve company details by SEC CIK number. Uses company_tickers as primary source then enriches from submissions.")
    @GetMapping("/cik/{cik}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByCik(
            @Parameter(description = "SEC CIK number (padded or raw)", example = "0000320193") @PathVariable String cik) {
        log.info("GET /api/companies/cik/{}", cik);
        return companyService.getCompanyByCik(cik)
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get company by ticker",
               description = "Retrieve company details by stock ticker symbol. Uses company_tickers as primary source then enriches from submissions.")
    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByTicker(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String ticker) {
        log.info("GET /api/companies/ticker/{}", ticker);
        return companyService.getCompanyByTicker(ticker)
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Lightweight CIK ↔ Ticker cross-lookup ────────────────────────────────

    @Operation(summary = "Get CIK for a ticker symbol",
               description = "Fast lookup: returns only the zero-padded CIK string for the given ticker, using company_tickers collection.")
    @GetMapping("/ticker/{ticker}/cik")
    public ResponseEntity<ApiResponse<String>> getCikByTicker(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String ticker) {
        log.info("GET /api/companies/ticker/{}/cik", ticker);
        return companyService.getCikByTicker(ticker)
                .map(cik -> ResponseEntity.ok(ApiResponse.success(cik)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get ticker symbol for a CIK",
               description = "Fast lookup: returns only the ticker string for the given CIK, using company_tickers collection.")
    @GetMapping("/cik/{cik}/ticker")
    public ResponseEntity<ApiResponse<String>> getTickerByCik(
            @Parameter(description = "SEC CIK number (padded or raw)", example = "0000320193") @PathVariable String cik) {
        log.info("GET /api/companies/cik/{}/ticker", cik);
        return companyService.getTickerByCik(cik)
                .map(t -> ResponseEntity.ok(ApiResponse.success(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get raw CompanyTicker document by ticker",
               description = "Returns the raw company_tickers entry for a given ticker symbol.")
    @GetMapping("/ticker/{ticker}/info")
    public ResponseEntity<ApiResponse<CompanyTicker>> getCompanyTickerByTicker(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String ticker) {
        log.info("GET /api/companies/ticker/{}/info", ticker);
        return companyService.getCompanyTickerByTicker(ticker)
                .map(ct -> ResponseEntity.ok(ApiResponse.success(ct)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get raw CompanyTicker document by CIK",
               description = "Returns the raw company_tickers entry for a given CIK.")
    @GetMapping("/cik/{cik}/info")
    public ResponseEntity<ApiResponse<CompanyTicker>> getCompanyTickerByCik(
            @Parameter(description = "SEC CIK number (padded or raw)", example = "0000320193") @PathVariable String cik) {
        log.info("GET /api/companies/cik/{}/info", cik);
        return companyService.getCompanyTickerByCik(cik)
                .map(ct -> ResponseEntity.ok(ApiResponse.success(ct)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Filings ──────────────────────────────────────────────────────────────

    @Operation(summary = "Get company filings", description = "Retrieve SEC filings for a company by its internal ID")
    @GetMapping("/{id}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilings(
            @Parameter(description = "Company ID") @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        log.info("GET /api/companies/{}/filings?page={}&size={}", id, page, size);
        return ResponseEntity.ok(ApiResponse.success(filingService.getFilingsByCompany(id, safePage, safeSize)));
    }

    @Operation(summary = "Get company filings by CIK", description = "Retrieve SEC filings for a company by its CIK number")
    @GetMapping("/cik/{cik}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilingsByCik(
            @Parameter(description = "SEC CIK number", example = "0000320193") @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        log.info("GET /api/companies/cik/{}/filings?page={}&size={}", cik, page, size);
        return ResponseEntity.ok(ApiResponse.success(filingService.getFilingsByCik(cik, safePage, safeSize)));
    }
}
