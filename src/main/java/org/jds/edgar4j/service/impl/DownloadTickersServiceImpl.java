package org.jds.edgar4j.service.impl;

import java.util.List;

import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.service.DownloadTickersService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadTickersServiceImpl implements DownloadTickersService {

    private final SecApiClient secApiClient;
    private final SecResponseParser responseParser;
    private final TickerRepository tickerRepository;

    @Override
    public void downloadTickers() {
        log.info("Download tickers");

        String jsonResponse = secApiClient.fetchCompanyTickers();
        log.debug("Received tickers response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersJson(jsonResponse);
        log.info("Parsed {} tickers", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} tickers", tickers.size());
    }

    @Override
    public void downloadTickersExchanges() {
        log.info("Download tickers with exchanges");

        String jsonResponse = secApiClient.fetchCompanyTickersExchanges();
        log.debug("Received tickers exchanges response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersExchangeJson(jsonResponse);
        log.info("Parsed {} tickers with exchanges", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} tickers with exchanges", tickers.size());
    }

    @Override
    public void downloadTickersMFs() {
        log.info("Download mutual fund tickers");

        String jsonResponse = secApiClient.fetchCompanyTickersMutualFunds();
        log.debug("Received mutual fund tickers response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersJson(jsonResponse);
        log.info("Parsed {} mutual fund tickers", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} mutual fund tickers", tickers.size());
    }

    private void saveTickers(List<Ticker> tickers) {
        for (Ticker ticker : tickers) {
            if (ticker.getCode() != null) {
                Ticker existingTicker = tickerRepository.findByCode(ticker.getCode()).orElse(null);
                if (existingTicker != null) {
                    ticker.setId(existingTicker.getId());
                }
            }
        }
        tickerRepository.saveAll(tickers);
    }
}

