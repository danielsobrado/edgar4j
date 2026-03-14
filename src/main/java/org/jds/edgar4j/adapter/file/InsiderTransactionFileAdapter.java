package org.jds.edgar4j.adapter.file;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FilePageSupport;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class InsiderTransactionFileAdapter extends AbstractLongIdFileDataPort<InsiderTransaction>
        implements InsiderTransactionDataPort {

    private static final String INDEX_ACCESSION_NUMBER = "accessionNumber";
    private static final String INDEX_COMPANY_CIK = "companyCik";
    private static final String INDEX_INSIDER_CIK = "insiderCik";
    private static final String INDEX_TRANSACTION_CODE = "transactionCode";

    public InsiderTransactionFileAdapter(FileStorageEngine storageEngine) {
        super(
                storageEngine.registerCollection(
                        "insider_transactions",
                        InsiderTransaction.class,
                        FileFormat.JSON,
                        transaction -> transaction.getId() == null ? null : String.valueOf(transaction.getId()),
                        (transaction, id) -> transaction.setId(Long.parseLong(id))),
                InsiderTransaction::getId,
                InsiderTransaction::setId);
        registerIgnoreCaseIndex(INDEX_ACCESSION_NUMBER, InsiderTransaction::getAccessionNumber);
        registerExactIndex(INDEX_COMPANY_CIK, transaction -> companyCik(transaction));
        registerExactIndex(INDEX_INSIDER_CIK, transaction -> insiderCik(transaction));
        registerIgnoreCaseIndex(INDEX_TRANSACTION_CODE, InsiderTransaction::getTransactionCode);
    }

    @Override
    public Optional<InsiderTransaction> findByAccessionNumber(String accessionNumber) {
        return findFirstByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public List<InsiderTransaction> findByCompanyCik(String cik) {
        return findAllByIndex(INDEX_COMPANY_CIK, cik);
    }

    @Override
    public Page<InsiderTransaction> findByCompanyCik(String cik, Pageable pageable) {
        return page(findByCompanyCik(cik), pageable, null);
    }

    @Override
    public List<InsiderTransaction> findByCompanyCikAndTransactionDateBetween(String cik, LocalDate startDate, LocalDate endDate) {
        return findMatching(transaction -> cik.equals(companyCik(transaction))
                && isBetween(transaction.getTransactionDate(), startDate, endDate));
    }

    @Override
    public List<InsiderTransaction> findByInsiderCik(String cik) {
        return findAllByIndex(INDEX_INSIDER_CIK, cik);
    }

    @Override
    public Page<InsiderTransaction> findByInsiderCik(String cik, Pageable pageable) {
        return page(findByInsiderCik(cik), pageable, null);
    }

    @Override
    public List<InsiderTransaction> findByInsiderCikAndTransactionDateBetween(String cik, LocalDate startDate, LocalDate endDate) {
        return findMatching(transaction -> cik.equals(insiderCik(transaction))
                && isBetween(transaction.getTransactionDate(), startDate, endDate));
    }

    @Override
    public List<InsiderTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate) {
        return findMatching(transaction -> isBetween(transaction.getTransactionDate(), startDate, endDate));
    }

    @Override
    public List<InsiderTransaction> findByFilingDateBetween(LocalDate startDate, LocalDate endDate) {
        return findMatching(transaction -> isBetween(transaction.getFilingDate(), startDate, endDate));
    }

    @Override
    public List<InsiderTransaction> findByTransactionCode(String transactionCode) {
        return findAllByIndex(INDEX_TRANSACTION_CODE, transactionCode);
    }

    @Override
    public List<InsiderTransaction> findByAcquiredDisposed(InsiderTransaction.AcquiredDisposed acquiredDisposed) {
        return findMatching(transaction -> acquiredDisposed == transaction.getAcquiredDisposed());
    }

    @Override
    public List<InsiderTransaction> findSignificantTransactions(BigDecimal threshold) {
        return findMatching(transaction -> transaction.getTransactionValue() != null
                && threshold != null
                && transaction.getTransactionValue().compareTo(threshold) > 0);
    }

    @Override
    public List<InsiderTransaction> findRecentTransactions(LocalDate since) {
        return FilePageSupport.applySort(
                findMatching(transaction -> transaction.getTransactionDate() != null
                        && since != null
                        && transaction.getTransactionDate().isAfter(since)),
                Sort.by(Sort.Direction.DESC, "transactionDate"));
    }

    @Override
    public List<InsiderTransaction> findByCompanyCikAndInsiderCik(String companyCik, String insiderCik) {
        return findMatching(transaction -> companyCik.equals(companyCik(transaction))
                && insiderCik.equals(insiderCik(transaction)));
    }

    @Override
    public List<InsiderTransaction> findByIsDerivativeTrue() {
        return findMatching(transaction -> isTrue(transaction.getIsDerivative()));
    }

    @Override
    public List<InsiderTransaction> findByAcquiredDisposedAndSharesTransactedGreaterThan(
            InsiderTransaction.AcquiredDisposed acquiredDisposed,
            BigDecimal shares) {
        return findMatching(transaction -> acquiredDisposed == transaction.getAcquiredDisposed()
                && transaction.getSharesTransacted() != null
                && shares != null
                && transaction.getSharesTransacted().compareTo(shares) > 0);
    }

    @Override
    public List<InsiderTransaction> findByOwnershipNature(InsiderTransaction.OwnershipNature ownershipNature) {
        return findMatching(transaction -> ownershipNature == transaction.getOwnershipNature());
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return existsByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public Long countTransactionsByCompany(String cik) {
        return countByIndex(INDEX_COMPANY_CIK, cik);
    }

    @Override
    public Long countTransactionsByInsider(String cik) {
        return countByIndex(INDEX_INSIDER_CIK, cik);
    }

    @Override
    public List<InsiderTransaction> findTransactionsWithHighOwnershipChange(BigDecimal threshold) {
        return findMatching(transaction -> transaction.getOwnershipPercentageBefore() != null
                && transaction.getOwnershipPercentageAfter() != null
                && threshold != null
                && transaction.getOwnershipPercentageAfter()
                        .subtract(transaction.getOwnershipPercentageBefore())
                        .abs()
                        .compareTo(threshold) > 0);
    }

    @Override
    public List<InsiderTransaction> findLatestTransactionsByCompany(String cik, Pageable pageable) {
        return page(
                findByCompanyCik(cik),
                pageable,
                Sort.by(Sort.Direction.DESC, "transactionDate"))
                .getContent();
    }

    @Override
    public List<InsiderTransaction> findBySecurityTitleContainingIgnoreCase(String securityTitle) {
        return findMatching(transaction -> containsIgnoreCase(transaction.getSecurityTitle(), securityTitle));
    }

    private boolean isBetween(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null
                && (startDate == null || !value.isBefore(startDate))
                && (endDate == null || !value.isAfter(endDate));
    }

    private String companyCik(InsiderTransaction transaction) {
        return transaction.getCompany() != null ? transaction.getCompany().getCik() : null;
    }

    private String insiderCik(InsiderTransaction transaction) {
        return transaction.getInsider() != null ? transaction.getInsider().getCik() : null;
    }
}