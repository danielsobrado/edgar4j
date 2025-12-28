package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.repository.Form13FRepository.FilerSummary;
import org.jds.edgar4j.repository.Form13FRepository.HoldingSummary;
import org.jds.edgar4j.repository.Form13FRepository.PortfolioSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 13F filings.
 * Form 13F is filed quarterly by institutional investment managers with
 * over $100 million in qualifying assets.
 */
public interface Form13FService {

    /**
     * Downloads Form 13F Information Table XML from SEC EDGAR.
     */
    CompletableFuture<String> downloadForm13F(String cik, String accessionNumber, String infoTableDocument);

    /**
     * Downloads Form 13F primary document (cover page/metadata) from SEC EDGAR.
     */
    CompletableFuture<String> downloadPrimaryDocument(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parses Form 13F Information Table XML content into domain model.
     */
    Form13F parseInformationTable(String xml, String accessionNumber);

    /**
     * Parses and adds metadata from primary document.
     */
    void parseAndAddMetadata(Form13F form13F, String primaryDocXml);

    /**
     * Downloads and parses a complete Form 13F filing.
     */
    CompletableFuture<Form13F> downloadAndParseForm13F(String cik, String accessionNumber,
            String primaryDocument, String infoTableDocument);

    /**
     * Saves or updates a Form 13F filing.
     */
    Form13F save(Form13F form13F);

    /**
     * Saves multiple Form 13F filings.
     */
    List<Form13F> saveAll(List<Form13F> form13FList);

    /**
     * Finds Form 13F by accession number.
     */
    Optional<Form13F> findByAccessionNumber(String accessionNumber);

    /**
     * Finds Form 13F by ID.
     */
    Optional<Form13F> findById(String id);

    /**
     * Finds all Form 13F filings for a CIK.
     */
    Page<Form13F> findByCik(String cik, Pageable pageable);

    /**
     * Finds Form 13F filings by filer name.
     */
    Page<Form13F> findByFilerName(String filerName, Pageable pageable);

    /**
     * Finds Form 13F filings for a specific quarter.
     */
    Page<Form13F> findByReportPeriod(LocalDate reportPeriod, Pageable pageable);

    /**
     * Finds Form 13F filings within a date range.
     */
    Page<Form13F> findByReportPeriodRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Finds Form 13F filings by CIK and report period range.
     */
    Page<Form13F> findByCikAndReportPeriodRange(String cik, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Finds Form 13F filings containing a specific CUSIP.
     */
    Page<Form13F> findByHoldingCusip(String cusip, Pageable pageable);

    /**
     * Finds Form 13F filings containing holdings with specific issuer name.
     */
    Page<Form13F> findByHoldingIssuerName(String issuerName, Pageable pageable);

    /**
     * Finds recent Form 13F filings.
     */
    List<Form13F> findRecentFilings(int limit);

    /**
     * Finds recent Form 13F filings for a specific CIK.
     */
    List<Form13F> findRecentFilingsByCik(String cik, int limit);

    /**
     * Checks if a Form 13F filing exists.
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Checks if a Form 13F filing exists for CIK and period.
     */
    boolean existsByCikAndReportPeriod(String cik, LocalDate reportPeriod);

    /**
     * Deletes a Form 13F filing by ID.
     */
    void deleteById(String id);

    /**
     * Gets top filers by total portfolio value for a quarter.
     */
    List<FilerSummary> getTopFilers(LocalDate reportPeriod, int limit);

    /**
     * Gets most widely held securities for a quarter.
     */
    List<HoldingSummary> getTopHoldings(LocalDate reportPeriod, int limit);

    /**
     * Gets portfolio history for a specific filer.
     */
    List<PortfolioSnapshot> getPortfolioHistory(String cik);

    /**
     * Gets holdings for a specific filing.
     */
    List<Form13FHolding> getHoldings(String accessionNumber);

    /**
     * Gets holdings for a specific filing by CUSIP.
     */
    Optional<Form13FHolding> getHoldingByCusip(String accessionNumber, String cusip);

    /**
     * Gets institutional ownership statistics for a CUSIP.
     */
    InstitutionalOwnershipStats getInstitutionalOwnership(String cusip, LocalDate reportPeriod);

    /**
     * Compares holdings between two quarters for a filer.
     */
    HoldingsComparison compareHoldings(String cik, LocalDate period1, LocalDate period2);

    /**
     * Institutional ownership statistics for a security.
     */
    record InstitutionalOwnershipStats(
        String cusip,
        String issuerName,
        LocalDate reportPeriod,
        int institutionCount,
        long totalShares,
        long totalValue,
        List<TopHolder> topHolders
    ) {}

    record TopHolder(
        String cik,
        String filerName,
        long shares,
        long value
    ) {}

    /**
     * Holdings comparison between two periods.
     */
    record HoldingsComparison(
        String cik,
        String filerName,
        LocalDate period1,
        LocalDate period2,
        List<HoldingChange> newPositions,
        List<HoldingChange> closedPositions,
        List<HoldingChange> increasedPositions,
        List<HoldingChange> decreasedPositions,
        long totalValueChange
    ) {}

    record HoldingChange(
        String cusip,
        String issuerName,
        Long sharesPeriod1,
        Long sharesPeriod2,
        Long valuePeriod1,
        Long valuePeriod2,
        Double percentChange
    ) {}
}
