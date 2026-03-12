package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form20F;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 20-F filings.
 */
public interface Form20FService {

    CompletableFuture<String> downloadForm20F(String cik, String accessionNumber, String primaryDocument);

    Form20F parse(String rawDocument, String accessionNumber, String primaryDocument);

    CompletableFuture<Form20F> downloadAndParse(String cik, String accessionNumber, String primaryDocument);

    Form20F save(Form20F form20F);

    List<Form20F> saveAll(List<Form20F> form20FList);

    Optional<Form20F> findByAccessionNumber(String accessionNumber);

    Optional<Form20F> findById(String id);

    Page<Form20F> findByCik(String cik, Pageable pageable);

    Page<Form20F> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form20F> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form20F> findRecentFilings(int limit);

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteById(String id);
}

