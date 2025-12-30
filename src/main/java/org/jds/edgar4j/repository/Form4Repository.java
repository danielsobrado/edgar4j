package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 4 SEC filings.
 */
@Repository
public interface Form4Repository extends MongoRepository<Form4, String> {

    // Find by accession number (unique identifier)
    Optional<Form4> findByAccessionNumber(String accessionNumber);

    // Find by trading symbol
    List<Form4> findByTradingSymbol(String tradingSymbol);

    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    // Find by CIK
    List<Form4> findByCik(String cik);

    Page<Form4> findByCik(String cik, Pageable pageable);

    // Find by owner name
    List<Form4> findByRptOwnerName(String ownerName);

    List<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName);

    Page<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName, Pageable pageable);

    // Find by owner CIK
    List<Form4> findByRptOwnerCik(String rptOwnerCik);

    // Find by date range
    List<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    Page<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Find by symbol and date range
    @Query("{ 'tradingSymbol': ?0, 'transactionDate': { $gte: ?1, $lte: ?2 } }")
    Page<Form4> findBySymbolAndDateRange(String tradingSymbol, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("{ 'tradingSymbol': ?0, 'transactionDate': { $gte: ?1, $lte: ?2 } }")
    List<Form4> findByTradingSymbolAndTransactionDateBetween(String tradingSymbol, LocalDate startDate, LocalDate endDate);

    // Find by owner type
    List<Form4> findByIsDirectorTrue();

    List<Form4> findByIsOfficerTrue();

    List<Form4> findByIsTenPercentOwnerTrue();

    Page<Form4> findByIsDirectorTrue(Pageable pageable);

    Page<Form4> findByIsOfficerTrue(Pageable pageable);

    Page<Form4> findByIsTenPercentOwnerTrue(Pageable pageable);

    // Find by acquired/disposed code
    @Query("{ 'acquiredDisposedCode': ?0 }")
    List<Form4> findByAcquiredDisposedCode(String code);

    @Query("{ 'acquiredDisposedCode': ?0 }")
    Page<Form4> findByAcquiredDisposedCode(String code, Pageable pageable);

    // Find recent filings
    List<Form4> findTop10ByOrderByTransactionDateDesc();

    List<Form4> findTop10ByTradingSymbolOrderByTransactionDateDesc(String tradingSymbol);

    // Count queries
    @Query(value = "{ 'acquiredDisposedCode': 'A' }", count = true)
    long countBuys();

    @Query(value = "{ 'acquiredDisposedCode': 'D' }", count = true)
    long countSells();

    @Query(value = "{ 'tradingSymbol': ?0, 'acquiredDisposedCode': 'A' }", count = true)
    long countBuysBySymbol(String tradingSymbol);

    @Query(value = "{ 'tradingSymbol': ?0, 'acquiredDisposedCode': 'D' }", count = true)
    long countSellsBySymbol(String tradingSymbol);

    // Existence check
    boolean existsByAccessionNumber(String accessionNumber);

    // Complex queries
    @Query("{ 'tradingSymbol': ?0, 'rptOwnerName': { $regex: ?1, $options: 'i' } }")
    List<Form4> findBySymbolAndOwnerName(String tradingSymbol, String ownerName);

    @Query("{ 'transactionValue': { $gte: ?0 } }")
    Page<Form4> findByMinTransactionValue(Float minValue, Pageable pageable);

    @Query("{ 'tradingSymbol': ?0, 'transactionValue': { $gte: ?1 } }")
    List<Form4> findLargeTransactionsBySymbol(String tradingSymbol, Float minValue);

    // Aggregation for statistics
    @Aggregation(pipeline = {
        "{ $match: { 'tradingSymbol': ?0, 'transactionDate': { $gte: ?1, $lte: ?2 } } }",
        "{ $group: { _id: '$acquiredDisposedCode', totalValue: { $sum: '$transactionValue' }, count: { $sum: 1 } } }"
    })
    List<TransactionSummary> getTransactionSummaryBySymbol(String tradingSymbol, LocalDate startDate, LocalDate endDate);

    /**
     * Projection for transaction summary aggregation.
     */
    interface TransactionSummary {
        String getId(); // A or D
        Double getTotalValue();
        Long getCount();
    }
}
