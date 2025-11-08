package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.repository.Form13FRepository;
import org.jds.edgar4j.service.Form13FDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for Form 13F institutional holdings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@RestController
@RequestMapping("/api/form13f")
public class Form13FController {

    @Autowired
    private Form13FRepository form13FRepository;

    @Autowired
    private Form13FDownloadService downloadService;

    /**
     * Get Form 13F by accession number
     * GET /api/form13f/filing/{accessionNumber}
     */
    @GetMapping("/filing/{accessionNumber}")
    public ResponseEntity<Form13F> getByAccessionNumber(@PathVariable String accessionNumber) {
        log.info("GET /api/form13f/filing/{}", accessionNumber);

        Form13F form = form13FRepository.findByAccessionNumber(accessionNumber);

        if (form == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(form);
    }

    /**
     * Get all filings for an institution
     * GET /api/form13f/institution/{cik}?page=0&size=20
     */
    @GetMapping("/institution/{cik}")
    public ResponseEntity<Page<Form13F>> getByInstitution(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/form13f/institution/{} - page={}, size={}", cik, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Form13F> filings = form13FRepository.findByFilerCikOrderByFilingDateDesc(cik, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Search institutions by name
     * GET /api/form13f/search?name=berkshire&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Form13F>> searchByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/form13f/search - name={}, page={}, size={}", name, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Form13F> filings = form13FRepository.findByFilerNameContaining(name, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get filings for a specific quarter
     * GET /api/form13f/quarter/2024-12-31?page=0&size=50
     */
    @GetMapping("/quarter/{quarterEnd}")
    public ResponseEntity<Page<Form13F>> getByQuarter(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quarterEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/form13f/quarter/{} - page={}, size={}", quarterEnd, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Form13F> filings = form13FRepository.findByPeriodOfReport(quarterEnd, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get latest filing for an institution
     * GET /api/form13f/institution/{cik}/latest
     */
    @GetMapping("/institution/{cik}/latest")
    public ResponseEntity<Form13F> getLatestForInstitution(@PathVariable String cik) {
        log.info("GET /api/form13f/institution/{}/latest", cik);

        Page<Form13F> filings = form13FRepository.findByFilerCikOrderByFilingDateDesc(
            cik, PageRequest.of(0, 1));

        if (filings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(filings.getContent().get(0));
    }

    /**
     * Get top holdings for an institution
     * GET /api/form13f/institution/{cik}/holdings?quarterEnd=2024-12-31&limit=10
     */
    @GetMapping("/institution/{cik}/holdings")
    public ResponseEntity<List<Form13F.Holding>> getTopHoldings(
            @PathVariable String cik,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quarterEnd,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/form13f/institution/{}/holdings - quarterEnd={}, limit={}", cik, quarterEnd, limit);

        Form13F form;
        if (quarterEnd != null) {
            form = form13FRepository.findByFilerCikAndPeriodOfReport(cik, quarterEnd);
        } else {
            // Get latest filing
            Page<Form13F> filings = form13FRepository.findByFilerCikOrderByFilingDateDesc(
                cik, PageRequest.of(0, 1));
            form = filings.isEmpty() ? null : filings.getContent().get(0);
        }

        if (form == null) {
            return ResponseEntity.notFound().build();
        }

        List<Form13F.Holding> topHoldings = form.getTopHoldings(limit);

        return ResponseEntity.ok(topHoldings);
    }

    /**
     * Download Form 13Fs for a specific quarter
     * POST /api/form13f/download/quarter?year=2024&quarter=4
     */
    @PostMapping("/download/quarter")
    public ResponseEntity<Map<String, Object>> downloadForQuarter(
            @RequestParam int year,
            @RequestParam int quarter) {

        log.info("POST /api/form13f/download/quarter - year={}, quarter={}", year, quarter);

        int downloaded = downloadService.downloadForQuarter(year, quarter);

        Map<String, Object> response = new HashMap<>();
        response.put("year", year);
        response.put("quarter", quarter);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download latest Form 13F filings
     * POST /api/form13f/download/latest?count=100
     */
    @PostMapping("/download/latest")
    public ResponseEntity<Map<String, Object>> downloadLatest(
            @RequestParam(defaultValue = "100") int count) {

        log.info("POST /api/form13f/download/latest - count={}", count);

        int downloaded = downloadService.downloadLatestFilings(count);

        Map<String, Object> response = new HashMap<>();
        response.put("requested", count);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download specific Form 13F by accession number
     * POST /api/form13f/download/accession/{accessionNumber}
     */
    @PostMapping("/download/accession/{accessionNumber}")
    public ResponseEntity<Map<String, Object>> downloadByAccessionNumber(
            @PathVariable String accessionNumber) {

        log.info("POST /api/form13f/download/accession/{}", accessionNumber);

        boolean success = downloadService.downloadByAccessionNumber(accessionNumber);

        Map<String, Object> response = new HashMap<>();
        response.put("accessionNumber", accessionNumber);
        response.put("success", success);
        response.put("status", success ? "completed" : "failed");

        return ResponseEntity.ok(response);
    }

    /**
     * Get Form 13F statistics
     * GET /api/form13f/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Form13FDownloadService.DownloadStatistics> getStatistics() {
        log.info("GET /api/form13f/statistics");

        Form13FDownloadService.DownloadStatistics stats = downloadService.getStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Get institution count
     * GET /api/form13f/count/institutions
     */
    @GetMapping("/count/institutions")
    public ResponseEntity<Map<String, Long>> getInstitutionCount() {
        log.info("GET /api/form13f/count/institutions");

        long count = form13FRepository.count();

        Map<String, Long> response = new HashMap<>();
        response.put("institutionCount", count);

        return ResponseEntity.ok(response);
    }

    /**
     * Check if institution has a holding for a security
     * GET /api/form13f/institution/{cik}/holds/{cusip}?quarterEnd=2024-12-31
     */
    @GetMapping("/institution/{cik}/holds/{cusip}")
    public ResponseEntity<Map<String, Object>> checkHolding(
            @PathVariable String cik,
            @PathVariable String cusip,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quarterEnd) {

        log.info("GET /api/form13f/institution/{}/holds/{} - quarterEnd={}", cik, cusip, quarterEnd);

        Form13F form;
        if (quarterEnd != null) {
            form = form13FRepository.findByFilerCikAndPeriodOfReport(cik, quarterEnd);
        } else {
            Page<Form13F> filings = form13FRepository.findByFilerCikOrderByFilingDateDesc(
                cik, PageRequest.of(0, 1));
            form = filings.isEmpty() ? null : filings.getContent().get(0);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("filerCik", cik);
        response.put("cusip", cusip);

        if (form == null) {
            response.put("holds", false);
            return ResponseEntity.ok(response);
        }

        Form13F.Holding holding = form.getHoldingByCusip(cusip);
        response.put("holds", holding != null);
        response.put("quarterEnd", form.getPeriodOfReport());

        if (holding != null) {
            response.put("holding", holding);
        }

        return ResponseEntity.ok(response);
    }
}
