package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form8K;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 8-K SEC filings.
 */
@Repository
public interface Form8KRepository extends MongoRepository<Form8K, String> {

    Optional<Form8K> findByAccessionNumber(String accessionNumber);

    Page<Form8K> findByCik(String cik, Pageable pageable);

    Page<Form8K> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    @Query("{ 'filedDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form8K> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form8K> findTop20ByOrderByFiledDateDesc();

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteByAccessionNumber(String accessionNumber);
}

