package org.jds.edgar4j.repository.insider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for InsiderTransaction entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Profile("resource-high")
public interface InsiderTransactionRepository extends MongoRepository<InsiderTransaction, Long> {

    /**
     * Find transaction by accession number
     */
    Optional<InsiderTransaction> findByAccessionNumber(String accessionNumber);

    /**
     * Find transactions by company CIK
     */
    List<InsiderTransaction> findByCompanyCik(String cik);

    /**
     * Find transactions by company CIK with pagination
     */
    Page<InsiderTransaction> findByCompanyCik(String cik, Pageable pageable);

    /**
     * Find transactions by company CIK and transaction date range
     */
    List<InsiderTransaction> findByCompanyCikAndTransactionDateBetween(
        String cik, LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by insider CIK
     */
    List<InsiderTransaction> findByInsiderCik(String cik);

    /**
     * Find transactions by insider CIK with pagination
     */
    Page<InsiderTransaction> findByInsiderCik(String cik, Pageable pageable);

    /**
     * Find transactions by insider CIK and transaction date range
     */
    List<InsiderTransaction> findByInsiderCikAndTransactionDateBetween(
        String cik, LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by date range
     */
    List<InsiderTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by filing date range
     */
    List<InsiderTransaction> findByFilingDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by transaction code
     */
    List<InsiderTransaction> findByTransactionCode(String transactionCode);

    /**
     * Find transactions by acquired/disposed
     */
    List<InsiderTransaction> findByAcquiredDisposed(InsiderTransaction.AcquiredDisposed acquiredDisposed);

    /**
     * Find significant transactions (over $1M)
     */
    @Query("{ 'transactionValue': { $gt: ?0 } }")
    List<InsiderTransaction> findSignificantTransactions(BigDecimal threshold);

    /**
     * Find recent transactions
     */
    List<InsiderTransaction> findByTransactionDateAfterOrderByTransactionDateDesc(LocalDate since);

    /**
     * Find transactions for company and insider
     */
    List<InsiderTransaction> findByCompanyCikAndInsiderCik(String companyCik, String insiderCik);

    /**
     * Find derivative transactions
     */
    List<InsiderTransaction> findByIsDerivativeTrue();

    /**
     * Find purchase transactions
     */
    List<InsiderTransaction> findByAcquiredDisposedAndSharesTransactedGreaterThan(
        InsiderTransaction.AcquiredDisposed acquiredDisposed, BigDecimal shares);

    /**
     * Find transactions by ownership nature
     */
    List<InsiderTransaction> findByOwnershipNature(InsiderTransaction.OwnershipNature ownershipNature);

    /**
     * Check if transaction exists by accession number
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Count transactions by company
     */
    @Query(value = "{ 'company.cik': ?0 }", count = true)
    Long countTransactionsByCompany(String cik);

    /**
     * Count transactions by insider
     */
    @Query(value = "{ 'insider.cik': ?0 }", count = true)
    Long countTransactionsByInsider(String cik);

    /**
     * Find transactions with high ownership percentage changes
     */
    @Query("{ $expr: { $gt: [ { $abs: { $subtract: ['$ownershipPercentageAfter', '$ownershipPercentageBefore'] } }, ?0 ] } }")
    List<InsiderTransaction> findTransactionsWithHighOwnershipChange(BigDecimal threshold);

    /**
     * Find latest transactions by company
     */
    Page<InsiderTransaction> findByCompanyCikOrderByTransactionDateDesc(String cik, Pageable pageable);

    /**
     * Find transactions by security title
     */
    List<InsiderTransaction> findBySecurityTitleContainingIgnoreCase(String securityTitle);

    default List<InsiderTransaction> findRecentTransactions(LocalDate since) {
        return findByTransactionDateAfterOrderByTransactionDateDesc(since);
    }

    default List<InsiderTransaction> findLatestTransactionsByCompany(String cik, Pageable pageable) {
        return findByCompanyCikOrderByTransactionDateDesc(cik, pageable).getContent();
    }
}
