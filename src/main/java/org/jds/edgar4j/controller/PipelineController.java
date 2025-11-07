package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.DownloadHistory.ProcessingStatus;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import java.util.HashSet;
import java.util.Set;

import org.jds.edgar4j.service.BackfillService;
import org.jds.edgar4j.service.Form4DownloadService;
import org.jds.edgar4j.service.InsiderFormDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for insider form download pipeline management
 * Supports Forms 3, 4, and 5
 *
 * @author J. Daniel Sobrado
 * @version 2.0
 * @since 2025-11-05
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    @Autowired
    private Form4DownloadService downloadService;  // Backwards compatibility

    @Autowired
    private InsiderFormDownloadService insiderFormDownloadService;

    @Autowired
    private BackfillService backfillService;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    /**
     * Get pipeline statistics (includes breakdown by form type)
     * GET /api/pipeline/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<InsiderFormDownloadService.DownloadStatistics> getStatistics() {
        log.info("Getting pipeline statistics");
        InsiderFormDownloadService.DownloadStatistics stats = insiderFormDownloadService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Download Form 4s for a specific date
     * POST /api/pipeline/download/date/{date}
     */
    @PostMapping("/download/date/{date}")
    public ResponseEntity<Map<String, Object>> downloadForDate(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("Manual download requested for date: {}", date);

        int downloaded = downloadService.downloadForDate(date);

        Map<String, Object> response = new HashMap<>();
        response.put("date", date);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download Form 4s for a date range
     * POST /api/pipeline/download/range?startDate={start}&endDate={end}
     */
    @PostMapping("/download/range")
    public ResponseEntity<Map<String, Object>> downloadForDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Manual download requested for range: {} to {}", startDate, endDate);

        int downloaded = downloadService.downloadForDateRange(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download latest Form 4s
     * POST /api/pipeline/download/latest?count={count}
     */
    @PostMapping("/download/latest")
    public ResponseEntity<Map<String, Object>> downloadLatest(
        @RequestParam(defaultValue = "100") int count) {

        log.info("Manual download requested for latest {} filings", count);

        int downloaded = downloadService.downloadLatestFilings(count);

        Map<String, Object> response = new HashMap<>();
        response.put("requested", count);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download specific Form 4 by accession number
     * POST /api/pipeline/download/accession/{accessionNumber}
     */
    @PostMapping("/download/accession/{accessionNumber}")
    public ResponseEntity<Map<String, Object>> downloadByAccessionNumber(
        @PathVariable String accessionNumber) {

        log.info("Manual download requested for accession: {}", accessionNumber);

        boolean success = downloadService.downloadByAccessionNumber(accessionNumber);

        Map<String, Object> response = new HashMap<>();
        response.put("accessionNumber", accessionNumber);
        response.put("success", success);
        response.put("status", success ? "completed" : "failed");

        return ResponseEntity.ok(response);
    }

    /**
     * Retry failed downloads
     * POST /api/pipeline/retry?maxRetries={retries}
     */
    @PostMapping("/retry")
    public ResponseEntity<Map<String, Object>> retryFailedDownloads(
        @RequestParam(defaultValue = "3") int maxRetries) {

        log.info("Retry failed downloads requested (maxRetries: {})", maxRetries);

        int retried = downloadService.retryFailedDownloads(maxRetries);

        Map<String, Object> response = new HashMap<>();
        response.put("maxRetries", maxRetries);
        response.put("retried", retried);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Backfill date range
     * POST /api/pipeline/backfill/range?startDate={start}&endDate={end}
     */
    @PostMapping("/backfill/range")
    public ResponseEntity<Map<String, Object>> backfillDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Backfill requested for range: {} to {}", startDate, endDate);

        int downloaded = backfillService.backfillDateRange(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Backfill recent days
     * POST /api/pipeline/backfill/recent?days={days}
     */
    @PostMapping("/backfill/recent")
    public ResponseEntity<Map<String, Object>> backfillRecentDays(
        @RequestParam(defaultValue = "30") int days) {

        log.info("Backfill requested for recent {} days", days);

        int downloaded = backfillService.backfillRecentDays(days);

        Map<String, Object> response = new HashMap<>();
        response.put("days", days);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Auto-backfill missing dates
     * POST /api/pipeline/backfill/auto
     */
    @PostMapping("/backfill/auto")
    public ResponseEntity<Map<String, Object>> autoBackfill() {
        log.info("Auto-backfill requested");

        int downloaded = backfillService.autoBackfill();

        Map<String, Object> response = new HashMap<>();
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Find missing dates
     * GET /api/pipeline/missing?startDate={start}&endDate={end}
     */
    @GetMapping("/missing")
    public ResponseEntity<List<LocalDate>> findMissingDates(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Finding missing dates from {} to {}", startDate, endDate);

        List<LocalDate> missingDates = backfillService.findMissingDates(startDate, endDate);

        return ResponseEntity.ok(missingDates);
    }

    /**
     * Get download history by status
     * GET /api/pipeline/history?status={status}&page={page}&size={size}
     */
    @GetMapping("/history")
    public ResponseEntity<Page<DownloadHistory>> getDownloadHistory(
        @RequestParam(required = false) ProcessingStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {

        log.info("Getting download history: status={}, page={}, size={}", status, page, size);

        Page<DownloadHistory> history;
        if (status != null) {
            history = downloadHistoryRepository.findByStatus(status, PageRequest.of(page, size));
        } else {
            history = downloadHistoryRepository.findAll(PageRequest.of(page, size));
        }

        return ResponseEntity.ok(history);
    }

    /**
     * Get download history for a specific date
     * GET /api/pipeline/history/date/{date}?page={page}&size={size}
     */
    @GetMapping("/history/date/{date}")
    public ResponseEntity<Page<DownloadHistory>> getDownloadHistoryByDate(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {

        log.info("Getting download history for date: {}", date);

        Page<DownloadHistory> history = downloadHistoryRepository.findByFilingDateBetween(
            date, date, PageRequest.of(page, size));

        return ResponseEntity.ok(history);
    }

    /**
     * Download insider forms for a specific date (Forms 3, 4, 5)
     * POST /api/pipeline/insider-forms/download/date/{date}?formTypes=3,4,5
     */
    @PostMapping("/insider-forms/download/date/{date}")
    public ResponseEntity<Map<String, Object>> downloadInsiderFormsForDate(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "3,4,5") String formTypes) {

        log.info("Manual insider form download requested for date: {} (types: {})", date, formTypes);

        Set<String> formTypeSet = parseFormTypes(formTypes);
        int downloaded = insiderFormDownloadService.downloadForDate(date, formTypeSet);

        Map<String, Object> response = new HashMap<>();
        response.put("date", date);
        response.put("formTypes", formTypeSet);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download insider forms for a date range (Forms 3, 4, 5)
     * POST /api/pipeline/insider-forms/download/range?startDate={start}&endDate={end}&formTypes=3,4,5
     */
    @PostMapping("/insider-forms/download/range")
    public ResponseEntity<Map<String, Object>> downloadInsiderFormsForDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(defaultValue = "3,4,5") String formTypes) {

        log.info("Manual insider form download requested for range: {} to {} (types: {})",
            startDate, endDate, formTypes);

        Set<String> formTypeSet = parseFormTypes(formTypes);
        int downloaded = insiderFormDownloadService.downloadForDateRange(startDate, endDate, formTypeSet);

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("formTypes", formTypeSet);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download latest insider forms (Forms 3, 4, 5)
     * POST /api/pipeline/insider-forms/download/latest?count={count}&formTypes=3,4,5
     */
    @PostMapping("/insider-forms/download/latest")
    public ResponseEntity<Map<String, Object>> downloadLatestInsiderForms(
        @RequestParam(defaultValue = "100") int count,
        @RequestParam(defaultValue = "3,4,5") String formTypes) {

        log.info("Manual insider form download requested for latest {} filings (types: {})",
            count, formTypes);

        Set<String> formTypeSet = parseFormTypes(formTypes);
        int downloaded = insiderFormDownloadService.downloadLatestFilings(formTypeSet, count);

        Map<String, Object> response = new HashMap<>();
        response.put("requested", count);
        response.put("formTypes", formTypeSet);
        response.put("downloaded", downloaded);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Download specific insider form by accession number and form type
     * POST /api/pipeline/insider-forms/download/accession/{accessionNumber}?formType=4
     */
    @PostMapping("/insider-forms/download/accession/{accessionNumber}")
    public ResponseEntity<Map<String, Object>> downloadInsiderFormByAccessionNumber(
        @PathVariable String accessionNumber,
        @RequestParam(defaultValue = "4") String formType) {

        log.info("Manual insider form download requested for accession: {} (type: {})",
            accessionNumber, formType);

        boolean success = insiderFormDownloadService.downloadByAccessionNumber(accessionNumber, formType);

        Map<String, Object> response = new HashMap<>();
        response.put("accessionNumber", accessionNumber);
        response.put("formType", formType);
        response.put("success", success);
        response.put("status", success ? "completed" : "failed");

        return ResponseEntity.ok(response);
    }

    /**
     * Parse comma-separated form types into a Set
     */
    private Set<String> parseFormTypes(String formTypes) {
        Set<String> formTypeSet = new HashSet<>();
        if (formTypes != null && !formTypes.isEmpty()) {
            String[] types = formTypes.split(",");
            for (String type : types) {
                formTypeSet.add(type.trim());
            }
        }
        return formTypeSet;
    }

    /**
     * Health check endpoint
     * GET /api/pipeline/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        InsiderFormDownloadService.DownloadStatistics stats = insiderFormDownloadService.getStatistics();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("statistics", stats);
        health.put("timestamp", LocalDate.now());

        return ResponseEntity.ok(health);
    }
}
