package org.jds.edgar4j.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form5;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 5 SEC filings.
 */
@Repository
public interface Form5Repository extends MongoRepository<Form5, String> {

    Optional<Form5> findByAccessionNumber(String accessionNumber);

    Page<Form5> findByCik(String cik, Pageable pageable);

    Page<Form5> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    @Query("{ 'filedDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form5> findByFiledDateBetween(Date startDate, Date endDate, Pageable pageable);

    List<Form5> findTop20ByOrderByFiledDateDesc();

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteByAccessionNumber(String accessionNumber);
}

