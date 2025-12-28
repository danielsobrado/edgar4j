package org.jds.edgar4j.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.Form4Service.InsiderStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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

    private final Form4Service form4Service;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

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
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form4>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<Form4> results = form4Service.findByTradingSymbol(symbol.toUpperCase(), pageRequest);
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

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
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
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Date start = parseDate(startDate);
            Date end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
            Page<Form4> results = form4Service.findByDateRange(start, end, pageRequest);
            return ResponseEntity.ok(results);
        } catch (ParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get Form 4 filings by symbol and date range.
     */
    @GetMapping("/symbol/{symbol}/date-range")
    public ResponseEntity<Page<Form4>> getBySymbolAndDateRange(
            @PathVariable String symbol,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Date start = parseDate(startDate);
            Date end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
            Page<Form4> results = form4Service.findBySymbolAndDateRange(symbol.toUpperCase(), start, end, pageRequest);
            return ResponseEntity.ok(results);
        } catch (ParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent Form 4 filings.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Form4>> getRecentFilings(
            @RequestParam(defaultValue = "10") int limit) {
        List<Form4> results = form4Service.findRecentFilings(Math.min(limit, 100));
        return ResponseEntity.ok(results);
    }

    /**
     * Get insider statistics for a symbol.
     */
    @GetMapping("/symbol/{symbol}/stats")
    public ResponseEntity<InsiderStats> getInsiderStats(
            @PathVariable String symbol,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        try {
            Date start = parseDate(startDate);
            Date end = parseDate(endDate);
            InsiderStats stats = form4Service.getInsiderStats(symbol.toUpperCase(), start, end);
            return ResponseEntity.ok(stats);
        } catch (ParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
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

    private Date parseDate(String dateStr) throws ParseException {
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.parse(dateStr);
        }
    }
}
