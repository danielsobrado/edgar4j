package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.CompanyMarketData;

public interface CompanyMarketDataDataPort extends BaseDocumentDataPort<CompanyMarketData> {

    Optional<CompanyMarketData> findByTickerIgnoreCase(String ticker);

    List<CompanyMarketData> findByTickerIn(List<String> tickers);
}
