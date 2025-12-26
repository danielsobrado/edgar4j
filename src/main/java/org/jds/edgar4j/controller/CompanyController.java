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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final FilingService filingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CompanyListResponse>>> getCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyById(@PathVariable String id) {
        log.info("GET /api/companies/{}", id);
        return companyService.getCompanyById(id)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByCik(@PathVariable String cik) {
        log.info("GET /api/companies/cik/{}", cik);
        return companyService.getCompanyByCik(cik)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyByTicker(@PathVariable String ticker) {
        log.info("GET /api/companies/ticker/{}", ticker);
        return companyService.getCompanyByTicker(ticker)
                .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilings(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/companies/{}/filings?page={}&size={}", id, page, size);
        PaginatedResponse<FilingResponse> filings = filingService.getFilingsByCompany(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }

    @GetMapping("/cik/{cik}/filings")
    public ResponseEntity<ApiResponse<PaginatedResponse<FilingResponse>>> getCompanyFilingsByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/companies/cik/{}/filings?page={}&size={}", cik, page, size);
        PaginatedResponse<FilingResponse> filings = filingService.getFilingsByCik(cik, page, size);
        return ResponseEntity.ok(ApiResponse.success(filings));
    }
}
