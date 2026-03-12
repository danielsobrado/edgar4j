package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form8K;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 8-K filings.
 */
public interface Form8KService {

    /**
     * Downloads a Form 8-K primary document from SEC EDGAR.
     */
    CompletableFuture<String> downloadForm8K(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parses Form 8-K primary document content into a domain model.
     */
    Form8K parse(String rawDocument, String accessionNumber);

    /**
     * Downloads and parses a complete Form 8-K filing (primary document).
     */
    CompletableFuture<Form8K> downloadAndParse(String cik, String accessionNumber, String primaryDocument);

    /**
     * Saves or updates a Form 8-K filing.
     */
    Form8K save(Form8K form8K);

    /**
     * Saves multiple Form 8-K filings.
     */
    List<Form8K> saveAll(List<Form8K> form8KList);

    Optional<Form8K> findByAccessionNumber(String accessionNumber);

    Optional<Form8K> findById(String id);

    Page<Form8K> findByCik(String cik, Pageable pageable);

    Page<Form8K> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form8K> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form8K> findRecentFilings(int limit);

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteById(String id);
}

