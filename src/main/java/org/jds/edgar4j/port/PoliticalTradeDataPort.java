package org.jds.edgar4j.port;

import java.util.Optional;

import org.jds.edgar4j.model.PoliticalTrade;

public interface PoliticalTradeDataPort extends BaseDocumentDataPort<PoliticalTrade> {

    Optional<PoliticalTrade> findBySourceTradeId(String sourceTradeId);

    boolean existsBySourceTradeId(String sourceTradeId);
}
