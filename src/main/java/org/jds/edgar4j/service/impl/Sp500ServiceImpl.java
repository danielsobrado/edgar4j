package org.jds.edgar4j.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jds.edgar4j.config.CacheConfig;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Sp500Constituent;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.Sp500ConstituentDataPort;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.Sp500Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class Sp500ServiceImpl implements Sp500Service {

    private static final String WIKIPEDIA_SP500_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies";
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (compatible; Edgar4j/1.0; +https://github.com/jds/edgar4j)";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int MINIMUM_EXPECTED_COLUMNS = 7;
    private static final int TICKER_COLUMN = 0;
    private static final int COMPANY_NAME_COLUMN = 1;
    private static final int SECTOR_COLUMN = 2;
    private static final int SUB_INDUSTRY_COLUMN = 3;
    private static final int DATE_ADDED_COLUMN = 5;
    private static final int CIK_COLUMN = 6;
    private static final Pattern FOOTNOTE_PATTERN = Pattern.compile("\\[[^\\]]*]");
    private static final DateTimeFormatter LONG_MONTH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_MONTH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final Sp500ConstituentDataPort sp500Repository;
    private final CompanyTickerDataPort companyTickerRepository;
    private final SettingsService settingsService;

    @Override
    @CacheEvict(value = CacheConfig.CACHE_SP500, allEntries = true)
    public List<Sp500Constituent> syncFromWikipedia() {
        log.info("Starting S&P 500 sync from Wikipedia");

        long startTime = System.currentTimeMillis();
        Instant syncedAt = Instant.now();

        try {
            Document wikipediaDocument = fetchWikipediaDocument();
            List<Sp500Constituent> existingConstituents = sp500Repository.findAll();
            Map<String, Sp500Constituent> existingByTicker = new LinkedHashMap<>();
            for (Sp500Constituent constituent : existingConstituents) {
                String normalizedTicker = normalizeTicker(constituent.getTicker());
                if (normalizedTicker != null) {
                    existingByTicker.putIfAbsent(normalizedTicker, constituent);
                }
            }

            List<Sp500Constituent> parsedConstituents = parseConstituents(wikipediaDocument, existingByTicker, syncedAt);
            if (parsedConstituents.isEmpty()) {
                throw new IllegalStateException("No S&P 500 constituents were parsed from Wikipedia");
            }

            List<Sp500Constituent> savedConstituents = sp500Repository.saveAll(parsedConstituents).stream()
                    .sorted(java.util.Comparator.comparing(Sp500Constituent::getTicker))
                    .toList();

            Set<String> currentTickers = parsedConstituents.stream()
                    .map(Sp500Constituent::getTicker)
                    .collect(Collectors.toSet());

            List<Sp500Constituent> staleConstituents = existingConstituents.stream()
                    .filter(constituent -> constituent.getTicker() != null)
                    .filter(constituent -> !currentTickers.contains(normalizeTicker(constituent.getTicker())))
                    .toList();

            if (!staleConstituents.isEmpty()) {
                sp500Repository.deleteAll(staleConstituents);
                log.info("Removed {} stale S&P 500 constituents", staleConstituents.size());
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("S&P 500 sync completed: {} saved, {} removed in {} ms",
                    savedConstituents.size(), staleConstituents.size(), duration);

            return savedConstituents;
        } catch (Exception e) {
            log.error("Failed to sync S&P 500 constituents from Wikipedia", e);
            throw new IllegalStateException("Failed to sync S&P 500 constituents", e);
        }
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_SP500, key = "'all'")
    public List<Sp500Constituent> getAll() {
        return sp500Repository.findAllByOrderByTickerAsc();
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_SP500, key = "'tickers'")
    public Set<String> getAllTickers() {
        return sp500Repository.findAllByOrderByTickerAsc().stream()
                .map(Sp500Constituent::getTicker)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_SP500, key = "'member_' + (#ticker == null ? 'null' : #ticker.toUpperCase())")
    public boolean isSp500(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return false;
        }
        return sp500Repository.existsByTickerIgnoreCase(normalizedTicker);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_SP500, key = "'ticker_' + (#ticker == null ? 'null' : #ticker.toUpperCase())")
    public Optional<Sp500Constituent> findByTicker(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return Optional.empty();
        }
        return sp500Repository.findByTickerIgnoreCase(normalizedTicker);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_SP500, key = "'count'")
    public long count() {
        return sp500Repository.count();
    }

    Document fetchWikipediaDocument() throws IOException {
        return Jsoup.connect(WIKIPEDIA_SP500_URL)
                .userAgent(resolveUserAgent())
                .timeout(CONNECT_TIMEOUT_MS)
                .maxBodySize(0)
                .get();
    }

    List<Sp500Constituent> parseConstituents(
            Document document,
            Map<String, Sp500Constituent> existingByTicker,
            Instant syncedAt
    ) {
        Element constituentsTable = Optional.ofNullable(document.selectFirst("table#constituents"))
                .orElse(document.selectFirst("table.wikitable"));

        if (constituentsTable == null) {
            throw new IllegalStateException("Wikipedia S&P 500 constituents table was not found");
        }

        Elements rows = constituentsTable.select("tbody tr");
        Map<String, Sp500Constituent> parsedByTicker = new LinkedHashMap<>();

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < MINIMUM_EXPECTED_COLUMNS) {
                continue;
            }

            String ticker = normalizeTicker(cells.get(TICKER_COLUMN).text());
            if (ticker == null) {
                continue;
            }

            String companyName = cleanText(cells.get(COMPANY_NAME_COLUMN).text());
            String sector = cleanText(cells.get(SECTOR_COLUMN).text());
            String subIndustry = cleanText(cells.get(SUB_INDUSTRY_COLUMN).text());
            LocalDate dateAdded = parseDateSafe(cells.get(DATE_ADDED_COLUMN).text());
            String cik = normalizeCik(cells.get(CIK_COLUMN).text());
            if (cik == null) {
                cik = resolveCikFromTicker(ticker);
            }

            Sp500Constituent constituent = existingByTicker.getOrDefault(ticker, Sp500Constituent.builder().build());
            constituent.setTicker(ticker);
            constituent.setCompanyName(companyName);
            constituent.setCik(cik);
            constituent.setSector(sector);
            constituent.setSubIndustry(subIndustry);
            if (dateAdded != null || constituent.getDateAdded() == null) {
                constituent.setDateAdded(dateAdded);
            }
            constituent.setLastUpdated(syncedAt);

            parsedByTicker.put(ticker, constituent);
        }

        return new ArrayList<>(parsedByTicker.values());
    }

    private String resolveUserAgent() {
        try {
            String configuredUserAgent = settingsService.getUserAgent();
            if (configuredUserAgent != null && !configuredUserAgent.isBlank()) {
                return configuredUserAgent.trim();
            }
        } catch (Exception e) {
            log.debug("Falling back to default user agent for S&P 500 sync", e);
        }
        return DEFAULT_USER_AGENT;
    }

    private String resolveCikFromTicker(String ticker) {
        return companyTickerRepository.findByTickerIgnoreCase(ticker)
                .map(CompanyTicker::getCikPadded)
                .orElse(null);
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null) {
            return null;
        }

        String normalizedTicker = cleanText(ticker);
        if (normalizedTicker == null) {
            return null;
        }

        return normalizedTicker.replace('.', '-').toUpperCase(Locale.ROOT);
    }

    private String normalizeCik(String cik) {
        String cleanedCik = cleanText(cik);
        if (cleanedCik == null) {
            return null;
        }

        String digitsOnly = cleanedCik.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return null;
        }

        try {
            return String.format("%010d", Long.parseLong(digitsOnly));
        } catch (NumberFormatException e) {
            log.debug("Could not normalize CIK value: {}", cik);
            return digitsOnly;
        }
    }

    private LocalDate parseDateSafe(String rawDate) {
        String cleanedDate = cleanText(rawDate);
        if (cleanedDate == null) {
            return null;
        }

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                LONG_MONTH_DATE_FORMAT,
                SHORT_MONTH_DATE_FORMAT)) {
            try {
                return LocalDate.parse(cleanedDate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        log.debug("Could not parse S&P 500 date_added value: {}", rawDate);
        return null;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = FOOTNOTE_PATTERN.matcher(value.replace('\u00A0', ' ')).replaceAll("").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
