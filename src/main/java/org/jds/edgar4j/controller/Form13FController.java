package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.repository.Form13FRepository.FilerSummary;
import org.jds.edgar4j.repository.Form13FRepository.HoldingSummary;
import org.jds.edgar4j.repository.Form13FRepository.PortfolioSnapshot;
import org.jds.edgar4j.service.Form13FService;
import org.jds.edgar4j.service.Form13FService.HoldingsComparison;
import org.jds.edgar4j.service.Form13FService.InstitutionalOwnershipStats;
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
 * REST API for Form 13F institutional holdings filings.
 */
@Slf4j
@RestController
@RequestMapping("/api/form13f")
@RequiredArgsConstructor
public class Form13FController {

    private final Form13FService form13FService;

    /**
     * Get Form 13F by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Form13F> getById(@PathVariable String id) {
        return form13FService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get Form 13F by accession number.
     */
    @GetMapping("/accession/{accessionNumber}")
    public ResponseEntity<Form13F> getByAccessionNumber(@PathVariable String accessionNumber) {
        return form13FService.findByAccessionNumber(accessionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get Form 13F filings by CIK.
     */
    @GetMapping("/cik/{cik}")
    public ResponseEntity<Page<Form13F>> getByCik(
            @PathVariable String cik,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByCik(cik, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Search Form 13F filings by filer name.
     */
    @GetMapping("/filer")
    public ResponseEntity<Page<Form13F>> searchByFilerName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByFilerName(name, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 13F filings for a specific quarter.
     */
    @GetMapping("/quarter")
    public ResponseEntity<Page<Form13F>> getByQuarter(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "totalValue");
        Page<Form13F> results = form13FService.findByReportPeriod(period, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 13F filings within date range.
     */
    @GetMapping("/date-range")
    public ResponseEntity<Page<Form13F>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByReportPeriodRange(startDate, endDate, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get Form 13F filings by CIK and date range.
     */
    @GetMapping("/cik/{cik}/date-range")
    public ResponseEntity<Page<Form13F>> getByCikAndDateRange(
            @PathVariable String cik,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByCikAndReportPeriodRange(cik, startDate, endDate, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get recent Form 13F filings.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Form13F>> getRecentFilings(
            @RequestParam(defaultValue = "10") int limit) {
        List<Form13F> results = form13FService.findRecentFilings(Math.min(limit, 100));
        return ResponseEntity.ok(results);
    }

    /**
     * Get holdings for a specific filing.
     */
    @GetMapping("/accession/{accessionNumber}/holdings")
    public ResponseEntity<List<Form13FHolding>> getHoldings(@PathVariable String accessionNumber) {
        List<Form13FHolding> holdings = form13FService.getHoldings(accessionNumber);
        if (holdings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(holdings);
    }

    /**
     * Search for filings containing a specific CUSIP.
     */
    @GetMapping("/cusip/{cusip}")
    public ResponseEntity<Page<Form13F>> getByHoldingCusip(
            @PathVariable String cusip,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByHoldingCusip(cusip.toUpperCase(), pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Search for filings containing holdings with specific issuer name.
     */
    @GetMapping("/issuer")
    public ResponseEntity<Page<Form13F>> getByHoldingIssuer(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PaginationUtils.pageRequest(page, size, "reportPeriod");
        Page<Form13F> results = form13FService.findByHoldingIssuerName(name, pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get top filers by portfolio value for a quarter.
     */
    @GetMapping("/top-filers")
    public ResponseEntity<List<FilerSummary>> getTopFilers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period,
            @RequestParam(defaultValue = "10") int limit) {
        List<FilerSummary> results = form13FService.getTopFilers(period, Math.min(limit, 100));
        return ResponseEntity.ok(results);
    }

    /**
     * Get top holdings by value for a quarter.
     */
    @GetMapping("/top-holdings")
    public ResponseEntity<List<HoldingSummary>> getTopHoldings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period,
            @RequestParam(defaultValue = "10") int limit) {
        List<HoldingSummary> results = form13FService.getTopHoldings(period, Math.min(limit, 100));
        return ResponseEntity.ok(results);
    }

    /**
     * Get portfolio history for a filer.
     */
    @GetMapping("/cik/{cik}/history")
    public ResponseEntity<List<PortfolioSnapshot>> getPortfolioHistory(@PathVariable String cik) {
        List<PortfolioSnapshot> history = form13FService.getPortfolioHistory(cik);
        return ResponseEntity.ok(history);
    }

    /**
     * Get institutional ownership statistics for a CUSIP.
     */
    @GetMapping("/cusip/{cusip}/ownership")
    public ResponseEntity<InstitutionalOwnershipStats> getInstitutionalOwnership(
            @PathVariable String cusip,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {

        InstitutionalOwnershipStats stats = form13FService.getInstitutionalOwnership(cusip.toUpperCase(), period);
        return ResponseEntity.ok(stats);
    }

    /**
     * Compare holdings between two quarters for a filer.
     */
    @GetMapping("/cik/{cik}/compare")
    public ResponseEntity<HoldingsComparison> compareHoldings(
            @PathVariable String cik,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2) {

        HoldingsComparison comparison = form13FService.compareHoldings(cik, period1, period2);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Download and parse a Form 13F filing.
     */
    @PostMapping("/download")
    public ResponseEntity<Form13F> downloadAndParse(
            @RequestParam String cik,
            @RequestParam String accessionNumber,
            @RequestParam String primaryDocument,
            @RequestParam String infoTableDocument) {

        // Check if already exists
        if (form13FService.existsByAccessionNumber(accessionNumber)) {
            return form13FService.findByAccessionNumber(accessionNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            Form13F form13F = form13FService.downloadAndParseForm13F(cik, accessionNumber, primaryDocument, infoTableDocument)
                    .join();

            if (form13F == null) {
                return ResponseEntity.badRequest().build();
            }

            Form13F saved = form13FService.save(form13F);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to download/parse Form 13F: {}", accessionNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Save a Form 13F filing (manual entry or re-processing).
     */
    @PostMapping
    public ResponseEntity<Form13F> save(@RequestBody Form13F form13F) {
        Form13F saved = form13FService.save(form13F);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a Form 13F filing.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (form13FService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        form13FService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
