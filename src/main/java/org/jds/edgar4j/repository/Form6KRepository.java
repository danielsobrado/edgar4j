package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form6K;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 6-K SEC filings.
 */
@Repository
public interface Form6KRepository extends MongoRepository<Form6K, String> {

    Optional<Form6K> findByAccessionNumber(String accessionNumber);

    Page<Form6K> findByCik(String cik, Pageable pageable);

    Page<Form6K> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    @Query("{ 'filedDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form6K> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form6K> findTop20ByOrderByFiledDateDesc();

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteByAccessionNumber(String accessionNumber);
}

