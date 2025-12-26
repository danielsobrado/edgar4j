package org.jds.edgar4j.repository;

import java.util.Date;
import java.util.List;

import org.jds.edgar4j.entity.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface Form4Repository extends MongoRepository<Form4, String> {

    List<Form4> findByTradingSymbol(String tradingSymbol);

    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    List<Form4> findByRptOwnerName(String ownerName);

    List<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName);

    List<Form4> findByTransactionDateBetween(Date startDate, Date endDate);

    Page<Form4> findByTransactionDateBetween(Date startDate, Date endDate, Pageable pageable);

    List<Form4> findByBoughtSold(String boughtSold);

    List<Form4> findByIsDirectorTrue();

    List<Form4> findByIsOfficerTrue();

    List<Form4> findByIsTenOwnerTrue();

    @Query("{ 'tradingSymbol': ?0, 'transactionDate': { $gte: ?1, $lte: ?2 } }")
    Page<Form4> findBySymbolAndDateRange(String tradingSymbol, Date startDate, Date endDate, Pageable pageable);

    @Query("{ 'owner': ?0 }")
    List<Form4> findByOwnerType(String ownerType);

    List<Form4> findTop10ByOrderByTransactionDateDesc();

    @Query(value = "{ 'boughtSold': 'B' }", count = true)
    long countBuys();

    @Query(value = "{ 'boughtSold': 'S' }", count = true)
    long countSells();
}
