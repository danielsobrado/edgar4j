package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jds.edgar4j.model.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch repository for Form 4 insider trading filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Repository
public interface Form4Repository extends ElasticsearchRepository<Form4, String> {

    /**
     * Find all Form 4s for a specific ticker symbol
     * @param tradingSymbol ticker symbol
     * @param pageable pagination parameters
     * @return page of Form 4 filings
     */
    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    /**
     * Find all Form 4s filed within a date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param pageable pagination parameters
     * @return page of Form 4 filings
     */
    Page<Form4> findByFilingDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find all Form 4s for a specific ticker filed within a date range
     * @param tradingSymbol ticker symbol
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param pageable pagination parameters
     * @return page of Form 4 filings
     */
    Page<Form4> findByTradingSymbolAndFilingDateBetween(
        String tradingSymbol,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find all Form 4s for a specific issuer CIK
     * @param issuerCik issuer CIK
     * @param pageable pagination parameters
     * @return page of Form 4 filings
     */
    Page<Form4> findByIssuerCik(String issuerCik, Pageable pageable);

    /**
     * Find all Form 4s by transaction date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param pageable pagination parameters
     * @return page of Form 4 filings
     */
    Page<Form4> findByPeriodOfReportBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find Form 4 by SEC accession number
     * @param accessionNumber SEC accession number
     * @return Form 4 filing or null if not found
     */
    Form4 findByAccessionNumber(String accessionNumber);

    /**
     * Find recent Form 4 filings ordered by filing date (most recent first)
     * @param pageable pagination parameters
     * @return page of recent Form 4 filings
     */
    Page<Form4> findAllByOrderByFilingDateDesc(Pageable pageable);

    /**
     * Find recent Form 4 filings for a ticker ordered by filing date
     * @param tradingSymbol ticker symbol
     * @param pageable pagination parameters
     * @return page of recent Form 4 filings for the ticker
     */
    Page<Form4> findByTradingSymbolOrderByFilingDateDesc(String tradingSymbol, Pageable pageable);

    /**
     * Find all Form 4s filed after a specific date
     * @param filingDate filing date threshold
     * @return list of Form 4 filings
     */
    List<Form4> findByFilingDateAfter(LocalDateTime filingDate);

    /**
     * Find all Form 4s with transactions after a specific date
     * @param transactionDate transaction date threshold
     * @return list of Form 4 filings
     */
    List<Form4> findByPeriodOfReportAfter(LocalDate transactionDate);
}
