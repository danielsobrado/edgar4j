package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form5;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 5 filings.
 */
public interface Form5Service {

    CompletableFuture<String> downloadForm5(String cik, String accessionNumber, String primaryDocument);

    Form5 parse(String xml, String accessionNumber);

    CompletableFuture<Form5> downloadAndParse(String cik, String accessionNumber, String primaryDocument);

    Form5 save(Form5 form5);

    List<Form5> saveAll(List<Form5> form5List);

    Optional<Form5> findByAccessionNumber(String accessionNumber);

    Optional<Form5> findById(String id);

    Page<Form5> findByCik(String cik, Pageable pageable);

    Page<Form5> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form5> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form5> findRecentFilings(int limit);

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteById(String id);
}
