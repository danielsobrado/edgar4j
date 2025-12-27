package org.jds.edgar4j.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        List<String> codes = tickers.stream()
                .map(Ticker::getCode)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();

        if (!codes.isEmpty()) {
            Map<String, String> existingIdsByCode = tickerRepository.findByCodeIn(codes).stream()
                    .filter(existing -> existing.getCode() != null)
                    .collect(Collectors.toMap(Ticker::getCode, Ticker::getId, (left, right) -> left));

            for (Ticker ticker : tickers) {
                String existingId = existingIdsByCode.get(ticker.getCode());
                if (existingId != null) {
                    ticker.setId(existingId);
                }
            }
        }

        tickerRepository.saveAll(tickers);
    }
}

