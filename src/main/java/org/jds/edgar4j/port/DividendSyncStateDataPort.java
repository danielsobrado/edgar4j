package org.jds.edgar4j.port;

import java.util.Optional;

import org.jds.edgar4j.model.DividendSyncState;

public interface DividendSyncStateDataPort extends BaseDocumentDataPort<DividendSyncState> {

    Optional<DividendSyncState> findByCik(String cik);

    Optional<DividendSyncState> findByTickerIgnoreCase(String ticker);
}
