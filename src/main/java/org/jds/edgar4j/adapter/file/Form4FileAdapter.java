package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form4FileAdapter extends AbstractFileDataPort<Form4> implements Form4DataPort {

    public Form4FileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form4",
                Form4.class,
                FileFormat.JSONL,
                Form4::getId,
                Form4::setId));
    }

    @Override
    public Optional<Form4> findByAccessionNumber(String accessionNumber) {
        return findFirst(value -> accessionNumber != null && accessionNumber.equals(value.getAccessionNumber()));
    }

    @Override
    public List<Form4> findByAccessionNumberIn(List<String> accessionNumbers) {
        if (accessionNumbers == null || accessionNumbers.isEmpty()) {
            return List.of();
        }
        return findMatching(value -> accessionNumbers.contains(value.getAccessionNumber()));
    }

    @Override
    public Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return findMatching(value -> equalsIgnoreCase(value.getTradingSymbol(), tradingSymbol), pageable);
    }

    @Override
    public Page<Form4> findByCik(String cik, Pageable pageable) {
        return findMatching(value -> cik != null && cik.equals(value.getCik()), pageable);
    }

    @Override
    public List<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName) {
        return findMatching(value -> containsIgnoreCase(value.getRptOwnerName(), ownerName));
    }

    @Override
    public Page<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> between(value.getTransactionDate(), startDate, endDate), pageable);
    }

    @Override
    public Page<Form4> findBySymbolAndDateRange(
            String tradingSymbol,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        return findMatching(value -> equalsIgnoreCase(value.getTradingSymbol(), tradingSymbol)
                && between(value.getTransactionDate(), startDate, endDate), pageable);
    }

    @Override
    public List<Form4> findByTradingSymbolAndTransactionDateBetween(
            String tradingSymbol,
            LocalDate startDate,
            LocalDate endDate) {
        return findMatching(value -> equalsIgnoreCase(value.getTradingSymbol(), tradingSymbol)
                && between(value.getTransactionDate(), startDate, endDate));
    }

    @Override
    public List<Form4> findRecentAcquisitions(LocalDate since) {
        return findMatching(value -> isRecentAcquisition(value, since));
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return exists(value -> accessionNumber != null && accessionNumber.equals(value.getAccessionNumber()));
    }

    private boolean between(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null && !value.isBefore(startDate) && !value.isAfter(endDate);
    }

    private boolean isRecentAcquisition(Form4 form4, LocalDate since) {
        if (form4 == null || since == null) {
            return false;
        }
        if ("A".equalsIgnoreCase(form4.getAcquiredDisposedCode()) && betweenAfter(form4.getTransactionDate(), since)) {
            return true;
        }
        return form4.getTransactions() != null && form4.getTransactions().stream()
                .filter(Objects::nonNull)
                .anyMatch(transaction -> isRecentPurchase(transaction, since));
    }

    private boolean isRecentPurchase(Form4Transaction transaction, LocalDate since) {
        return transaction != null
                && "A".equalsIgnoreCase(transaction.getAcquiredDisposedCode())
                && "P".equalsIgnoreCase(transaction.getTransactionCode())
                && betweenAfter(transaction.getTransactionDate(), since);
    }

    private boolean betweenAfter(LocalDate value, LocalDate since) {
        return value != null && !value.isBefore(since);
    }
}
