package org.jds.edgar4j.repository.insider;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.TransactionType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for TransactionType entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Profile("resource-high & !resource-low")
public interface TransactionTypeRepository extends MongoRepository<TransactionType, Long> {

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
    @Query(value = "{ 'isActive': true }", count = true)
    Long countActiveTransactionTypes();

    /**
     * Find purchase-related transaction types
     */
    @Query("{ 'isActive': true, 'transactionCategory': { $in: ['PURCHASE', 'GRANT'] } }")
    List<TransactionType> findPurchaseTransactionTypes();

    /**
     * Find sale-related transaction types
     */
    @Query("{ 'isActive': true, 'transactionCategory': 'SALE' }")
    List<TransactionType> findSaleTransactionTypes();

    /**
     * Find derivative-related transaction types
     */
    @Query("{ 'isActive': true, 'transactionCategory': 'EXERCISE' }")
    List<TransactionType> findDerivativeTransactionTypes();
}
