package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 4 filings.
 */
public interface Form4Service {

    /**
     * Downloads Form 4 XML from SEC EDGAR.
     */
    CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parses Form 4 XML content into domain model.
     */
    Form4 parseForm4(String xml, String accessionNumber);

    /**
     * Downloads and parses a Form 4 filing.
     */
    CompletableFuture<Form4> downloadAndParseForm4(String cik, String accessionNumber, String primaryDocument);

    /**
     * Saves or updates a Form 4 filing.
     */
    Form4 save(Form4 form4);

    /**
     * Saves multiple Form 4 filings.
     */
    List<Form4> saveAll(List<Form4> form4List);

    /**
     * Finds Form 4 by accession number.
     */
    Optional<Form4> findByAccessionNumber(String accessionNumber);

    /**
     * Finds Form 4 by ID.
     */
    Optional<Form4> findById(String id);

    /**
     * Finds all Form 4 filings for a trading symbol.
     */
    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    /**
     * Finds all Form 4 filings for a CIK.
     */
    Page<Form4> findByCik(String cik, Pageable pageable);

    /**
     * Finds Form 4 filings by owner name.
     */
    List<Form4> findByOwnerName(String ownerName);

    /**
     * Finds Form 4 filings within a date range.
     */
    Page<Form4> findByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Finds Form 4 filings by symbol and date range.
     */
    Page<Form4> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Finds recent Form 4 filings.
     */
    List<Form4> findRecentFilings(int limit);

    /**
     * Checks if a Form 4 filing exists.
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Deletes a Form 4 filing.
     */
    void deleteById(String id);

    /**
     * Gets insider transaction statistics for a symbol.
     */
    InsiderStats getInsiderStats(String tradingSymbol, LocalDate startDate, LocalDate endDate);

    /**
     * Insider trading statistics.
     */
    record InsiderStats(
        long totalBuys,
        long totalSells,
        double totalBuyValue,
        double totalSellValue,
        long directorTransactions,
        long officerTransactions,
        long tenPercentOwnerTransactions
    ) {}
}
