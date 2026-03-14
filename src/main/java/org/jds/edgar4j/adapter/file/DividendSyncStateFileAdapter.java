package org.jds.edgar4j.adapter.file;

import java.util.Optional;

import org.jds.edgar4j.model.DividendSyncState;
import org.jds.edgar4j.port.DividendSyncStateDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class DividendSyncStateFileAdapter extends AbstractFileDataPort<DividendSyncState>
        implements DividendSyncStateDataPort {

    private static final String INDEX_CIK = "cik";
    private static final String INDEX_TICKER = "ticker";

    public DividendSyncStateFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "dividend_sync_states",
                DividendSyncState.class,
                FileFormat.JSON,
                DividendSyncState::getId,
                DividendSyncState::setId));
        registerExactIndex(INDEX_CIK, DividendSyncState::getCik);
        registerIgnoreCaseIndex(INDEX_TICKER, DividendSyncState::getTicker);
    }

    @Override
    public Optional<DividendSyncState> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public Optional<DividendSyncState> findByTickerIgnoreCase(String ticker) {
        return findFirstByIndex(INDEX_TICKER, ticker);
    }
}
