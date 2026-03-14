package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.port.CompanyMarketDataDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class CompanyMarketDataFileAdapter extends AbstractFileDataPort<CompanyMarketData>
        implements CompanyMarketDataDataPort {

    private static final String INDEX_TICKER = "ticker";

    public CompanyMarketDataFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "company_market_data",
                CompanyMarketData.class,
                FileFormat.JSON,
                CompanyMarketData::getId,
                CompanyMarketData::setId));
        registerIgnoreCaseIndex(INDEX_TICKER, CompanyMarketData::getTicker);
    }

    @Override
    public Optional<CompanyMarketData> findByTickerIgnoreCase(String ticker) {
        return findFirstByIndex(INDEX_TICKER, ticker);
    }

    @Override
    public List<CompanyMarketData> findByTickerIn(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, CompanyMarketData> matches = new java.util.LinkedHashMap<>();
        for (String ticker : tickers) {
            findAllByIndex(INDEX_TICKER, ticker).forEach(value -> matches.putIfAbsent(value.getId(), value));
        }
        return List.copyOf(matches.values());
    }
}
