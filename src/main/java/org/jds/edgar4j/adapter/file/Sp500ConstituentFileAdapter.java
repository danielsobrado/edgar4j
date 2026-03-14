package org.jds.edgar4j.adapter.file;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Sp500Constituent;
import org.jds.edgar4j.port.Sp500ConstituentDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Sp500ConstituentFileAdapter extends AbstractFileDataPort<Sp500Constituent>
        implements Sp500ConstituentDataPort {

    private static final String INDEX_TICKER = "ticker";

    public Sp500ConstituentFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "sp500_constituents",
                Sp500Constituent.class,
                FileFormat.JSON,
                Sp500Constituent::getId,
                Sp500Constituent::setId));
        registerIgnoreCaseIndex(INDEX_TICKER, Sp500Constituent::getTicker);
    }

    @Override
    public Optional<Sp500Constituent> findByTickerIgnoreCase(String ticker) {
        return findFirstByIndex(INDEX_TICKER, ticker);
    }

    @Override
    public boolean existsByTickerIgnoreCase(String ticker) {
        return existsByIndex(INDEX_TICKER, ticker);
    }

    @Override
    public List<Sp500Constituent> findAllByOrderByTickerAsc() {
        return findAll().stream()
                .sorted(Comparator.comparing(Sp500Constituent::getTicker, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
