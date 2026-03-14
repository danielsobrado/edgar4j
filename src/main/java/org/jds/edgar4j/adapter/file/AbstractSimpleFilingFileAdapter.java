package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.Optional;

import org.jds.edgar4j.port.SimpleAccessionedFilingDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public abstract class AbstractSimpleFilingFileAdapter<T> extends AbstractFileDataPort<T>
        implements SimpleAccessionedFilingDataPort<T> {

    protected AbstractSimpleFilingFileAdapter(FileCollection<T> collection) {
        super(collection);
    }

    @Override
    public Optional<T> findByAccessionNumber(String accessionNumber) {
        return findFirst(value -> equalsSafe(accessionNumber, getAccessionNumber(value)));
    }

    @Override
    public Page<T> findByCik(String cik, Pageable pageable) {
        return findMatching(value -> equalsSafe(cik, getCik(value)), pageable);
    }

    @Override
    public Page<T> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return findMatching(value -> equalsIgnoreCase(getTradingSymbol(value), tradingSymbol), pageable);
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
        return exists(value -> equalsSafe(accessionNumber, getAccessionNumber(value)));
    }

    protected boolean equalsSafe(String left, String right) {
        return left != null && left.equals(right);
    }

    protected abstract String getAccessionNumber(T value);

    protected abstract String getCik(T value);

    protected abstract String getTradingSymbol(T value);

    protected abstract LocalDate getFiledDate(T value);
}
