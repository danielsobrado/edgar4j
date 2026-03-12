package org.jds.edgar4j.service;

import java.time.LocalDate;

import org.jds.edgar4j.dto.response.MarketDataResponse;

public interface MarketDataService {

    MarketDataResponse getDailyPrices(String ticker, LocalDate startDate, LocalDate endDate);
}
