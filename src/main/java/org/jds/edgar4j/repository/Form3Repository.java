package org.jds.edgar4j.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form3;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 3 SEC filings.
 */
@Repository
public interface Form3Repository extends MongoRepository<Form3, String> {

    Optional<Form3> findByAccessionNumber(String accessionNumber);

    Page<Form3> findByCik(String cik, Pageable pageable);

    Page<Form3> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    @Query("{ 'filedDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form3> findByFiledDateBetween(Date startDate, Date endDate, Pageable pageable);

    List<Form3> findTop20ByOrderByFiledDateDesc();

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteByAccessionNumber(String accessionNumber);
}

