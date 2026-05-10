package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.service.Form3Service;
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
 * REST API for Form 3 initial ownership filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form3")
@RequiredArgsConstructor
public class Form3Controller {

    private final Form3Service form3Service;

    @GetMapping("/{id}")
    public ResponseEntity<Form3> getById(@PathVariable String id) {
        return form3Service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form3> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form3Service.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form3>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form3Service.findByCik(cik, pageRequest));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form3>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form3Service.findByTradingSymbol(symbol.toUpperCase(), pageRequest));
    }

    @GetMapping("/date-range")
    public ResponseEntity<Page<Form3>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "filedDate");
        return ResponseEntity.ok(form3Service.findByFiledDateRange(startDate, endDate, pageRequest));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Form3>> getRecentFilings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(form3Service.findRecentFilings(Math.min(limit, 100)));
    }

    @PostMapping("/download")
    public ResponseEntity<Form3> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedDate) {

        if (form3Service.existsByAccessionNumber(accessionNumber)) {
            return form3Service.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form3 form3 = form3Service.downloadAndParse(cik, accessionNumber, primaryDocument).join();
            if (form3 == null) {
                return ResponseEntity.badRequest().build();
            }

            if (companyName != null && !companyName.isBlank()) {
                form3.setIssuerName(companyName);
            }
            if (filedDate != null) {
                form3.setFiledDate(filedDate);
            }

            Form3 saved = form3Service.save(form3);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to download/parse Form 3: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Form3> save(@RequestBody Form3 form3) {
        Form3 saved = form3Service.save(form3);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form3Service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form3Service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
