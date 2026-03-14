package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.TransactionType;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TransactionTypeDataPort extends BaseInsiderDataPort<TransactionType> {

    Optional<TransactionType> findByTransactionCode(String transactionCode);

    List<TransactionType> findByIsActiveTrueOrderBySortOrder();

    List<TransactionType> findByTransactionCategory(TransactionType.TransactionCategory category);

    List<TransactionType> findByTransactionNameContainingIgnoreCase(String name);

    boolean existsByTransactionCode(String transactionCode);

    Long countActiveTransactionTypes();

    List<TransactionType> findPurchaseTransactionTypes();

    List<TransactionType> findSaleTransactionTypes();

    List<TransactionType> findDerivativeTransactionTypes();
}
