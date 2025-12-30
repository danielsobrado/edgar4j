package org.jds.edgar4j.controller;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.FilingService;
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
    private final FilingService filingService;

    @Operation(summary = "Search companies", description = "Search and list companies with pagination and sorting")
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CompanyListResponse>>> getCompanies(
            @Parameter(description = "Search term for company name or ticker") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {
        log.info("GET /api/companies?search={}&page={}&size={}", search, page, size);

        CompanySearchRequest request = CompanySearchRequest.builder()
                .searchTerm(search)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        PaginatedResponse<CompanyListResponse> companies = companyService.searchCompanies(request);
        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @Operation(summary = "Get company by ID", description = "Retrieve a company by its internal ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyById(
            @Parameter(description = "Company ID") @PathVariable String id) {
        log.info("GET /api/companies/{}", id);
        return companyService.getCompanyById(id)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get company by CIK", description = "Retrieve a company by its SEC CIK number")
    @GetMapping("/cik/{cik}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByCik(
            @Parameter(description = "SEC CIK number", example = "0000320193") @PathVariable String cik) {
        log.info("GET /api/companies/cik/{}", cik);
        return companyService.getCompanyByCik(cik)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get company by ticker", description = "Retrieve a company by its stock ticker symbol")
    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByTicker(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String ticker) {
        log.info("GET /api/companies/ticker/{}", ticker);
        return companyService.getCompanyByTicker(ticker)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get company filings", description = "Retrieve SEC filings for a company by its internal ID")
    @GetMapping("/{id}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilings(
            @Parameter(description = "Company ID") @PathVariable String id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/companies/{}/filings?page={}&size={}", id, page, size);
        PaginatedResponse<FilingResponse> filings = filingService.getFilingsByCompany(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }

    @Operation(summary = "Get company filings by CIK", description = "Retrieve SEC filings for a company by its CIK number")
    @GetMapping("/cik/{cik}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilingsByCik(
            @Parameter(description = "SEC CIK number", example = "0000320193") @PathVariable String cik,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/companies/cik/{}/filings?page={}&size={}", cik, page, size);
        PaginatedResponse<FilingResponse> filings = filingService.getFilingsByCik(cik, page, size);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }
}
