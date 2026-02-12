package org.jds.edgar4j.repository.insider;

import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for InsiderTransaction entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Repository
public interface InsiderTransactionRepository extends JpaRepository<InsiderTransaction, Long> {

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
     * Find transactions by insider CIK
     */
    List<InsiderTransaction> findByInsiderCik(String cik);

    /**
     * Find transactions by insider CIK with pagination
     */
    Page<InsiderTransaction> findByInsiderCik(String cik, Pageable pageable);

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
    @Query("SELECT t FROM InsiderTransaction t WHERE t.transactionValue > :threshold")
    List<InsiderTransaction> findSignificantTransactions(@Param("threshold") BigDecimal threshold);

    /**
     * Find recent transactions
     */
    @Query("SELECT t FROM InsiderTransaction t WHERE t.transactionDate > :since ORDER BY t.transactionDate DESC")
    List<InsiderTransaction> findRecentTransactions(@Param("since") LocalDate since);

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
    @Query("SELECT COUNT(t) FROM InsiderTransaction t WHERE t.company.cik = :cik")
    Long countTransactionsByCompany(@Param("cik") String cik);

    /**
     * Count transactions by insider
     */
    @Query("SELECT COUNT(t) FROM InsiderTransaction t WHERE t.insider.cik = :cik")
    Long countTransactionsByInsider(@Param("cik") String cik);

    /**
     * Find transactions with high ownership percentage changes
     */
    @Query("SELECT t FROM InsiderTransaction t WHERE ABS(t.ownershipPercentageAfter - t.ownershipPercentageBefore) > :threshold")
    List<InsiderTransaction> findTransactionsWithHighOwnershipChange(@Param("threshold") BigDecimal threshold);

    /**
     * Find latest transactions by company
     */
    @Query("SELECT t FROM InsiderTransaction t WHERE t.company.cik = :cik ORDER BY t.transactionDate DESC")
    List<InsiderTransaction> findLatestTransactionsByCompany(@Param("cik") String cik, Pageable pageable);

    /**
     * Find transactions by security title
     */
    List<InsiderTransaction> findBySecurityTitleContainingIgnoreCase(String securityTitle);
}
