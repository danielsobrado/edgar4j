package org.jds.edgar4j.integration;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jds.edgar4j.exception.PoliticalTradeSyncException;
import org.jds.edgar4j.model.PoliticalTrade;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CapitolTradesPoliticalTradeSource implements PoliticalTradeSource {

    private static final String BASE_URL = "https://www.capitoltrades.com/trades";
    private static final int TIMEOUT_MILLIS = 30_000;
    private static final long PAGE_DELAY_MILLIS = 350L;

    private final CapitolTradesPoliticalTradeParser parser;

    public CapitolTradesPoliticalTradeSource() {
        this(Clock.systemDefaultZone());
    }

    CapitolTradesPoliticalTradeSource(Clock clock) {
        this.parser = new CapitolTradesPoliticalTradeParser(clock);
    }

    @Override
    public String sourceName() {
        return "CAPITOL_TRADES";
    }

    @Override
    public List<PoliticalTrade> fetch(PoliticalTradeSourceRequest request) {
        int maxPages = request == null ? 1 : Math.max(1, request.maxPages());
        String assetType = normalizeAssetType(request == null ? null : request.assetType());
        List<PoliticalTrade> trades = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            URI pageUri = buildUri(assetType, page);
            try {
                String html = fetchPage(pageUri);
                List<PoliticalTrade> pageTrades = parser.parse(html, pageUri, assetType);
                if (pageTrades.isEmpty()) {
                    if (page == 1) {
                        log.warn("No political trades parsed from first Capitol Trades page {}", pageUri);
                    } else {
                        log.info("No political trades parsed from {}", pageUri);
                    }
                    break;
                }
                log.info("Parsed {} political trades from {}", pageTrades.size(), pageUri);
                trades.addAll(pageTrades);
                if (page < maxPages) {
                    sleepBetweenPages();
                }
            } catch (IOException ex) {
                throw new PoliticalTradeSyncException(
                        "Failed to fetch political trades from " + pageUri,
                        "POLITICAL_TRADE_SOURCE_FETCH_FAILED",
                        HttpStatus.BAD_GATEWAY,
                        ex);
            }
        }

        return trades;
    }

    String fetchPage(URI pageUri) throws IOException {
        return Jsoup.connect(pageUri.toString())
                .userAgent("edgar4j political-trades-sync")
                .referrer("https://www.capitoltrades.com/")
                .timeout(TIMEOUT_MILLIS)
                .maxBodySize(0)
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .execute()
                .body();
    }

    private URI buildUri(String assetType, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("page", page);
        if (assetType != null) {
            builder.queryParam("assetType", assetType);
        }
        return builder.build(true).toUri();
    }

    private String normalizeAssetType(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void sleepBetweenPages() {
        try {
            Thread.sleep(PAGE_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Political trade sync interrupted", ex);
        }
    }
}
