package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13DG;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 13D/13G SEC filings (beneficial ownership reports).
 */
@Repository
public interface Form13DGRepository extends MongoRepository<Form13DG, String> {

    // Find by accession number (unique identifier)
    Optional<Form13DG> findByAccessionNumber(String accessionNumber);

    // Find by form type (SC 13D, SC 13G, etc.)
    List<Form13DG> findByFormType(String formType);

    Page<Form13DG> findByFormType(String formType, Pageable pageable);

    // Find by schedule type (13D or 13G)
    List<Form13DG> findByScheduleType(String scheduleType);

    Page<Form13DG> findByScheduleType(String scheduleType, Pageable pageable);

    // Find by issuer CIK
    List<Form13DG> findByIssuerCik(String issuerCik);

    Page<Form13DG> findByIssuerCik(String issuerCik, Pageable pageable);

    // Find by issuer name
    List<Form13DG> findByIssuerNameContainingIgnoreCase(String issuerName);

    Page<Form13DG> findByIssuerNameContainingIgnoreCase(String issuerName, Pageable pageable);

    // Find by CUSIP
    List<Form13DG> findByCusip(String cusip);

    Page<Form13DG> findByCusip(String cusip, Pageable pageable);

    // Find by filing person CIK (beneficial owner)
    List<Form13DG> findByFilingPersonCik(String filingPersonCik);

    Page<Form13DG> findByFilingPersonCik(String filingPersonCik, Pageable pageable);

    // Find by filing person name
    List<Form13DG> findByFilingPersonNameContainingIgnoreCase(String filingPersonName);

    Page<Form13DG> findByFilingPersonNameContainingIgnoreCase(String filingPersonName, Pageable pageable);

    // Find by event date
    List<Form13DG> findByEventDate(LocalDate eventDate);

    // Find by event date range
    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 } }")
    List<Form13DG> findByEventDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form13DG> findByEventDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Find by filed date range
    List<Form13DG> findByFiledDateBetween(LocalDate startDate, LocalDate endDate);

    Page<Form13DG> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Find recent filings
    List<Form13DG> findTop20ByOrderByFiledDateDesc();

    List<Form13DG> findTop20ByOrderByEventDateDesc();

    List<Form13DG> findTop10ByScheduleTypeOrderByFiledDateDesc(String scheduleType);

    // Find by issuer CIK and schedule type
    @Query("{ 'issuerCik': ?0, 'scheduleType': ?1 }")
    List<Form13DG> findByIssuerCikAndScheduleType(String issuerCik, String scheduleType);

    @Query("{ 'issuerCik': ?0, 'scheduleType': ?1 }")
    Page<Form13DG> findByIssuerCikAndScheduleType(String issuerCik, String scheduleType, Pageable pageable);

    // Find by CUSIP and schedule type
    @Query("{ 'cusip': ?0, 'scheduleType': ?1 }")
    List<Form13DG> findByCusipAndScheduleType(String cusip, String scheduleType);

    // Find by filing person CIK with date range
    @Query("{ 'filingPersonCik': ?0, 'eventDate': { $gte: ?1, $lte: ?2 } }")
    List<Form13DG> findByFilingPersonCikAndEventDateBetween(String filingPersonCik, LocalDate startDate, LocalDate endDate);

    // Find by ownership threshold
    @Query("{ 'percentOfClass': { $gte: ?0 } }")
    List<Form13DG> findByMinPercentOfClass(Double minPercent);

    @Query("{ 'percentOfClass': { $gte: ?0 } }")
    Page<Form13DG> findByMinPercentOfClass(Double minPercent, Pageable pageable);

    // Find 10%+ owners
    @Query("{ 'percentOfClass': { $gte: 10.0 } }")
    List<Form13DG> findTenPercentOwners();

    @Query("{ 'percentOfClass': { $gte: 10.0 } }")
    Page<Form13DG> findTenPercentOwners(Pageable pageable);

    // Find by percentage range
    @Query("{ 'percentOfClass': { $gte: ?0, $lte: ?1 } }")
    Page<Form13DG> findByPercentOfClassBetween(Double minPercent, Double maxPercent, Pageable pageable);

    // Find by shares owned range
    @Query("{ 'sharesBeneficiallyOwned': { $gte: ?0 } }")
    List<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares);

    @Query("{ 'sharesBeneficiallyOwned': { $gte: ?0 } }")
    Page<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares, Pageable pageable);

    // Find amendments only
    @Query("{ 'amendmentType': 'AMENDMENT' }")
    List<Form13DG> findAmendments();

    @Query("{ 'amendmentType': 'AMENDMENT' }")
    Page<Form13DG> findAmendments(Pageable pageable);

    // Find initial filings only
    @Query("{ 'amendmentType': 'INITIAL' }")
    List<Form13DG> findInitialFilings();

    @Query("{ 'amendmentType': 'INITIAL' }")
    Page<Form13DG> findInitialFilings(Pageable pageable);

    // Find by reporting person type
    @Query("{ 'reportingPersonTypes': ?0 }")
    List<Form13DG> findByReportingPersonType(String type);

    @Query("{ 'reportingPersonTypes': { $in: ?0 } }")
    Page<Form13DG> findByReportingPersonTypeIn(List<String> types, Pageable pageable);

    // Find 13G filings by category
    @Query("{ 'scheduleType': '13G', 'filerCategory': ?0 }")
    List<Form13DG> findByFilerCategory(String filerCategory);

    // Count queries
    long countByScheduleType(String scheduleType);

    long countByIssuerCik(String issuerCik);

    long countByCusip(String cusip);

    long countByFilingPersonCik(String filingPersonCik);

    // Existence checks
    boolean existsByAccessionNumber(String accessionNumber);

    boolean existsByIssuerCikAndFilingPersonCik(String issuerCik, String filingPersonCik);

    // Delete
    void deleteByAccessionNumber(String accessionNumber);

    // Aggregation: Get top beneficial owners for a security
    @Aggregation(pipeline = {
        "{ $match: { 'cusip': ?0 } }",
        "{ $sort: { 'eventDate': -1 } }",
        "{ $group: { _id: '$filingPersonCik', filingPersonName: { $first: '$filingPersonName' }, percentOfClass: { $first: '$percentOfClass' }, sharesBeneficiallyOwned: { $first: '$sharesBeneficiallyOwned' }, latestEventDate: { $first: '$eventDate' }, scheduleType: { $first: '$scheduleType' } } }",
        "{ $sort: { percentOfClass: -1 } }",
        "{ $limit: ?1 }"
    })
    List<BeneficialOwnerSummary> getTopBeneficialOwnersByCusip(String cusip, int limit);

    // Aggregation: Get ownership history for a specific filer-issuer combination
    @Aggregation(pipeline = {
        "{ $match: { 'filingPersonCik': ?0, 'issuerCik': ?1 } }",
        "{ $sort: { 'eventDate': -1 } }",
        "{ $project: { eventDate: 1, percentOfClass: 1, sharesBeneficiallyOwned: 1, formType: 1, amendmentType: 1 } }"
    })
    List<OwnershipHistoryEntry> getOwnershipHistory(String filingPersonCik, String issuerCik);

    // Aggregation: Count filings by schedule type and date
    @Aggregation(pipeline = {
        "{ $match: { 'filedDate': { $gte: ?0, $lte: ?1 } } }",
        "{ $group: { _id: '$scheduleType', count: { $sum: 1 } } }"
    })
    List<ScheduleTypeCount> countByScheduleTypeInDateRange(LocalDate startDate, LocalDate endDate);

    // Aggregation: Get activist filings (13D with purpose indicating activism)
    @Aggregation(pipeline = {
        "{ $match: { 'scheduleType': '13D', 'purposeOfTransaction': { $ne: null } } }",
        "{ $sort: { 'eventDate': -1 } }",
        "{ $limit: ?0 }"
    })
    List<Form13DG> getRecentActivistFilings(int limit);

    // Aggregation: Get portfolio of beneficial owner (all their holdings)
    @Aggregation(pipeline = {
        "{ $match: { 'filingPersonCik': ?0 } }",
        "{ $sort: { 'eventDate': -1 } }",
        "{ $group: { _id: '$issuerCik', issuerName: { $first: '$issuerName' }, cusip: { $first: '$cusip' }, percentOfClass: { $first: '$percentOfClass' }, sharesBeneficiallyOwned: { $first: '$sharesBeneficiallyOwned' }, latestEventDate: { $first: '$eventDate' }, scheduleType: { $first: '$scheduleType' } } }"
    })
    List<OwnerPortfolioEntry> getOwnerPortfolio(String filingPersonCik);

    /**
     * Summary of a beneficial owner for a security.
     */
    interface BeneficialOwnerSummary {
        String getId(); // Filing person CIK
        String getFilingPersonName();
        Double getPercentOfClass();
        Long getSharesBeneficiallyOwned();
        LocalDate getLatestEventDate();
        String getScheduleType();
    }

    /**
     * Entry in ownership history timeline.
     */
    interface OwnershipHistoryEntry {
        LocalDate getEventDate();
        Double getPercentOfClass();
        Long getSharesBeneficiallyOwned();
        String getFormType();
        String getAmendmentType();
    }

    /**
     * Count of filings by schedule type.
     */
    interface ScheduleTypeCount {
        String getId(); // Schedule type (13D or 13G)
        Long getCount();
    }

    /**
     * Entry in beneficial owner's portfolio.
     */
    interface OwnerPortfolioEntry {
        String getId(); // Issuer CIK
        String getIssuerName();
        String getCusip();
        Double getPercentOfClass();
        Long getSharesBeneficiallyOwned();
        LocalDate getLatestEventDate();
        String getScheduleType();
    }
}
