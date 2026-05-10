package org.jds.edgar4j.service.impl;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.port.TickerDataPort;
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
    private final TickerDataPort tickerRepository;

    @Override
    public int downloadTickers() {
        log.info("Download tickers");

        String jsonResponse = secApiClient.fetchCompanyTickers();
        log.debug("Received tickers response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersJson(jsonResponse);
        log.info("Parsed {} tickers", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} tickers", tickers.size());
        return tickers.size();
    }

    @Override
    public int downloadTickersExchanges() {
        log.info("Download tickers with exchanges");

        String jsonResponse = secApiClient.fetchCompanyTickersExchanges();
        log.debug("Received tickers exchanges response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersExchangeJson(jsonResponse);
        log.info("Parsed {} tickers with exchanges", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} tickers with exchanges", tickers.size());
        return tickers.size();
    }

    @Override
    public int downloadTickersMFs() {
        log.info("Download mutual fund tickers");

        String jsonResponse = secApiClient.fetchCompanyTickersMutualFunds();
        log.debug("Received mutual fund tickers response length: {} characters", jsonResponse.length());

        List<Ticker> tickers = responseParser.parseTickersJson(jsonResponse);
        log.info("Parsed {} mutual fund tickers", tickers.size());

        saveTickers(tickers);
        log.info("Saved {} mutual fund tickers", tickers.size());
        return tickers.size();
    }

    private void saveTickers(List<Ticker> tickers) {
        List<String> codes = tickers.stream()
                .map(Ticker::getCode)
                .map(this::normalizeTickerCode)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();

        if (!codes.isEmpty()) {
            Map<String, String> existingIdsByCode = tickerRepository.findByCodeIn(codes).stream()
                    .filter(existing -> existing.getCode() != null && !existing.getCode().isBlank())
                    .collect(Collectors.toMap(
                        existing -> normalizeTickerCode(existing.getCode()),
                        Ticker::getId,
                        (left, right) -> left,
                        LinkedHashMap::new));

            for (Ticker ticker : tickers) {
                String normalizedCode = normalizeTickerCode(ticker.getCode());
                ticker.setCode(normalizedCode);
                if (normalizedCode == null) {
                    continue;
                }

                String existingId = existingIdsByCode.get(normalizedCode);
                if (existingId != null) {
                    ticker.setId(existingId);
                }
            }
        }

        tickerRepository.saveAll(tickers);
    }

    private String normalizeTickerCode(String code) {
        if (code == null) {
            return null;
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}

