package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.service.Form20FService;
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
 * REST API for Form 20-F annual reports (foreign private issuers).
 */
@Slf4j
@RestController
@RequestMapping("/api/form20f")
@RequiredArgsConstructor
public class Form20FController {

    private final Form20FService form20FService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/{id}")
    public ResponseEntity<Form20F> getById(@PathVariable String id) {
        return form20FService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form20F> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form20FService.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form20F>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form20FService.findByCik(cik, pageRequest));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<Page<Form20F>> getBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        return ResponseEntity.ok(form20FService.findByTradingSymbol(symbol.toUpperCase(), pageRequest));
    }

    @GetMapping("/date-range")
    public ResponseEntity<Page<Form20F>> getByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
            return ResponseEntity.ok(form20FService.findByFiledDateRange(start, end, pageRequest));
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Form20F>> getRecentFilings(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(form20FService.findRecentFilings(Math.min(limit, 100)));
    }

    @PostMapping("/download")
    public ResponseEntity<Form20F> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String filedDate,
            @RequestParam(required = false) String reportDate) {

        if (form20FService.existsByAccessionNumber(accessionNumber)) {
            return form20FService.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form20F form20F = form20FService.downloadAndParse(cik, accessionNumber, primaryDocument).join();
            if (form20F == null) {
                return ResponseEntity.badRequest().build();
            }

            if (companyName != null && !companyName.isBlank()) {
                form20F.setCompanyName(companyName);
            }
            if (filedDate != null && !filedDate.isBlank()) {
                form20F.setFiledDate(parseDate(filedDate));
            }
            if (reportDate != null && !reportDate.isBlank()) {
                form20F.setReportDate(parseDate(reportDate));
            }

            Form20F saved = form20FService.save(form20F);
            return ResponseEntity.ok(saved);

        } catch (DateTimeParseException e) {
            log.warn("Invalid date format in request: filedDate={}, reportDate={}", filedDate, reportDate);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to download/parse Form 20-F: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Form20F> save(@RequestBody Form20F form20F) {
        Form20F saved = form20FService.save(form20F);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form20FService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form20FService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }
}

