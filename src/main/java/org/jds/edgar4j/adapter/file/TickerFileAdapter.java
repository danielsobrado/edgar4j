package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class TickerFileAdapter extends AbstractFileDataPort<Ticker> implements TickerDataPort {

    private static final String INDEX_CODE = "code";
    private static final String INDEX_CIK = "cik";

    public TickerFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "tickers",
                Ticker.class,
                FileFormat.JSON,
                Ticker::getId,
                Ticker::setId));
        registerIgnoreCaseIndex(INDEX_CODE, Ticker::getCode);
        registerExactIndex(INDEX_CIK, Ticker::getCik);
    }

    @Override
    public Optional<Ticker> findByCode(String code) {
        return findFirstByIndex(INDEX_CODE, code);
    }

    @Override
    public Optional<Ticker> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public List<Ticker> findByCodeIn(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, Ticker> matches = new java.util.LinkedHashMap<>();
        for (String code : codes) {
            findAllByIndex(INDEX_CODE, code).forEach(ticker -> matches.putIfAbsent(ticker.getId(), ticker));
        }
        return List.copyOf(matches.values());
    }
}
