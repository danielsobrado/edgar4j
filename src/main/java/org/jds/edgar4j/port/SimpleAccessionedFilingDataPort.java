package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SimpleAccessionedFilingDataPort<T> extends BaseDocumentDataPort<T> {

    Optional<T> findByAccessionNumber(String accessionNumber);

    Page<T> findByCik(String cik, Pageable pageable);

    Page<T> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    Page<T> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    boolean existsByAccessionNumber(String accessionNumber);
}
