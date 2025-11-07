package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jds.edgar4j.model.InsiderForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for SEC Insider Forms (Forms 3, 4, and 5)
 * Provides query methods for all three form types
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Repository
public interface InsiderFormRepository extends ElasticsearchRepository<InsiderForm, String> {

    // ========== Form Type Queries ==========

    /**
     * Find forms by type (3, 4, or 5)
     */
    Page<InsiderForm> findByFormType(String formType, Pageable pageable);

    /**
     * Find forms by type and ticker
     */
    Page<InsiderForm> findByFormTypeAndTradingSymbol(String formType, String ticker, Pageable pageable);

    /**
     * Find forms by type and CIK
     */
    Page<InsiderForm> findByFormTypeAndIssuerCik(String formType, String cik, Pageable pageable);

    /**
     * Find forms by type and date range
     */
    Page<InsiderForm> findByFormTypeAndFilingDateBetween(
        String formType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // ========== Ticker Queries ==========

    /**
     * Find by trading symbol (any form type)
     */
    Page<InsiderForm> findByTradingSymbol(String ticker, Pageable pageable);

    /**
     * Find by trading symbol and filing date range
     */
    Page<InsiderForm> findByTradingSymbolAndFilingDateBetween(
        String ticker, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find by trading symbol and period of report date range
     */
    Page<InsiderForm> findByTradingSymbolAndPeriodOfReportBetween(
        String ticker, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // ========== CIK Queries ==========

    /**
     * Find by issuer CIK
     */
    Page<InsiderForm> findByIssuerCik(String cik, Pageable pageable);

    /**
     * Find by issuer CIK and filing date range
     */
    Page<InsiderForm> findByIssuerCikAndFilingDateBetween(
        String cik, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // ========== Date Range Queries ==========

    /**
     * Find by filing date range (any form type)
     */
    Page<InsiderForm> findByFilingDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find by period of report date range (any form type)
     */
    Page<InsiderForm> findByPeriodOfReportBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // ========== Accession Number Queries ==========

    /**
     * Find by accession number
     */
    InsiderForm findByAccessionNumber(String accessionNumber);

    /**
     * Check if accession number exists
     */
    boolean existsByAccessionNumber(String accessionNumber);

    // ========== Count Queries ==========

    /**
     * Count by form type
     */
    long countByFormType(String formType);

    /**
     * Count by ticker and form type
     */
    long countByTradingSymbolAndFormType(String ticker, String formType);

    /**
     * Count by CIK and form type
     */
    long countByIssuerCikAndFormType(String cik, String formType);

    /**
     * Count by form type and date range
     */
    long countByFormTypeAndFilingDateBetween(String formType, LocalDateTime startDate, LocalDateTime endDate);
}
