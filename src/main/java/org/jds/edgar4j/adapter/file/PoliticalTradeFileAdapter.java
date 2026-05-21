package org.jds.edgar4j.adapter.file;

import java.util.Optional;

import org.jds.edgar4j.model.PoliticalTrade;
import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class PoliticalTradeFileAdapter extends AbstractFileDataPort<PoliticalTrade> implements PoliticalTradeDataPort {

    private static final String INDEX_SOURCE_TRADE_ID = "sourceTradeId";

    public PoliticalTradeFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "political_trades",
                PoliticalTrade.class,
                FileFormat.JSONL,
                PoliticalTrade::getId,
                PoliticalTrade::setId));
        registerExactIndex(INDEX_SOURCE_TRADE_ID, PoliticalTrade::getSourceTradeId);
    }

    @Override
    public Optional<PoliticalTrade> findBySourceTradeId(String sourceTradeId) {
        return findFirstByIndex(INDEX_SOURCE_TRADE_ID, sourceTradeId);
    }

    @Override
    public boolean existsBySourceTradeId(String sourceTradeId) {
        return existsByIndex(INDEX_SOURCE_TRADE_ID, sourceTradeId);
    }
}
