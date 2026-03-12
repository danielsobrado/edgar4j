package org.jds.edgar4j.repository.insider;

import org.jds.edgar4j.model.insider.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TransactionType entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, Long> {

    /**
     * Find transaction type by code
     */
    Optional<TransactionType> findByTransactionCode(String transactionCode);

    /**
     * Find active transaction types
     */
    List<TransactionType> findByIsActiveTrueOrderBySortOrder();

    /**
     * Find transaction types by category
     */
    List<TransactionType> findByTransactionCategory(TransactionType.TransactionCategory category);

    /**
     * Find transaction types by name
     */
    List<TransactionType> findByTransactionNameContainingIgnoreCase(String name);

    /**
     * Check if transaction type exists by code
     */
    boolean existsByTransactionCode(String transactionCode);

    /**
     * Count active transaction types
     */
    @Query("SELECT COUNT(t) FROM TransactionType t WHERE t.isActive = true")
    Long countActiveTransactionTypes();

    /**
     * Find purchase-related transaction types
     */
    @Query("SELECT t FROM TransactionType t WHERE t.transactionCategory IN ('PURCHASE', 'GRANT') AND t.isActive = true")
    List<TransactionType> findPurchaseTransactionTypes();

    /**
     * Find sale-related transaction types
     */
    @Query("SELECT t FROM TransactionType t WHERE t.transactionCategory = 'SALE' AND t.isActive = true")
    List<TransactionType> findSaleTransactionTypes();

    /**
     * Find derivative-related transaction types
     */
    @Query("SELECT t FROM TransactionType t WHERE t.transactionCategory = 'EXERCISE' AND t.isActive = true")
    List<TransactionType> findDerivativeTransactionTypes();
}
