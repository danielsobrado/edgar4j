package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13F;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 13F SEC filings (institutional holdings).
 */
@Repository
public interface Form13FRepository extends MongoRepository<Form13F, String> {

    // Find by accession number (unique identifier)
    Optional<Form13F> findByAccessionNumber(String accessionNumber);

    // Find by CIK (filer's Central Index Key)
    List<Form13F> findByCik(String cik);

    Page<Form13F> findByCik(String cik, Pageable pageable);

    // Find by filer name
    List<Form13F> findByFilerName(String filerName);

    List<Form13F> findByFilerNameContainingIgnoreCase(String filerName);

    Page<Form13F> findByFilerNameContainingIgnoreCase(String filerName, Pageable pageable);

    // Find by report period
    List<Form13F> findByReportPeriod(LocalDate reportPeriod);

    Page<Form13F> findByReportPeriod(LocalDate reportPeriod, Pageable pageable);

    // Find by report period range
    @Query("{ 'reportPeriod': { $gte: ?0, $lte: ?1 } }")
    List<Form13F> findByReportPeriodBetween(LocalDate startDate, LocalDate endDate);

    @Query("{ 'reportPeriod': { $gte: ?0, $lte: ?1 } }")
    Page<Form13F> findByReportPeriodBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Find by CIK and report period range
    @Query("{ 'cik': ?0, 'reportPeriod': { $gte: ?1, $lte: ?2 } }")
    Page<Form13F> findByCikAndReportPeriodBetween(String cik, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("{ 'cik': ?0, 'reportPeriod': { $gte: ?1, $lte: ?2 } }")
    List<Form13F> findByCikAndReportPeriodBetweenList(String cik, LocalDate startDate, LocalDate endDate);

    // Find by filed date range
    List<Form13F> findByFiledDateBetween(LocalDate startDate, LocalDate endDate);

    Page<Form13F> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Find recent filings
    List<Form13F> findTop10ByOrderByFiledDateDesc();

    List<Form13F> findTop10ByCikOrderByReportPeriodDesc(String cik);

    // Find filings with specific holdings (by CUSIP)
    @Query("{ 'holdings.cusip': ?0 }")
    List<Form13F> findByHoldingCusip(String cusip);

    @Query("{ 'holdings.cusip': ?0 }")
    Page<Form13F> findByHoldingCusip(String cusip, Pageable pageable);

    // Find filings with specific holdings (by issuer name)
    @Query("{ 'holdings.nameOfIssuer': { $regex: ?0, $options: 'i' } }")
    List<Form13F> findByHoldingIssuerName(String issuerName);

    @Query("{ 'holdings.nameOfIssuer': { $regex: ?0, $options: 'i' } }")
    Page<Form13F> findByHoldingIssuerName(String issuerName, Pageable pageable);

    // Find by report type
    List<Form13F> findByReportType(String reportType);

    // Find amended filings
    @Query("{ 'amendmentType': { $ne: null } }")
    List<Form13F> findAmendedFilings();

    @Query("{ 'amendmentType': { $ne: null } }")
    Page<Form13F> findAmendedFilings(Pageable pageable);

    // Find by total value range
    @Query("{ 'totalValue': { $gte: ?0 } }")
    List<Form13F> findByMinTotalValue(Long minValue);

    @Query("{ 'totalValue': { $gte: ?0 } }")
    Page<Form13F> findByMinTotalValue(Long minValue, Pageable pageable);

    @Query("{ 'totalValue': { $gte: ?0, $lte: ?1 } }")
    Page<Form13F> findByTotalValueBetween(Long minValue, Long maxValue, Pageable pageable);

    // Find by holdings count range
    @Query("{ 'holdingsCount': { $gte: ?0 } }")
    Page<Form13F> findByMinHoldingsCount(Integer minCount, Pageable pageable);

    // Count queries
    long countByReportPeriod(LocalDate reportPeriod);

    long countByCik(String cik);

    // Existence check
    boolean existsByAccessionNumber(String accessionNumber);

    boolean existsByCikAndReportPeriod(String cik, LocalDate reportPeriod);

    // Delete by accession number
    void deleteByAccessionNumber(String accessionNumber);

    // Aggregation for top filers by total value
    @Aggregation(pipeline = {
        "{ $match: { 'reportPeriod': ?0 } }",
        "{ $group: { _id: { cik: '$cik', filerName: '$filerName' }, totalValue: { $sum: '$totalValue' }, holdingsCount: { $sum: '$holdingsCount' } } }",
        "{ $sort: { totalValue: -1 } }",
        "{ $limit: ?1 }"
    })
    List<FilerSummary> getTopFilersByPeriod(LocalDate reportPeriod, int limit);

    // Aggregation for holdings summary by period
    @Aggregation(pipeline = {
        "{ $match: { 'reportPeriod': ?0 } }",
        "{ $unwind: '$holdings' }",
        "{ $group: { _id: '$holdings.cusip', issuerName: { $first: '$holdings.nameOfIssuer' }, totalShares: { $sum: '$holdings.sharesOrPrincipalAmount' }, totalValue: { $sum: '$holdings.value' }, filerCount: { $sum: 1 } } }",
        "{ $sort: { totalValue: -1 } }",
        "{ $limit: ?1 }"
    })
    List<HoldingSummary> getTopHoldingsByPeriod(LocalDate reportPeriod, int limit);

    // Aggregation for filer's portfolio over time
    @Aggregation(pipeline = {
        "{ $match: { 'cik': ?0 } }",
        "{ $sort: { 'reportPeriod': -1 } }",
        "{ $project: { reportPeriod: 1, totalValue: 1, holdingsCount: 1 } }"
    })
    List<PortfolioSnapshot> getPortfolioHistory(String cik);

    /**
     * Projection for filer summary aggregation.
     */
    interface FilerSummary {
        FilerKey getId();
        Long getTotalValue();
        Integer getHoldingsCount();

        interface FilerKey {
            String getCik();
            String getFilerName();
        }
    }

    /**
     * Projection for holding summary aggregation.
     */
    interface HoldingSummary {
        String getId(); // CUSIP
        String getIssuerName();
        Long getTotalShares();
        Long getTotalValue();
        Integer getFilerCount();
    }

    /**
     * Projection for portfolio snapshot over time.
     */
    interface PortfolioSnapshot {
        LocalDate getReportPeriod();
        Long getTotalValue();
        Integer getHoldingsCount();
    }
}
