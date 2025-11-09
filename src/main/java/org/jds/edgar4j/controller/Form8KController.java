package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.repository.Form8KRepository;
import org.jds.edgar4j.service.Form8KDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for Form 8-K current event reports
 * Provides endpoints for querying material corporate events
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-09
 */
@Slf4j
@RestController
@RequestMapping("/api/form8k")
public class Form8KController {

    @Autowired
    private Form8KRepository form8KRepository;

    @Autowired
    private Form8KDownloadService downloadService;

    /**
     * Get Form 8-K by accession number
     *
     * @param accessionNumber SEC accession number
     * @return Form 8-K or 404
     */
    @GetMapping("/{accessionNumber}")
    public ResponseEntity<Form8K> getByAccessionNumber(@PathVariable String accessionNumber) {
        log.info("GET /api/form8k/{}", accessionNumber);

        Optional<Form8K> form8K = form8KRepository.findById(accessionNumber);
        return form8K.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get Form 8-Ks for a specific company
     *
     * @param cik company CIK
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of Form 8-Ks
     */
    @GetMapping("/company/{cik}")
    public ResponseEntity<Page<Form8K>> getByCompany(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/company/{} (page={}, size={})", cik, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByCompanyCik(cik, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get Form 8-Ks by date range
     *
     * @param startDate start date
     * @param endDate end date
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of Form 8-Ks
     */
    @GetMapping("/date-range")
    public ResponseEntity<Page<Form8K>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/date-range?startDate={}&endDate={}", startDate, endDate);

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByFilingDateBetween(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get Form 8-Ks by item number (e.g., "1.01", "2.02")
     *
     * @param itemNumber item number
     * @param startDate optional start date
     * @param endDate optional end date
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of Form 8-Ks
     */
    @GetMapping("/item/{itemNumber}")
    public ResponseEntity<Page<Form8K>> getByItem(
            @PathVariable String itemNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/item/{}?startDate={}&endDate={}", itemNumber, startDate, endDate);

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings;

        if (startDate != null && endDate != null) {
            filings = form8KRepository.findByItemsContainingAndEventDateBetween(
                    itemNumber, startDate, endDate, pageable);
        } else {
            filings = form8KRepository.findByItemsContaining(itemNumber, pageable);
        }

        return ResponseEntity.ok(filings);
    }

    /**
     * Get earnings reports (Item 2.02)
     *
     * @param startDate optional start date
     * @param endDate optional end date
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of earnings reports
     */
    @GetMapping("/earnings")
    public ResponseEntity<Page<Form8K>> getEarningsReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/earnings");

        return getByItem("2.02", startDate, endDate, page, size);
    }

    /**
     * Get management changes (Item 5.02)
     *
     * @param startDate optional start date
     * @param endDate optional end date
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of management change reports
     */
    @GetMapping("/management-changes")
    public ResponseEntity<Page<Form8K>> getManagementChanges(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/management-changes");

        return getByItem("5.02", startDate, endDate, page, size);
    }

    /**
     * Get material agreements (Item 1.01)
     *
     * @param startDate optional start date
     * @param endDate optional end date
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of material agreement reports
     */
    @GetMapping("/material-agreements")
    public ResponseEntity<Page<Form8K>> getMaterialAgreements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/material-agreements");

        return getByItem("1.01", startDate, endDate, page, size);
    }

    /**
     * Get Form 8-Ks by industry
     *
     * @param industry industry name
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of Form 8-Ks
     */
    @GetMapping("/industry/{industry}")
    public ResponseEntity<Page<Form8K>> getByIndustry(
            @PathVariable String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/industry/{}", industry);

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByIndustry(industry, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Search Form 8-Ks by text content
     *
     * @param query search query
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of Form 8-Ks
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Form8K>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/search?query={}", query);

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByTextContentContaining(query, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get recent amendments
     *
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of amended Form 8-Ks
     */
    @GetMapping("/amendments")
    public ResponseEntity<Page<Form8K>> getAmendments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/form8k/amendments");

        Pageable pageable = PageRequest.of(page, size, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByIsAmendment(true, pageable);

        return ResponseEntity.ok(filings);
    }

    /**
     * Get event items for a specific company
     *
     * @param cik company CIK
     * @param limit maximum number of recent events (default 50)
     * @return list of event items
     */
    @GetMapping("/company/{cik}/events")
    public ResponseEntity<List<Form8K.EventItem>> getCompanyEvents(
            @PathVariable String cik,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("GET /api/form8k/company/{}/events", cik);

        Pageable pageable = PageRequest.of(0, limit, Sort.by("filingDate").descending());
        Page<Form8K> filings = form8KRepository.findByCompanyCik(cik, pageable);

        List<Form8K.EventItem> events = filings.stream()
                .flatMap(form -> form.getEventItems() != null ? form.getEventItems().stream() : null)
                .filter(event -> event != null)
                .limit(limit)
                .toList();

        return ResponseEntity.ok(events);
    }

    /**
     * Check if a company filed a specific item type
     *
     * @param cik company CIK
     * @param itemNumber item number (e.g., "2.02")
     * @param days days to look back (default 30)
     * @return map with result
     */
    @GetMapping("/company/{cik}/has-item/{itemNumber}")
    public ResponseEntity<Map<String, Object>> checkCompanyItem(
            @PathVariable String cik,
            @PathVariable String itemNumber,
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/form8k/company/{}/has-item/{}", cik, itemNumber);

        LocalDate startDate = LocalDate.now().minusDays(days);
        Pageable pageable = PageRequest.of(0, 1);

        Page<Form8K> filings = form8KRepository.findByCompanyCikAndItemsContainingAndFilingDateAfter(
                cik, itemNumber, startDate.atStartOfDay(), pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("companyCik", cik);
        result.put("itemNumber", itemNumber);
        result.put("hasFiled", !filings.isEmpty());
        result.put("lookbackDays", days);

        if (!filings.isEmpty()) {
            Form8K latestFiling = filings.getContent().get(0);
            result.put("latestFilingDate", latestFiling.getFilingDate());
            result.put("accessionNumber", latestFiling.getAccessionNumber());
        }

        return ResponseEntity.ok(result);
    }

    // ===== Download Endpoints =====

    /**
     * Download Form 8-Ks for a specific date
     *
     * @param date filing date
     * @return download result
     */
    @PostMapping("/download/date")
    public ResponseEntity<Map<String, Object>> downloadForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("POST /api/form8k/download/date?date={}", date);

        try {
            int downloaded = downloadService.downloadForDate(date);

            Map<String, Object> result = new HashMap<>();
            result.put("date", date);
            result.put("downloaded", downloaded);
            result.put("status", "completed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error downloading Form 8-Ks for date {}", date, e);

            Map<String, Object> result = new HashMap<>();
            result.put("date", date);
            result.put("status", "error");
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Download Form 8-Ks for a date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return download result
     */
    @PostMapping("/download/date-range")
    public ResponseEntity<Map<String, Object>> downloadForDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("POST /api/form8k/download/date-range?startDate={}&endDate={}", startDate, endDate);

        try {
            int downloaded = downloadService.downloadForDateRange(startDate, endDate);

            Map<String, Object> result = new HashMap<>();
            result.put("startDate", startDate);
            result.put("endDate", endDate);
            result.put("downloaded", downloaded);
            result.put("status", "completed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error downloading Form 8-Ks for date range {} to {}", startDate, endDate, e);

            Map<String, Object> result = new HashMap<>();
            result.put("startDate", startDate);
            result.put("endDate", endDate);
            result.put("status", "error");
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Download latest Form 8-K filings
     *
     * @param count number of filings to download (default 100)
     * @return download result
     */
    @PostMapping("/download/latest")
    public ResponseEntity<Map<String, Object>> downloadLatest(
            @RequestParam(defaultValue = "100") int count) {
        log.info("POST /api/form8k/download/latest?count={}", count);

        try {
            int downloaded = downloadService.downloadLatestFilings(count);

            Map<String, Object> result = new HashMap<>();
            result.put("requested", count);
            result.put("downloaded", downloaded);
            result.put("status", "completed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error downloading latest Form 8-Ks", e);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Download Form 8-K by accession number
     *
     * @param accessionNumber SEC accession number
     * @return download result
     */
    @PostMapping("/download/accession/{accessionNumber}")
    public ResponseEntity<Map<String, Object>> downloadByAccession(
            @PathVariable String accessionNumber) {
        log.info("POST /api/form8k/download/accession/{}", accessionNumber);

        try {
            boolean success = downloadService.downloadByAccessionNumber(accessionNumber);

            Map<String, Object> result = new HashMap<>();
            result.put("accessionNumber", accessionNumber);
            result.put("success", success);
            result.put("status", success ? "completed" : "failed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error downloading Form 8-K {}", accessionNumber, e);

            Map<String, Object> result = new HashMap<>();
            result.put("accessionNumber", accessionNumber);
            result.put("status", "error");
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * Get download statistics
     *
     * @return statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Form8KDownloadService.DownloadStatistics> getStatistics() {
        log.info("GET /api/form8k/statistics");

        Form8KDownloadService.DownloadStatistics stats = downloadService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Retry failed downloads
     *
     * @param maxRetries maximum retry attempts (default 3)
     * @return retry result
     */
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailed(
            @RequestParam(defaultValue = "3") int maxRetries) {
        log.info("POST /api/form8k/retry-failed?maxRetries={}", maxRetries);

        try {
            int retried = downloadService.retryFailedDownloads(maxRetries);

            Map<String, Object> result = new HashMap<>();
            result.put("maxRetries", maxRetries);
            result.put("successfulRetries", retried);
            result.put("status", "completed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error retrying failed Form 8-K downloads", e);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
