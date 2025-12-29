package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.repository.Form13DGRepository.BeneficialOwnerSummary;
import org.jds.edgar4j.repository.Form13DGRepository.OwnerPortfolioEntry;
import org.jds.edgar4j.repository.Form13DGRepository.OwnershipHistoryEntry;
import org.jds.edgar4j.repository.Form13DGRepository.ScheduleTypeCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Schedule 13D/13G filings.
 *
 * Schedule 13D: Filed when acquiring >5% ownership with intent to influence.
 * Schedule 13G: Filed when acquiring >5% ownership with passive intent.
 */
public interface Form13DGService {

    // ========== DOWNLOAD AND PARSE ==========

    /**
     * Downloads Schedule 13D/13G document from SEC EDGAR.
     */
    CompletableFuture<String> downloadForm13DG(String cik, String accessionNumber, String document);

    /**
     * Parses Schedule 13D/13G XML content into domain model.
     */
    Form13DG parse(String xml, String accessionNumber);

    /**
     * Downloads and parses a complete Schedule 13D/13G filing.
     */
    CompletableFuture<Form13DG> downloadAndParse(String cik, String accessionNumber, String document);

    // ========== CRUD OPERATIONS ==========

    /**
     * Saves or updates a Schedule 13D/13G filing.
     */
    Form13DG save(Form13DG form13DG);

    /**
     * Saves multiple Schedule 13D/13G filings.
     */
    List<Form13DG> saveAll(List<Form13DG> form13DGList);

    /**
     * Finds Schedule 13D/13G by accession number.
     */
    Optional<Form13DG> findByAccessionNumber(String accessionNumber);

    /**
     * Finds Schedule 13D/13G by ID.
     */
    Optional<Form13DG> findById(String id);

    /**
     * Deletes a Schedule 13D/13G filing by ID.
     */
    void deleteById(String id);

    /**
     * Checks if a Schedule 13D/13G filing exists.
     */
    boolean existsByAccessionNumber(String accessionNumber);

    // ========== SEARCH BY SCHEDULE TYPE ==========

    /**
     * Finds filings by schedule type (13D or 13G).
     */
    Page<Form13DG> findByScheduleType(String scheduleType, Pageable pageable);

    /**
     * Finds filings by form type (SC 13D, SC 13G, etc.).
     */
    Page<Form13DG> findByFormType(String formType, Pageable pageable);

    /**
     * Counts filings by schedule type.
     */
    long countByScheduleType(String scheduleType);

    // ========== SEARCH BY ISSUER ==========

    /**
     * Finds filings for a specific issuer by CIK.
     */
    Page<Form13DG> findByIssuerCik(String issuerCik, Pageable pageable);

    /**
     * Finds filings for a specific issuer by name.
     */
    Page<Form13DG> findByIssuerName(String issuerName, Pageable pageable);

    /**
     * Finds filings for a specific security by CUSIP.
     */
    Page<Form13DG> findByCusip(String cusip, Pageable pageable);

    // ========== SEARCH BY BENEFICIAL OWNER ==========

    /**
     * Finds filings by beneficial owner CIK.
     */
    Page<Form13DG> findByFilingPersonCik(String filingPersonCik, Pageable pageable);

    /**
     * Finds filings by beneficial owner name.
     */
    Page<Form13DG> findByFilingPersonName(String filingPersonName, Pageable pageable);

    // ========== SEARCH BY DATE ==========

    /**
     * Finds filings by event date range.
     */
    Page<Form13DG> findByEventDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Finds filings by filed date range.
     */
    Page<Form13DG> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // ========== SEARCH BY OWNERSHIP ==========

    /**
     * Finds filings where ownership exceeds threshold.
     */
    Page<Form13DG> findByMinPercentOfClass(Double minPercent, Pageable pageable);

    /**
     * Finds filings where ownership is 10% or more.
     */
    Page<Form13DG> findTenPercentOwners(Pageable pageable);

    /**
     * Finds filings where shares exceed threshold.
     */
    Page<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares, Pageable pageable);

    // ========== RECENT FILINGS ==========

    /**
     * Finds most recent filings.
     */
    List<Form13DG> findRecentFilings(int limit);

    /**
     * Finds most recent 13D filings (activist).
     */
    List<Form13DG> findRecent13DFilings(int limit);

    /**
     * Finds most recent 13G filings (passive).
     */
    List<Form13DG> findRecent13GFilings(int limit);

    // ========== AMENDMENTS ==========

    /**
     * Finds all amendments.
     */
    Page<Form13DG> findAmendments(Pageable pageable);

    /**
     * Finds all initial filings.
     */
    Page<Form13DG> findInitialFilings(Pageable pageable);

    // ========== ANALYTICS ==========

    /**
     * Gets top beneficial owners for a security by CUSIP.
     */
    List<BeneficialOwnerSummary> getTopBeneficialOwners(String cusip, int limit);

    /**
     * Gets ownership history for a filer-issuer combination.
     */
    List<OwnershipHistoryEntry> getOwnershipHistory(String filingPersonCik, String issuerCik);

    /**
     * Gets filing counts by schedule type for a date range.
     */
    List<ScheduleTypeCount> getFilingCountsByScheduleType(LocalDate startDate, LocalDate endDate);

    /**
     * Gets recent activist filings (13D with purpose).
     */
    List<Form13DG> getRecentActivistFilings(int limit);

    /**
     * Gets portfolio of beneficial owner (all their holdings).
     */
    List<OwnerPortfolioEntry> getOwnerPortfolio(String filingPersonCik);

    /**
     * Compares ownership between two dates for a filer-issuer pair.
     */
    OwnershipComparison compareOwnership(String filingPersonCik, String issuerCik);

    /**
     * Gets all beneficial owners for a specific CUSIP with latest data.
     */
    BeneficialOwnershipSnapshot getBeneficialOwnershipSnapshot(String cusip);

    // ========== RECORD TYPES ==========

    /**
     * Comparison of ownership over time.
     */
    record OwnershipComparison(
        String filingPersonCik,
        String filingPersonName,
        String issuerCik,
        String issuerName,
        String cusip,
        List<OwnershipDataPoint> history,
        Double earliestPercent,
        Double latestPercent,
        Double percentChange,
        Long earliestShares,
        Long latestShares,
        Long sharesChange
    ) {}

    record OwnershipDataPoint(
        LocalDate eventDate,
        Double percentOfClass,
        Long sharesBeneficiallyOwned,
        String formType
    ) {}

    /**
     * Snapshot of all beneficial owners for a security.
     */
    record BeneficialOwnershipSnapshot(
        String cusip,
        String securityTitle,
        String issuerName,
        String issuerCik,
        List<BeneficialOwnerDetail> beneficialOwners,
        Double totalPercentOwned,
        Long totalSharesOwned,
        int activistCount,
        int passiveCount,
        LocalDate asOfDate
    ) {}

    record BeneficialOwnerDetail(
        String filingPersonCik,
        String filingPersonName,
        Double percentOfClass,
        Long sharesBeneficiallyOwned,
        LocalDate eventDate,
        String scheduleType,
        boolean isActivist
    ) {}
}
