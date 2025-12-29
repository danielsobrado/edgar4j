package org.jds.edgar4j.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form3;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for downloading, parsing, and persisting SEC Form 3 filings.
 */
public interface Form3Service {

    CompletableFuture<String> downloadForm3(String cik, String accessionNumber, String primaryDocument);

    Form3 parse(String xml, String accessionNumber);

    CompletableFuture<Form3> downloadAndParse(String cik, String accessionNumber, String primaryDocument);

    Form3 save(Form3 form3);

    List<Form3> saveAll(List<Form3> form3List);

    Optional<Form3> findByAccessionNumber(String accessionNumber);

    Optional<Form3> findById(String id);

    Page<Form3> findByCik(String cik, Pageable pageable);

    Page<Form3> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form3> findByFiledDateRange(Date startDate, Date endDate, Pageable pageable);

    List<Form3> findRecentFilings(int limit);

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteById(String id);
}

