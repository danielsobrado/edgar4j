package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.port.TransactionTypeDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FilePageSupport;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class TransactionTypeFileAdapter extends AbstractLongIdFileDataPort<TransactionType> implements TransactionTypeDataPort {

    private static final String INDEX_TRANSACTION_CODE = "transactionCode";

    public TransactionTypeFileAdapter(FileStorageEngine storageEngine) {
        super(
                storageEngine.registerCollection(
                        "transaction_types",
                        TransactionType.class,
                        FileFormat.JSON,
                        transactionType -> transactionType.getId() == null ? null : String.valueOf(transactionType.getId()),
                        (transactionType, id) -> transactionType.setId(Long.parseLong(id))),
                TransactionType::getId,
                TransactionType::setId);
        registerIgnoreCaseIndex(INDEX_TRANSACTION_CODE, TransactionType::getTransactionCode);
    }

    @Override
    public Optional<TransactionType> findByTransactionCode(String transactionCode) {
        return findFirstByIndex(INDEX_TRANSACTION_CODE, transactionCode);
    }

    @Override
    public List<TransactionType> findByIsActiveTrueOrderBySortOrder() {
        return FilePageSupport.applySort(
                findMatching(transactionType -> isTrue(transactionType.getIsActive())),
                Sort.by(Sort.Direction.ASC, "sortOrder"));
    }

    @Override
    public List<TransactionType> findByTransactionCategory(TransactionType.TransactionCategory category) {
        return findMatching(transactionType -> category == transactionType.getTransactionCategory());
    }

    @Override
    public List<TransactionType> findByTransactionNameContainingIgnoreCase(String name) {
        return findMatching(transactionType -> containsIgnoreCase(transactionType.getTransactionName(), name));
    }

    @Override
    public boolean existsByTransactionCode(String transactionCode) {
        return existsByIndex(INDEX_TRANSACTION_CODE, transactionCode);
    }

    @Override
    public Long countActiveTransactionTypes() {
        return count(transactionType -> isTrue(transactionType.getIsActive()));
    }

    @Override
    public List<TransactionType> findPurchaseTransactionTypes() {
        return findMatching(transactionType -> isTrue(transactionType.getIsActive())
                && (transactionType.getTransactionCategory() == TransactionType.TransactionCategory.PURCHASE
                        || transactionType.getTransactionCategory() == TransactionType.TransactionCategory.GRANT));
    }

    @Override
    public List<TransactionType> findSaleTransactionTypes() {
        return findMatching(transactionType -> isTrue(transactionType.getIsActive())
                && transactionType.getTransactionCategory() == TransactionType.TransactionCategory.SALE);
    }

    @Override
    public List<TransactionType> findDerivativeTransactionTypes() {
        return findMatching(transactionType -> isTrue(transactionType.getIsActive())
                && transactionType.getTransactionCategory() == TransactionType.TransactionCategory.EXERCISE);
    }
}