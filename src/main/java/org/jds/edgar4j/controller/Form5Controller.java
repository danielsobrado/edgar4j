package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.service.Form5Service;
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
 * REST API for Form 5 annual ownership filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form5")
@RequiredArgsConstructor
public class Form5Controller {

    private final Form5Service form5Service;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/{id}")
    public ResponseEntity<Form5> getById(@PathVariable String id) {
        return form5Service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form5> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form5Service.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form5>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form5Service.findByCik(cik, pageRequest));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form5>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form5Service.findByTradingSymbol(symbol.toUpperCase(), pageRequest));
    }

    @GetMapping("/date-range")
    public ResponseEntity<Page<Form5>> getByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
            return ResponseEntity.ok(form5Service.findByFiledDateRange(start, end, pageRequest));
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Form5>> getRecentFilings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(form5Service.findRecentFilings(Math.min(limit, 100)));
    }

    @PostMapping("/download")
    public ResponseEntity<Form5> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String filedDate) {

        if (form5Service.existsByAccessionNumber(accessionNumber)) {
            return form5Service.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form5 form5 = form5Service.downloadAndParse(cik, accessionNumber, primaryDocument).join();
            if (form5 == null) {
                return ResponseEntity.badRequest().build();
            }

            if (companyName != null && !companyName.isBlank()) {
                form5.setIssuerName(companyName);
            }
            if (filedDate != null && !filedDate.isBlank()) {
                form5.setFiledDate(parseDate(filedDate));
            }

            Form5 saved = form5Service.save(form5);
            return ResponseEntity.ok(saved);

        } catch (DateTimeParseException e) {
            log.warn("Invalid date format in request: filedDate={}", filedDate);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to download/parse Form 5: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Form5> save(@RequestBody Form5 form5) {
        Form5 saved = form5Service.save(form5);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form5Service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form5Service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMAT);
    }
}
