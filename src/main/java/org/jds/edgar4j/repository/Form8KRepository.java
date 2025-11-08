package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jds.edgar4j.model.Form8K;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 8-K current reports
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Repository
public interface Form8KRepository extends ElasticsearchRepository<Form8K, String> {

    /**
     * Find by accession number
     */
    Form8K findByAccessionNumber(String accessionNumber);

    /**
     * Check if accession number exists
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Find by company CIK
     */
    Page<Form8K> findByCompanyCik(String companyCik, Pageable pageable);

    /**
     * Find by trading symbol
     */
    Page<Form8K> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    /**
     * Find by company name (contains)
     */
    Page<Form8K> findByCompanyNameContaining(String name, Pageable pageable);

    /**
     * Find by event date
     */
    Page<Form8K> findByEventDate(LocalDate eventDate, Pageable pageable);

    /**
     * Find by event date range
     */
    Page<Form8K> findByEventDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find by filing date range
     */
    Page<Form8K> findByFilingDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find by company and event date range
     */
    Page<Form8K> findByCompanyCikAndEventDateBetween(String companyCik, LocalDate startDate,
                                                      LocalDate endDate, Pageable pageable);

    /**
     * Find by trading symbol and event date range
     */
    Page<Form8K> findByTradingSymbolAndEventDateBetween(String tradingSymbol, LocalDate startDate,
                                                         LocalDate endDate, Pageable pageable);

    /**
     * Find by specific item number (contains in items list)
     */
    Page<Form8K> findByItemsContaining(String itemNumber, Pageable pageable);

    /**
     * Find by item number and date range
     */
    Page<Form8K> findByItemsContainingAndEventDateBetween(String itemNumber, LocalDate startDate,
                                                           LocalDate endDate, Pageable pageable);

    /**
     * Find by industry
     */
    Page<Form8K> findByIndustry(String industry, Pageable pageable);

    /**
     * Find amendments
     */
    Page<Form8K> findByIsAmendmentTrue(Pageable pageable);

    /**
     * Find original filings (not amendments)
     */
    Page<Form8K> findByIsAmendmentFalse(Pageable pageable);

    /**
     * Find latest filings for a company
     */
    Page<Form8K> findByCompanyCikOrderByFilingDateDesc(String companyCik, Pageable pageable);

    /**
     * Find latest filings by trading symbol
     */
    Page<Form8K> findByTradingSymbolOrderByFilingDateDesc(String tradingSymbol, Pageable pageable);

    /**
     * Count by company CIK
     */
    long countByCompanyCik(String companyCik);

    /**
     * Count by trading symbol
     */
    long countByTradingSymbol(String tradingSymbol);

    /**
     * Count by item number
     */
    long countByItemsContaining(String itemNumber);

    /**
     * Count by event date range
     */
    long countByEventDateBetween(LocalDate startDate, LocalDate endDate);
}
