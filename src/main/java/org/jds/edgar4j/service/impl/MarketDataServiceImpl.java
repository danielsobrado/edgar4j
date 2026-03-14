package org.jds.edgar4j.service.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.dayOfWeekInMonth;
import static java.time.temporal.TemporalAdjusters.lastInMonth;

import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.service.MarketDataService;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.jds.edgar4j.service.provider.MarketDataProviders;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private static final String DEFAULT_SETTINGS_ID = "default";
    private static final String DEFAULT_TIINGO_BASE_URL = "https://api.tiingo.com";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Duration TIINGO_TIMEOUT = Duration.ofSeconds(30);

    private final AppSettingsDataPort appSettingsRepository;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final TiingoEnvProperties tiingoEnvProperties;
    private final org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;
    private final MarketDataProviderSettingsResolver marketDataProviderSettingsResolver;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIINGO_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public MarketDataResponse getDailyPrices(String ticker, LocalDate startDate, LocalDate endDate) {
        String normalizedTicker = normalizeTicker(ticker);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }

        AppSettings settings = getSettings();
        String provider = resolveProvider(settings);
        if (MarketDataProviders.NONE.equals(provider)) {
            throw new IllegalStateException("Market data is disabled. Configure Tiingo in Settings to show price charts.");
        }
        if (MarketDataProviders.isProviderServiceProvider(provider)) {
            return getProviderBackedDailyPrices(normalizedTicker, startDate, endDate, provider);
        }
        if (!MarketDataProviders.TIINGO.equals(provider)) {
            throw new IllegalArgumentException("Unsupported market data provider: " + provider);
        }

        String apiKey = resolveApiKey(settings);
        if (apiKey == null) {
            throw new IllegalStateException("Tiingo API key is missing. Configure it in Settings to show price charts.");
        }

        Path cacheFile = getCacheFile(normalizedTicker);
        List<MarketDataResponse.PriceBar> cachedPrices = readCachedPrices(cacheFile);
        LocalDate today = LocalDate.now();
        List<DateRange> missingRanges = findMissingTradingRanges(cachedPrices, startDate, endDate, today);
        if (!missingRanges.isEmpty()) {
            List<MarketDataResponse.PriceBar> downloaded = new ArrayList<>();
            String baseUrl = resolveBaseUrl(settings);
            for (DateRange missingRange : missingRanges) {
                downloaded.addAll(downloadTiingoPrices(
                        normalizedTicker,
                        missingRange.start(),
                        missingRange.end(),
                        baseUrl,
                        apiKey));
            }

            cachedPrices = mergePrices(cachedPrices, downloaded);
            writeCachedPrices(cacheFile, cachedPrices);
        }

        List<MarketDataResponse.PriceBar> filteredPrices = cachedPrices.stream()
                .filter(price -> !price.getDate().isBefore(startDate) && !price.getDate().isAfter(endDate))
                .sorted(Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                .toList();

        return MarketDataResponse.builder()
                .ticker(normalizedTicker)
                .provider(provider)
                .startDate(startDate)
                .endDate(endDate)
                .prices(filteredPrices)
                .build();
    }

    private MarketDataResponse getProviderBackedDailyPrices(
            String ticker,
            LocalDate startDate,
            LocalDate endDate,
            String provider) {
        List<MarketDataProvider.StockPrice> prices = providerMarketDataService
                .getHistoricalPrices(ticker, startDate, endDate, provider)
                .join();

        List<MarketDataResponse.PriceBar> priceBars = prices.stream()
                .filter(price -> price.getDate() != null)
                .sorted(Comparator.comparing(MarketDataProvider.StockPrice::getDate))
                .map(this::toPriceBar)
                .toList();

        return MarketDataResponse.builder()
                .ticker(ticker)
                .provider(provider)
                .startDate(startDate)
                .endDate(endDate)
                .prices(priceBars)
                .build();
    }

    private AppSettings getSettings() {
        return appSettingsRepository.findById(DEFAULT_SETTINGS_ID)
                .orElseGet(() -> AppSettings.builder().id(DEFAULT_SETTINGS_ID).build());
    }

    private String normalizeTicker(String ticker) {
        String normalized = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return normalized;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_TIINGO_BASE_URL;
        }

        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/api/test")) {
            normalized = normalized.substring(0, normalized.length() - "/api/test".length());
        } else if (normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - "/api".length());
        }

        return normalized.replaceAll("/+$", "");
    }

    private Path getCacheFile(String ticker) {
        Path baseDir = tiingoEnvProperties.getDataDir()
                .map(Path::of)
                .orElse(Path.of("data", "market-data", "tiingo"));
        return baseDir.resolve("edgar4j").resolve("prices_1d").resolve(ticker + ".csv");
    }

    private String resolveProvider(AppSettings settings) {
        return marketDataProviderSettingsResolver.resolveSelectedProvider(settings);
    }

    private String resolveApiKey(AppSettings settings) {
        return marketDataProviderSettingsResolver.resolve(MarketDataProviders.TIINGO, settings).apiKey();
    }

    private String resolveBaseUrl(AppSettings settings) {
        return normalizeBaseUrl(marketDataProviderSettingsResolver.resolve(MarketDataProviders.TIINGO, settings).baseUrl());
    }

    private MarketDataResponse.PriceBar toPriceBar(MarketDataProvider.StockPrice stockPrice) {
        return MarketDataResponse.PriceBar.builder()
                .date(stockPrice.getDate())
                .open(firstNonZero(stockPrice.getOpen(), stockPrice.getPrice()))
                .high(firstNonZero(stockPrice.getHigh(), stockPrice.getPrice()))
                .low(firstNonZero(stockPrice.getLow(), stockPrice.getPrice()))
                .close(firstNonZero(stockPrice.getClose(), stockPrice.getPrice()))
                .volume(stockPrice.getVolume() != null ? stockPrice.getVolume().doubleValue() : 0d)
                .build();
    }

    private double firstNonZero(java.math.BigDecimal primaryValue, java.math.BigDecimal fallbackValue) {
        if (primaryValue != null) {
            return primaryValue.doubleValue();
        }
        return valueOrZero(fallbackValue);
    }

    private double valueOrZero(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : 0d;
    }

    private List<MarketDataResponse.PriceBar> downloadTiingoPrices(
            String ticker,
            LocalDate startDate,
            LocalDate endDate,
            String baseUrl,
            String apiKey) {
        try {
            String url = String.format(
                    "%s/tiingo/daily/%s/prices?startDate=%s&endDate=%s&resampleFreq=daily&format=json&token=%s",
                    normalizeBaseUrl(baseUrl),
                    URLEncoder.encode(ticker.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8),
                    DATE_FORMAT.format(startDate),
                    DATE_FORMAT.format(endDate),
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

            log.info("Downloading market data for {} from {} to {}", ticker, startDate, endDate);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", settingsService.getUserAgent())
                    .header("Accept", "application/json")
                    .timeout(TIINGO_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Tiingo request failed with HTTP " + response.statusCode());
            }

            JsonNode payload = objectMapper.readTree(response.body());
            if (!payload.isArray()) {
                throw new IllegalStateException("Unexpected Tiingo response format");
            }

            List<MarketDataResponse.PriceBar> prices = new ArrayList<>();
            for (JsonNode node : payload) {
                prices.add(MarketDataResponse.PriceBar.builder()
                        .date(parseDate(node.path("date").asText()))
                        .open(node.path("open").asDouble())
                        .high(node.path("high").asDouble())
                        .low(node.path("low").asDouble())
                        .close(node.path("close").asDouble())
                        .volume(node.path("volume").asDouble())
                        .build());
            }

            return prices.stream()
                    .sorted(Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                    .toList();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to download market data for " + ticker, e);
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Price row date is missing");
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (Exception ignored) {
            return LocalDate.parse(raw.substring(0, 10), DATE_FORMAT);
        }
    }

    private List<MarketDataResponse.PriceBar> mergePrices(
            List<MarketDataResponse.PriceBar> existing,
            List<MarketDataResponse.PriceBar> downloaded) {
        Map<LocalDate, MarketDataResponse.PriceBar> merged = new LinkedHashMap<>();
        for (MarketDataResponse.PriceBar price : existing) {
            merged.put(price.getDate(), price);
        }
        for (MarketDataResponse.PriceBar price : downloaded) {
            merged.put(price.getDate(), price);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                .toList();
    }

    private List<MarketDataResponse.PriceBar> readCachedPrices(Path cacheFile) {
        if (!Files.exists(cacheFile)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(cacheFile, StandardCharsets.UTF_8);
            List<MarketDataResponse.PriceBar> prices = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 7) {
                    continue;
                }

                prices.add(MarketDataResponse.PriceBar.builder()
                        .date(LocalDate.parse(parts[1], DATE_FORMAT))
                        .open(Double.parseDouble(parts[2]))
                        .high(Double.parseDouble(parts[3]))
                        .low(Double.parseDouble(parts[4]))
                        .close(Double.parseDouble(parts[5]))
                        .volume(Double.parseDouble(parts[6]))
                        .build());
            }
            return prices.stream()
                    .sorted(Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read cached market data from " + cacheFile, e);
        }
    }

    private void writeCachedPrices(Path cacheFile, List<MarketDataResponse.PriceBar> prices) {
        try {
            Files.createDirectories(cacheFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
                writer.write("symbol,trade_date,open,high,low,close,volume");
                writer.newLine();
                for (MarketDataResponse.PriceBar price : prices) {
                    writer.write(String.join(",",
                            cacheFile.getFileName().toString().replace(".csv", ""),
                            DATE_FORMAT.format(price.getDate()),
                            Double.toString(price.getOpen()),
                            Double.toString(price.getHigh()),
                            Double.toString(price.getLow()),
                            Double.toString(price.getClose()),
                            Double.toString(price.getVolume())));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to cache market data to " + cacheFile, e);
        }
    }

    static List<DateRange> findMissingTradingRanges(
            List<MarketDataResponse.PriceBar> prices,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today) {
        if (endDate.isBefore(startDate)) {
            return List.of();
        }

        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;
        if (effectiveEndDate.isBefore(startDate)) {
            return List.of();
        }

        Set<LocalDate> availableDates = prices.stream()
                .map(MarketDataResponse.PriceBar::getDate)
                .collect(Collectors.toCollection(HashSet::new));

        List<DateRange> missingRanges = new ArrayList<>();
        LocalDate rangeStart = null;
        LocalDate rangeEnd = null;

        for (LocalDate cursor = startDate; !cursor.isAfter(effectiveEndDate); cursor = cursor.plusDays(1)) {
            if (!isExpectedTradingDay(cursor)) {
                continue;
            }

            if (availableDates.contains(cursor)) {
                if (rangeStart != null) {
                    missingRanges.add(new DateRange(rangeStart, rangeEnd));
                    rangeStart = null;
                    rangeEnd = null;
                }
                continue;
            }

            if (rangeStart == null) {
                rangeStart = cursor;
            }
            rangeEnd = cursor;
        }

        if (rangeStart != null) {
            missingRanges.add(new DateRange(rangeStart, rangeEnd));
        }

        return missingRanges;
    }

    static boolean isExpectedTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != SATURDAY
                && dayOfWeek != SUNDAY
                && !isUsMarketHoliday(date);
    }

    private static boolean isUsMarketHoliday(LocalDate date) {
        int year = date.getYear();
        return date.equals(observeFixedHoliday(LocalDate.of(year, 1, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year + 1, 1, 1)))
                || date.equals(dayOfWeekInMonth(3, MONDAY).adjustInto(LocalDate.of(year, 1, 1)))
                || date.equals(dayOfWeekInMonth(3, MONDAY).adjustInto(LocalDate.of(year, 2, 1)))
                || date.equals(calculateGoodFriday(year))
                || date.equals(lastInMonth(MONDAY).adjustInto(LocalDate.of(year, 5, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 6, 19)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 7, 4)))
                || date.equals(dayOfWeekInMonth(1, MONDAY).adjustInto(LocalDate.of(year, 9, 1)))
                || date.equals(dayOfWeekInMonth(4, THURSDAY).adjustInto(LocalDate.of(year, 11, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 12, 25)));
    }

    private static LocalDate observeFixedHoliday(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private static LocalDate calculateGoodFriday(int year) {
        LocalDate easterSunday = calculateEasterSunday(year);
        return easterSunday.minusDays(2);
    }

    private static LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    record DateRange(LocalDate start, LocalDate end) {
    }
}
