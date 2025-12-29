package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form20F;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 20-F SEC filings.
 */
@Repository
public interface Form20FRepository extends MongoRepository<Form20F, String> {

    Optional<Form20F> findByAccessionNumber(String accessionNumber);

    Page<Form20F> findByCik(String cik, Pageable pageable);

    Page<Form20F> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    @Query("{ 'filedDate': { $gte: ?0, $lte: ?1 } }")
    Page<Form20F> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form20F> findTop20ByOrderByFiledDateDesc();

    boolean existsByAccessionNumber(String accessionNumber);

    void deleteByAccessionNumber(String accessionNumber);
}

