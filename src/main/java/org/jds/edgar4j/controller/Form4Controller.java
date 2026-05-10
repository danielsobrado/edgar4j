package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.Form4Service.InsiderStats;
import org.jds.edgar4j.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for Form 4 insider trading filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form4")
@RequiredArgsConstructor
public class Form4Controller {

    private static final long MAX_DATE_RANGE_DAYS = 366;

    private final Form4Service form4Service;

    /**
     * Get Form 4 by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Form4> getById(@PathVariable String id) {
        return form4Service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get Form 4 by accession number.
     */
    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form4> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form4Service.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get Form 4 filings by trading symbol.
     * Falls back to SEC API if local database is empty.
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form4>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        PageRequest pageRequest = PaginationUtils.pageRequest(safePage, safeSize, "transactionDate");
        Page<Form4> results = form4Service.findByTradingSymbol(symbol.toUpperCase(), pageRequest);
        
        // If no results in local DB, try SEC API as fallback
        if (results.isEmpty()) {
            log.info("No local Form 4 data for symbol {}, attempting SEC API fallback", symbol);
            try {
                LocalDate start = startDate != null ? startDate : LocalDate.now().minusYears(1);
                LocalDate end = endDate != null ? endDate : LocalDate.now();

                if (!isValidDateRange(start, end)) {
                    return ResponseEntity.badRequest().build();
                }

                List<Form4> secApiResults = form4Service.fetchFromSecApi(
                    symbol.toUpperCase(), start, end, safeSize);

                if (!secApiResults.isEmpty()) {
                    int startIdx = (int) Math.min(
                            (long) safePage * safeSize,
                            (long) Integer.MAX_VALUE);
                    int endIdx = Math.min(startIdx + safeSize, secApiResults.size());
                    List<Form4> pageContent = startIdx < secApiResults.size()
                        ? secApiResults.subList(startIdx, endIdx)
                        : List.of();

                    results = new org.springframework.data.domain.PageImpl<>(
                        pageContent, pageRequest, secApiResults.size());
                }
            } catch (Exception e) {
                log.error("SEC API fallback failed for symbol {}: {}", symbol, e.getMessage());
            }
        }
        
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 4 filings by CIK.
     */
    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form4>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        PageRequest pageRequest = PaginationUtils.pageRequest(safePage, safeSize, "transactionDate");
        Page<Form4> results = form4Service.findByCik(cik, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Search Form 4 filings by owner name.
     */
    @GetMapping("/owner")
    public ResponseEntity<List<Form4>> searchByOwner(@RequestParam String name) {
        List<Form4> results = form4Service.findByOwnerName(name);
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 4 filings within date range.
     */
    @GetMapping("/date-range")
    public ResponseEntity<Page<Form4>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!isValidDateRange(startDate, endDate)) {
            return ResponseEntity.badRequest().build();
        }
        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        PageRequest pageRequest = PaginationUtils.pageRequest(safePage, safeSize, "transactionDate");
        Page<Form4> results = form4Service.findByDateRange(startDate, endDate, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 4 filings by symbol and date range.
     */
    @GetMapping("/symbol/{symbol}/date-range")
    public ResponseEntity<Page<Form4>> getBySymbolAndDateRange(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!isValidDateRange(startDate, endDate)) {
            return ResponseEntity.badRequest().build();
        }
        int safePage = PaginationUtils.normalizePage(page);
        int safeSize = PaginationUtils.normalizeSize(size);
        PageRequest pageRequest = PaginationUtils.pageRequest(safePage, safeSize, "transactionDate");
        Page<Form4> results = form4Service.findBySymbolAndDateRange(symbol.toUpperCase(), startDate, endDate, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get recent Form 4 filings.
     * Falls back to SEC API if local database is empty.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Form4>> getRecentFilings(
            @RequestParam(defaultValue = "10") int limit) {
        List<Form4> results = form4Service.findRecentFilings(Math.min(limit, 100));
        
        // If no results in local DB, try SEC API as fallback
        if (results.isEmpty()) {
            log.info("No local recent Form 4 data, attempting SEC API fallback");
            try {
                results = form4Service.fetchRecentFromSecApi(Math.min(limit, 100));
            } catch (Exception e) {
                log.error("SEC API fallback failed for recent filings: {}", e.getMessage());
            }
        }
        
        return ResponseEntity.ok(results);
    }

    /**
     * Get insider statistics for a symbol.
     */
    @GetMapping("/symbol/{symbol}/stats")
    public ResponseEntity<InsiderStats> getInsiderStats(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (!isValidDateRange(startDate, endDate)) {
            return ResponseEntity.badRequest().build();
        }
        InsiderStats stats = form4Service.getInsiderStats(symbol.toUpperCase(), startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    /**
     * Download and parse a Form 4 filing.
     */
    @PostMapping("/download")
    public ResponseEntity<Form4> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument) {

        // Check if already exists
        if (form4Service.existsByAccessionNumber(accessionNumber)) {
            return form4Service.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form4 form4 = form4Service.downloadAndParseForm4(cik, accessionNumber, primaryDocument)
                    .join();

            if (form4 == null) {
                return ResponseEntity.badRequest().build();
            }

            Form4 saved = form4Service.save(form4);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to download/parse Form 4: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Save a Form 4 filing (manual entry or re-processing).
     */
    @PostMapping
    public ResponseEntity<Form4> save(@RequestBody Form4 form4) {
        Form4 saved = form4Service.save(form4);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a Form 4 filing.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form4Service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form4Service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isValidDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return false;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) <= MAX_DATE_RANGE_DAYS;
    }
}
