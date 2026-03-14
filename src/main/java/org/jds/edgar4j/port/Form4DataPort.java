package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Form4DataPort extends BaseDocumentDataPort<Form4> {

    Optional<Form4> findByAccessionNumber(String accessionNumber);

    List<Form4> findByAccessionNumberIn(List<String> accessionNumbers);

    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<Form4> findByCik(String cik, Pageable pageable);

    List<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName);

    Page<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Form4> findBySymbolAndDateRange(String tradingSymbol, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form4> findByTradingSymbolAndTransactionDateBetween(String tradingSymbol, LocalDate startDate, LocalDate endDate);

    List<Form4> findRecentAcquisitions(LocalDate since);

    boolean existsByAccessionNumber(String accessionNumber);
}
