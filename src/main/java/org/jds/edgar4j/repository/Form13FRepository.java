package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jds.edgar4j.model.Form13F;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 13F institutional holdings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Repository
public interface Form13FRepository extends ElasticsearchRepository<Form13F, String> {

    /**
     * Find by accession number
     */
    Form13F findByAccessionNumber(String accessionNumber);

    /**
     * Check if accession number exists
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Find by filer CIK
     */
    Page<Form13F> findByFilerCik(String filerCik, Pageable pageable);

    /**
     * Find by filer name (contains)
     */
    Page<Form13F> findByFilerNameContaining(String name, Pageable pageable);

    /**
     * Find by period of report
     */
    Page<Form13F> findByPeriodOfReport(LocalDate periodOfReport, Pageable pageable);

    /**
     * Find by period of report range
     */
    Page<Form13F> findByPeriodOfReportBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find by filing date range
     */
    Page<Form13F> findByFilingDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find latest filings for a filer
     */
    Page<Form13F> findByFilerCikOrderByFilingDateDesc(String filerCik, Pageable pageable);

    /**
     * Find by filer CIK and period of report
     */
    Form13F findByFilerCikAndPeriodOfReport(String filerCik, LocalDate periodOfReport);

    /**
     * Find amendments
     */
    Page<Form13F> findByIsAmendmentTrue(Pageable pageable);

    /**
     * Find original filings (not amendments)
     */
    Page<Form13F> findByIsAmendmentFalse(Pageable pageable);

    /**
     * Find by report type
     */
    Page<Form13F> findByReportType(String reportType, Pageable pageable);

    /**
     * Count by filer CIK
     */
    long countByFilerCik(String filerCik);

    /**
     * Count by period of report
     */
    long countByPeriodOfReport(LocalDate periodOfReport);
}
