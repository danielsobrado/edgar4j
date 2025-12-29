package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form6K;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 6-K filings.
 */
public interface Form6KService {

    /**
     * Downloads a Form 6-K primary document from SEC EDGAR.
     */
    CompletableFuture<String> downloadForm6K(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parses Form 6-K primary document content into a domain model.
     */
    Form6K parse(String rawDocument, String accessionNumber);

    /**
     * Downloads and parses a complete Form 6-K filing (primary document).
     */
    CompletableFuture<Form6K> downloadAndParse(String cik, String accessionNumber, String primaryDocument);

    /**
     * Saves or updates a Form 6-K filing.
     */
    Form6K save(Form6K form6K);

    /**
     * Saves multiple Form 6-K filings.
     */
    List<Form6K> saveAll(List<Form6K> form6KList);

    Optional<Form6K> findByAccessionNumber(String accessionNumber);

    Optional<Form6K> findById(String id);

    Page<Form6K> findByCik(String cik, Pageable pageable);

    Page<Form6K> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form6K> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form6K> findRecentFilings(int limit);

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteById(String id);
}

