package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.repository.Form13DGRepository.BeneficialOwnerSummary;
import org.jds.edgar4j.repository.Form13DGRepository.OwnerPortfolioEntry;
import org.jds.edgar4j.repository.Form13DGRepository.OwnershipHistoryEntry;
import org.jds.edgar4j.repository.Form13DGRepository.ScheduleTypeCount;
import org.jds.edgar4j.service.Form13DGService;
import org.jds.edgar4j.service.Form13DGService.BeneficialOwnershipSnapshot;
import org.jds.edgar4j.service.Form13DGService.OwnershipComparison;
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
 * REST API for Schedule 13D/13G beneficial ownership filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form13dg")
@RequiredArgsConstructor
public class Form13DGController {

    private final Form13DGService form13DGService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ========== BASIC CRUD ==========

    /**
     * Get Schedule 13D/G by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Form13DG> getById(@PathVariable String id) {
        return form13DGService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get Schedule 13D/G by accession number.
     */
    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form13DG> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form13DGService.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Save a Schedule 13D/G filing.
     */
    @PostMapping
    public ResponseEntity<Form13DG> save(@RequestBody Form13DG form13DG) {
        Form13DG saved = form13DGService.save(form13DG);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a Schedule 13D/G filing.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form13DGService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form13DGService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ========== SCHEDULE TYPE QUERIES ==========

    /**
     * Get filings by schedule type (13D or 13G).
     */
    @GetMapping("/schedule/{scheduleType}")
    public ResponseEntity<Page<Form13DG>> getByScheduleType(
            @PathVariable String scheduleType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByScheduleType(scheduleType.toUpperCase(), pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get recent filings (all types).
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Form13DG>> getRecentFilings(
            @RequestParam(defaultValue = "20") int limit) {
        List<Form13DG> results = form13DGService.findRecentFilings(Math.min(limit, 100));
        return ResponseEntity.ok(results);
    }

    /**
     * Get recent 13D filings (activist investors).
     */
    @GetMapping("/recent/13d")
    public ResponseEntity<List<Form13DG>> getRecent13DFilings(
            @RequestParam(defaultValue = "10") int limit) {
        List<Form13DG> results = form13DGService.findRecent13DFilings(Math.min(limit, 50));
        return ResponseEntity.ok(results);
    }

    /**
     * Get recent 13G filings (passive investors).
     */
    @GetMapping("/recent/13g")
    public ResponseEntity<List<Form13DG>> getRecent13GFilings(
            @RequestParam(defaultValue = "10") int limit) {
        List<Form13DG> results = form13DGService.findRecent13GFilings(Math.min(limit, 50));
        return ResponseEntity.ok(results);
    }

    /**
     * Get filing counts by schedule type for a date range.
     */
    @GetMapping("/stats/counts")
    public ResponseEntity<List<ScheduleTypeCount>> getFilingCounts(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            List<ScheduleTypeCount> counts = form13DGService.getFilingCountsByScheduleType(start, end);
            return ResponseEntity.ok(counts);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    // ========== ISSUER QUERIES ==========

    /**
     * Get filings for a specific issuer by CIK.
     */
    @GetMapping("/issuer/cik/{issuerCik}")
    public ResponseEntity<Page<Form13DG>> getByIssuerCik(
            @PathVariable String issuerCik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByIssuerCik(issuerCik, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Search filings by issuer name.
     */
    @GetMapping("/issuer")
    public ResponseEntity<Page<Form13DG>> searchByIssuerName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByIssuerName(name, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get filings for a specific security by CUSIP.
     */
    @GetMapping("/cusip/{cusip}")
    public ResponseEntity<Page<Form13DG>> getByCusip(
            @PathVariable String cusip,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByCusip(cusip.toUpperCase(), pageRequest);
        return ResponseEntity.ok(results);
    }

    // ========== BENEFICIAL OWNER QUERIES ==========

    /**
     * Get filings by beneficial owner CIK.
     */
    @GetMapping("/filer/cik/{filingPersonCik}")
    public ResponseEntity<Page<Form13DG>> getByFilingPersonCik(
            @PathVariable String filingPersonCik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByFilingPersonCik(filingPersonCik, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Search filings by beneficial owner name.
     */
    @GetMapping("/filer")
    public ResponseEntity<Page<Form13DG>> searchByFilingPersonName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Form13DG> results = form13DGService.findByFilingPersonName(name, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get portfolio of a beneficial owner (all their holdings).
     */
    @GetMapping("/filer/cik/{filingPersonCik}/portfolio")
    public ResponseEntity<List<OwnerPortfolioEntry>> getOwnerPortfolio(
            @PathVariable String filingPersonCik) {
        List<OwnerPortfolioEntry> portfolio = form13DGService.getOwnerPortfolio(filingPersonCik);
        return ResponseEntity.ok(portfolio);
    }

    // ========== DATE QUERIES ==========

    /**
     * Get filings by event date range.
     */
    @GetMapping("/event-date-range")
    public ResponseEntity<Page<Form13DG>> getByEventDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventDate"));
            Page<Form13DG> results = form13DGService.findByEventDateRange(start, end, pageRequest);
            return ResponseEntity.ok(results);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get filings by filed date range.
     */
    @GetMapping("/filed-date-range")
    public ResponseEntity<Page<Form13DG>> getByFiledDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
            Page<Form13DG> results = form13DGService.findByFiledDateRange(start, end, pageRequest);
            return ResponseEntity.ok(results);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {} or {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }
    }

    // ========== OWNERSHIP QUERIES ==========

    /**
     * Get filings with ownership exceeding threshold.
     */
    @GetMapping("/ownership/min")
    public ResponseEntity<Page<Form13DG>> getByMinOwnership(
            @RequestParam Double minPercent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "percentOfClass"));
        Page<Form13DG> results = form13DGService.findByMinPercentOfClass(minPercent, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get filings with 10%+ ownership.
     */
    @GetMapping("/ownership/ten-percent")
    public ResponseEntity<Page<Form13DG>> getTenPercentOwners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "percentOfClass"));
        Page<Form13DG> results = form13DGService.findTenPercentOwners(pageRequest);
        return ResponseEntity.ok(results);
    }

    // ========== AMENDMENTS ==========

    /**
     * Get all amendments.
     */
    @GetMapping("/amendments")
    public ResponseEntity<Page<Form13DG>> getAmendments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        Page<Form13DG> results = form13DGService.findAmendments(pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get all initial filings (non-amendments).
     */
    @GetMapping("/initial-filings")
    public ResponseEntity<Page<Form13DG>> getInitialFilings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "filedDate"));
        Page<Form13DG> results = form13DGService.findInitialFilings(pageRequest);
        return ResponseEntity.ok(results);
    }

    // ========== ANALYTICS ==========

    /**
     * Get top beneficial owners for a security by CUSIP.
     */
    @GetMapping("/cusip/{cusip}/top-owners")
    public ResponseEntity<List<BeneficialOwnerSummary>> getTopBeneficialOwners(
            @PathVariable String cusip,
            @RequestParam(defaultValue = "10") int limit) {

        List<BeneficialOwnerSummary> owners = form13DGService.getTopBeneficialOwners(cusip.toUpperCase(), Math.min(limit, 50));
        return ResponseEntity.ok(owners);
    }

    /**
     * Get complete beneficial ownership snapshot for a CUSIP.
     */
    @GetMapping("/cusip/{cusip}/ownership")
    public ResponseEntity<BeneficialOwnershipSnapshot> getBeneficialOwnershipSnapshot(
            @PathVariable String cusip) {

        BeneficialOwnershipSnapshot snapshot = form13DGService.getBeneficialOwnershipSnapshot(cusip.toUpperCase());
        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(snapshot);
    }

    /**
     * Get ownership history for a filer-issuer combination.
     */
    @GetMapping("/filer/{filingPersonCik}/issuer/{issuerCik}/history")
    public ResponseEntity<List<OwnershipHistoryEntry>> getOwnershipHistory(
            @PathVariable String filingPersonCik,
            @PathVariable String issuerCik) {

        List<OwnershipHistoryEntry> history = form13DGService.getOwnershipHistory(filingPersonCik, issuerCik);
        return ResponseEntity.ok(history);
    }

    /**
     * Compare ownership changes for a filer-issuer pair.
     */
    @GetMapping("/filer/{filingPersonCik}/issuer/{issuerCik}/compare")
    public ResponseEntity<OwnershipComparison> compareOwnership(
            @PathVariable String filingPersonCik,
            @PathVariable String issuerCik) {

        OwnershipComparison comparison = form13DGService.compareOwnership(filingPersonCik, issuerCik);
        if (comparison == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(comparison);
    }

    /**
     * Get recent activist filings (13D with purpose).
     */
    @GetMapping("/activist")
    public ResponseEntity<List<Form13DG>> getRecentActivistFilings(
            @RequestParam(defaultValue = "20") int limit) {

        List<Form13DG> filings = form13DGService.getRecentActivistFilings(Math.min(limit, 100));
        return ResponseEntity.ok(filings);
    }

    // ========== DOWNLOAD AND PARSE ==========

    /**
     * Download and parse a Schedule 13D/G filing.
     */
    @PostMapping("/download")
    public ResponseEntity<Form13DG> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String document) {

        // Check if already exists
        if (form13DGService.existsByAccessionNumber(accessionNumber)) {
            return form13DGService.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form13DG form13DG = form13DGService.downloadAndParse(cik, accessionNumber, document)
                    .join();

            if (form13DG == null) {
                return ResponseEntity.badRequest().build();
            }

            Form13DG saved = form13DGService.save(form13DG);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to download/parse Schedule 13D/G: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }
}
