package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.model.CompanyMarketData;

public interface CompanyMarketDataService {

    CompanyMarketData fetchAndSaveQuote(String ticker);

    List<CompanyMarketData> fetchAndSaveQuotesBatch(List<String> tickers);

    Optional<CompanyMarketData> getStoredMarketData(String ticker);

    Optional<CompanyMarketData> getMarketData(String ticker);

    Double getCurrentPrice(String ticker);

    Double getHistoricalClosePrice(String ticker, LocalDate date);

    MarketCapBackfillResponse backfillMissingMarketCaps(List<String> tickers, int batchSize, int maxTickers);

    long count();
}
