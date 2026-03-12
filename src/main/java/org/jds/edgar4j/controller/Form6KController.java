package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.service.Form6KService;
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
 * REST API for Form 6-K foreign private issuer filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form6k")
@RequiredArgsConstructor
public class Form6KController {

    private final Form6KService form6KService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/{id}")
    public ResponseEntity<Form6K> getById(@PathVariable String id) {
        return form6KService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form6K> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form6KService.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form6K>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form6KService.findByCik(cik, pageRequest));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form6K>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form6KService.findByTradingSymbol(symbol.toUpperCase(), pageRequest));
    }

    @GetMapping("/date-range")
    public ResponseEntity<Page<Form6K>> getByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
            return ResponseEntity.ok(form6KService.findByFiledDateRange(start, end, pageRequest));
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Form6K>> getRecentFilings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(form6KService.findRecentFilings(Math.min(limit, 100)));
    }

    @PostMapping("/download")
    public ResponseEntity<Form6K> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String filedDate,
            @RequestParam(required = false) String reportDate) {

        if (form6KService.existsByAccessionNumber(accessionNumber)) {
            return form6KService.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form6K form6K = form6KService.downloadAndParse(cik, accessionNumber, primaryDocument).join();
            if (form6K == null) {
                return ResponseEntity.badRequest().build();
            }

            if (companyName != null && !companyName.isBlank()) {
                form6K.setCompanyName(companyName);
            }
            if (filedDate != null && !filedDate.isBlank()) {
                form6K.setFiledDate(parseDate(filedDate));
            }
            if (reportDate != null && !reportDate.isBlank()) {
                form6K.setReportDate(parseDate(reportDate));
            }

            Form6K saved = form6KService.save(form6K);
            return ResponseEntity.ok(saved);

        } catch (DateTimeParseException e) {
            log.warn("Invalid date format in request: filedDate={}, reportDate={}", filedDate, reportDate);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to download/parse Form 6-K: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Form6K> save(@RequestBody Form6K form6K) {
        Form6K saved = form6KService.save(form6K);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form6KService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form6KService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }
}

