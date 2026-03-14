package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.Optional;

import org.jds.edgar4j.port.SimpleAccessionedFilingDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.jds.edgar4j.storage.file.FilePageSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public abstract class AbstractSimpleFilingFileAdapter<T> extends AbstractFileDataPort<T>
        implements SimpleAccessionedFilingDataPort<T> {

    private static final String INDEX_ACCESSION_NUMBER = "accessionNumber";
    private static final String INDEX_CIK = "cik";
    private static final String INDEX_TRADING_SYMBOL = "tradingSymbol";

    protected AbstractSimpleFilingFileAdapter(FileCollection<T> collection) {
        super(collection);
    }

    protected void registerCommonFilingIndexes() {
        registerExactIndex(INDEX_ACCESSION_NUMBER, this::getAccessionNumber);
        registerExactIndex(INDEX_CIK, this::getCik);
        registerIgnoreCaseIndex(INDEX_TRADING_SYMBOL, this::getTradingSymbol);
    }

    @Override
    public Optional<T> findByAccessionNumber(String accessionNumber) {
        return findFirstByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public Page<T> findByCik(String cik, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_CIK, cik), pageable);
    }

    @Override
    public Page<T> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_TRADING_SYMBOL, tradingSymbol), pageable);
    }

    @Override
    public Page<T> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> {
            LocalDate filedDate = getFiledDate(value);
            return filedDate != null && !filedDate.isBefore(startDate) && !filedDate.isAfter(endDate);
        }, pageable);
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return existsByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    protected boolean equalsSafe(String left, String right) {
        return left != null && left.equals(right);
    }

    protected abstract String getAccessionNumber(T value);

    protected abstract String getCik(T value);

    protected abstract String getTradingSymbol(T value);

    protected abstract LocalDate getFiledDate(T value);
}
