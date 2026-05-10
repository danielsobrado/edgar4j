package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.service.Form8KService;
import org.jds.edgar4j.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST API for Form 8-K current report filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form8k")
@RequiredArgsConstructor
public class Form8KController {

    private final Form8KService form8KService;

    @GetMapping("/{id}")
    public ResponseEntity<Form8K> getById(@PathVariable String id) {
        return form8KService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form8K> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form8KService.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form8K>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form8KService.findByCik(cik, pageRequest));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form8K>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form8KService.findByTradingSymbol(symbol.toUpperCase(), pageRequest));
    }

    @GetMapping("/date-range")
    public ResponseEntity<Page<Form8K>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form8KService.findByFiledDateRange(startDate, endDate, pageRequest));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Form8K>> getRecentFilings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(form8KService.findRecentFilings(Math.min(limit, 100)));
    }

    @PostMapping("/download")
    public ResponseEntity<Form8K> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String items) {

        if (form8KService.existsByAccessionNumber(accessionNumber)) {
            return form8KService.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form8K form8K = form8KService.downloadAndParse(cik, accessionNumber, primaryDocument).join();
            if (form8K == null) {
                return ResponseEntity.badRequest().build();
            }

            if (companyName != null && !companyName.isBlank()) {
                form8K.setCompanyName(companyName);
            }
            if (items != null && !items.isBlank() && (form8K.getItems() == null || form8K.getItems().isBlank())) {
                form8K.setItems(items);
            }
            if (filedDate != null) {
                form8K.setFiledDate(filedDate);
            }
            if (reportDate != null) {
                form8K.setReportDate(reportDate);
            }

            Form8K saved = form8KService.save(form8K);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to download/parse Form 8-K: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Form8K> save(@RequestBody Form8K form8K) {
        Form8K saved = form8KService.save(form8K);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form8KService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form8KService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}

