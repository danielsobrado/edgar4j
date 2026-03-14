package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Ticker;

public interface TickerDataPort extends BaseDocumentDataPort<Ticker> {

    Optional<Ticker> findByCode(String code);

    Optional<Ticker> findByCik(String cik);

    List<Ticker> findByCodeIn(List<String> codes);
}
