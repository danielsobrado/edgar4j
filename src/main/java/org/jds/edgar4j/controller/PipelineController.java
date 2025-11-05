package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.DownloadHistory.ProcessingStatus;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import org.jds.edgar4j.service.BackfillService;
import org.jds.edgar4j.service.Form4DownloadService;
import org.jds.edgar4j.service.Form4DownloadService.DownloadStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for Form 4 download pipeline management
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    @Autowired
    private Form4DownloadService downloadService;

    @Autowired
    private BackfillService backfillService;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    /**
     * Get pipeline statistics
     * GET /api/pipeline/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<DownloadStatistics> getStatistics() {
        log.info("Getting pipeline statistics");
        DownloadStatistics stats = downloadService.getStatistics();
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
     * Health check endpoint
     * GET /api/pipeline/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        DownloadStatistics stats = downloadService.getStatistics();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("statistics", stats);
        health.put("timestamp", LocalDate.now());

        return ResponseEntity.ok(health);
    }
}
