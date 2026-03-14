package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jds.edgar4j.config.CacheConfig;
import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.MarketCapSource;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.repository.CompanyMarketDataRepository;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.MarketDataService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.util.TickerNormalizer;
import org.jds.edgar4j.xbrl.XbrlService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMarketDataServiceImpl implements CompanyMarketDataService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final Duration XBRL_FILING_TIMEOUT = Duration.ofSeconds(45);
    private static final int XBRL_FILING_SCAN_LIMIT = 12;
    private static final List<String> PREFERRED_XBRL_MARKET_CAP_FORMS = List.of(
            "10-Q",
            "10-Q/A",
            "10-K",
            "10-K/A",
            "20-F",
            "20-F/A",
            "40-F",
            "40-F/A",
            "N-CSR",
            "N-CSR/A",
            "N-CSRS",
            "N-CSRS/A",
            "6-K",
            "6-K/A");

    private final CompanyMarketDataRepository marketDataRepository;
    private final CompanyTickerRepository companyTickerRepository;
    private final FillingRepository fillingRepository;
    private final MarketDataService historicalMarketDataService;
    private final org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;
    private final CacheManager cacheManager;
    private final SecApiConfig secApiConfig;
    private final SecApiClient secApiClient;
    private final SecResponseParser secResponseParser;
    private final ObjectMapper objectMapper;
    private final XbrlService xbrlService;

    @Override
    public CompanyMarketData fetchAndSaveQuote(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return null;
        }

        try {
            Optional<CompanyMarketData> existingMarketData = marketDataRepository.findByTickerIgnoreCase(normalizedTicker);
            CompletableFuture<MarketDataProvider.StockPrice> priceFuture =
                    providerMarketDataService.getCurrentPrice(normalizedTicker);
            CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture =
                    providerMarketDataService.getCompanyProfile(normalizedTicker);

            CompletableFuture.allOf(priceFuture, profileFuture).join();

            MarketDataProvider.StockPrice stockPrice = priceFuture.join();
            MarketDataProvider.CompanyProfile companyProfile = profileFuture.join();
            CompanyMarketData existing = existingMarketData.orElse(null);
            ResolvedQuoteData resolvedQuoteData = applyHistoricalQuoteFallback(
                    normalizedTicker,
                    resolveQuoteData(normalizedTicker, stockPrice, companyProfile));
            ResolvedQuoteData mergedQuoteData = mergeWithExistingMarketData(
                    resolvedQuoteData,
                    existing);

            if (mergedQuoteData == null || mergedQuoteData.currentPrice() == null || mergedQuoteData.currentPrice() <= 0d) {
                log.warn("No valid provider quote snapshot found for ticker {}", normalizedTicker);
                return null;
            }

            String cik = resolveCik(existing, normalizedTicker);
            ResolvedQuoteData enrichedQuoteData = enrichMarketCap(
                    normalizedTicker,
                    cik,
                    mergedQuoteData,
                    companyProfile);
            Instant now = Instant.now();
            CompanyMarketData marketData = existingMarketData
                    .map(current -> updateMarketData(current, enrichedQuoteData, now))
                    .orElseGet(() -> CompanyMarketData.builder()
                            .ticker(normalizedTicker)
                            .cik(cik)
                            .marketCap(enrichedQuoteData.marketCap())
                            .marketCapSource(normalizeMarketCapSource(
                                    enrichedQuoteData.marketCapSource(),
                                    enrichedQuoteData.marketCap()))
                            .currentPrice(enrichedQuoteData.currentPrice())
                            .previousClose(enrichedQuoteData.previousClose())
                            .currency(resolveCurrency(enrichedQuoteData.currency()))
                            .lastUpdated(now)
                            .build());

            if (marketData.getCik() == null) {
                marketData.setCik(cik);
            }

            return marketDataRepository.save(marketData);
        } catch (Exception e) {
            log.error("Failed to fetch market data for {}", normalizedTicker, e);
            return null;
        }
    }

    @Override
    public List<CompanyMarketData> fetchAndSaveQuotesBatch(List<String> tickers) {
        List<String> normalizedTickers = normalizeBatchTickers(tickers);
        List<CompanyMarketData> results = new ArrayList<>();

        int success = 0;
        int failed = 0;

        for (String ticker : normalizedTickers) {
            CompanyMarketData marketData = fetchAndSaveQuote(ticker);
            if (marketData != null) {
                results.add(marketData);
                success++;
            } else {
                failed++;
            }
        }

        log.info("Company market data batch fetch completed: {}/{} success, {} failed",
                success, normalizedTickers.size(), failed);
        return results;
    }

    @Override
    public Optional<CompanyMarketData> getStoredMarketData(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return Optional.empty();
        }

        return marketDataRepository.findByTickerIgnoreCase(normalizedTicker);
    }

    @Override
    public Optional<CompanyMarketData> getMarketData(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return Optional.empty();
        }

        Optional<CompanyMarketData> existingMarketData = getStoredMarketData(normalizedTicker);
        if (existingMarketData.isPresent() && !needsRefresh(existingMarketData.get())) {
            return existingMarketData;
        }

        CompanyMarketData refreshedMarketData = fetchAndSaveQuote(normalizedTicker);
        if (refreshedMarketData != null) {
            return Optional.of(refreshedMarketData);
        }

        return existingMarketData;
    }

    @Override
    public Double getCurrentPrice(String ticker) {
        return getMarketData(ticker)
                .map(CompanyMarketData::getCurrentPrice)
                .orElse(null);
    }

    @Override
    public Double getHistoricalClosePrice(String ticker, LocalDate date) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null || date == null) {
            return null;
        }

        try {
            MarketDataResponse response = historicalMarketDataService.getDailyPrices(
                    normalizedTicker,
                    date.minusDays(7),
                    date);

            if (response == null || response.getPrices() == null) {
                return null;
            }

            return response.getPrices().stream()
                    .filter(priceBar -> priceBar.getDate() != null)
                    .filter(priceBar -> !priceBar.getDate().isAfter(date))
                    .max(java.util.Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                    .map(MarketDataResponse.PriceBar::getClose)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve historical close for {} on {}", normalizedTicker, date, e);
            return null;
        }
    }

    @Override
    public MarketCapBackfillResponse backfillMissingMarketCaps(List<String> tickers, int batchSize, int maxTickers) {
        long startTime = System.currentTimeMillis();
        List<String> normalizedTickers = normalizeBatchTickers(tickers);
        int effectiveBatchSize = Math.max(1, batchSize);

        if (normalizedTickers.isEmpty()) {
            return MarketCapBackfillResponse.builder()
                    .batchSize(effectiveBatchSize)
                    .maxTickers(Math.max(0, maxTickers))
                    .sampleUnresolvedTickers(List.of())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        Map<String, CompanyMarketData> existingByTicker = marketDataRepository.findByTickerIn(normalizedTickers).stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        marketData -> normalizeTicker(marketData.getTicker()),
                        Function.identity(),
                        (left, right) -> left));

        List<String> candidateTickers = normalizedTickers.stream()
                .filter(ticker -> needsMarketCapBackfill(existingByTicker.get(ticker)))
                .sorted(Comparator.naturalOrder())
                .toList();

        int trackedTickers = normalizedTickers.size();
        int candidateCount = candidateTickers.size();
        int upToDateTickers = trackedTickers - candidateCount;
        int effectiveMaxTickers = maxTickers > 0 ? maxTickers : candidateCount;
        List<String> processingTickers = candidateTickers.stream()
                .limit(effectiveMaxTickers)
                .toList();

        if (!processingTickers.isEmpty()) {
            clearMarketDataLookupCaches();
        }

        int updatedTickers = 0;
        LinkedHashSet<String> unresolvedTickers = new LinkedHashSet<>();

        for (int start = 0; start < processingTickers.size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, processingTickers.size());
            List<String> batch = processingTickers.subList(start, end);
            List<CompanyMarketData> batchResults = fetchAndSaveQuotesBatch(batch);
            Map<String, CompanyMarketData> batchResultsByTicker = batchResults.stream()
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            marketData -> normalizeTicker(marketData.getTicker()),
                            Function.identity(),
                            (left, right) -> left));

            for (String ticker : batch) {
                CompanyMarketData refreshedMarketData = batchResultsByTicker.get(ticker);
                if (hasResolvedMarketCap(refreshedMarketData)) {
                    updatedTickers++;
                } else {
                    unresolvedTickers.add(ticker);
                }
            }
        }

        return MarketCapBackfillResponse.builder()
                .trackedTickers(trackedTickers)
                .candidateTickers(candidateCount)
                .processedTickers(processingTickers.size())
                .updatedTickers(updatedTickers)
                .unresolvedTickersCount(unresolvedTickers.size())
                .upToDateTickers(upToDateTickers)
                .deferredTickers(candidateCount - processingTickers.size())
                .batchSize(effectiveBatchSize)
                .maxTickers(effectiveMaxTickers)
                .durationMs(System.currentTimeMillis() - startTime)
                .sampleUnresolvedTickers(unresolvedTickers.stream().limit(20).toList())
                .build();
    }

    @Override
    public long count() {
        return marketDataRepository.count();
    }

    ResolvedQuoteData resolveQuoteData(
            String ticker,
            MarketDataProvider.StockPrice stockPrice,
            MarketDataProvider.CompanyProfile companyProfile
    ) {
        if (stockPrice == null && companyProfile == null) {
            return null;
        }

        Double currentPrice = firstNonNull(
                stockPrice != null ? toNullableDouble(stockPrice.getPrice()) : null,
                stockPrice != null ? toNullableDouble(stockPrice.getClose()) : null);
        Double previousClose = firstNonNull(
                stockPrice != null ? toNullableDouble(stockPrice.getPreviousClose()) : null,
                stockPrice != null ? toNullableDouble(stockPrice.getClose()) : null);
        Double marketCap = firstNonNull(
                stockPrice != null ? toNullableDouble(stockPrice.getMarketCap()) : null,
                companyProfile != null ? toNullableDouble(companyProfile.getMarketCapitalization()) : null);
        MarketCapSource marketCapSource = isPositive(marketCap)
                ? MarketCapSource.PROVIDER_MARKET_CAP
                : null;
        String currency = firstNonNull(
                stockPrice != null ? blankToNull(stockPrice.getCurrency()) : null,
                companyProfile != null ? blankToNull(companyProfile.getCurrency()) : null,
                DEFAULT_CURRENCY);

        return new ResolvedQuoteData(
                currentPrice,
                previousClose,
                marketCap,
                marketCapSource,
                currency,
                normalizeTicker(ticker));
    }

    private ResolvedQuoteData enrichMarketCap(
            String ticker,
            String cik,
            ResolvedQuoteData quoteData,
            MarketDataProvider.CompanyProfile companyProfile) {
        if (quoteData == null || isPositive(quoteData.marketCap())) {
            return quoteData;
        }

        Double marketCap = deriveMarketCap(
                quoteData.currentPrice(),
                companyProfile != null ? companyProfile.getSharesOutstanding() : null);
        if (marketCap != null) {
            return withResolvedMarketCap(quoteData, marketCap, MarketCapSource.PROVIDER_SHARES_OUTSTANDING);
        }

        Long companyFactsShares = resolveSharesOutstandingFromCompanyFacts(cik, ticker);
        marketCap = deriveMarketCap(quoteData.currentPrice(), companyFactsShares);
        if (marketCap != null) {
            return withResolvedMarketCap(quoteData, marketCap, MarketCapSource.SEC_COMPANYFACTS_SHARES_OUTSTANDING);
        }

        Long filingShares = resolveSharesOutstandingFromFilingXbrl(cik, ticker);
        marketCap = deriveMarketCap(quoteData.currentPrice(), filingShares);
        if (marketCap != null) {
            return withResolvedMarketCap(quoteData, marketCap, MarketCapSource.SEC_FILING_XBRL_SHARES_OUTSTANDING);
        }

        return quoteData;
    }

    private CompanyMarketData updateMarketData(CompanyMarketData existing, ResolvedQuoteData quoteData, Instant now) {
        existing.setTicker(quoteData.ticker());
        if (quoteData.currentPrice() != null && quoteData.currentPrice() > 0d) {
            existing.setCurrentPrice(quoteData.currentPrice());
        }
        if (quoteData.previousClose() != null && quoteData.previousClose() > 0d) {
            existing.setPreviousClose(quoteData.previousClose());
        }
        if (quoteData.marketCap() != null && quoteData.marketCap() > 0d) {
            existing.setMarketCap(quoteData.marketCap());
            existing.setMarketCapSource(normalizeMarketCapSource(quoteData.marketCapSource(), quoteData.marketCap()));
        }
        if (existing.getCik() == null) {
            existing.setCik(resolveCik(existing.getTicker()));
        }
        if (existing.getMarketCapSource() == null && isPositive(existing.getMarketCap())) {
            existing.setMarketCapSource(MarketCapSource.UNKNOWN);
        }
        existing.setCurrency(resolveCurrency(quoteData.currency()));
        existing.setLastUpdated(now);
        return existing;
    }

    private List<String> normalizeBatchTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(tickers.stream()
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private String resolveCik(CompanyMarketData existingMarketData, String ticker) {
        String existingCik = existingMarketData != null ? blankToNull(existingMarketData.getCik()) : null;
        return existingCik != null ? existingCik : resolveCik(ticker);
    }

    private String resolveCik(String ticker) {
        return companyTickerRepository.findByTickerIgnoreCase(ticker)
                .map(CompanyTicker::getCikPadded)
                .orElseGet(() -> resolveCikFromSecCompanyTickers(ticker));
    }

    private String normalizeTicker(String ticker) {
        return TickerNormalizer.normalize(ticker);
    }

    private String resolveCurrency(String currency) {
        String normalizedCurrency = blankToNull(currency);
        return normalizedCurrency != null ? normalizedCurrency : DEFAULT_CURRENCY;
    }

    private ResolvedQuoteData mergeWithExistingMarketData(ResolvedQuoteData quoteData, CompanyMarketData existingMarketData) {
        if (quoteData == null || existingMarketData == null) {
            return quoteData;
        }

        Double mergedMarketCap = firstPositive(quoteData.marketCap(), existingMarketData.getMarketCap());
        return new ResolvedQuoteData(
                firstPositive(quoteData.currentPrice(), existingMarketData.getCurrentPrice()),
                firstPositive(quoteData.previousClose(), existingMarketData.getPreviousClose()),
                mergedMarketCap,
                resolveMergedMarketCapSource(quoteData, existingMarketData, mergedMarketCap),
                firstNonBlank(quoteData.currency(), existingMarketData.getCurrency(), DEFAULT_CURRENCY),
                firstNonBlank(quoteData.ticker(), normalizeTicker(existingMarketData.getTicker())));
    }

    private ResolvedQuoteData applyHistoricalQuoteFallback(String ticker, ResolvedQuoteData quoteData) {
        if (quoteData == null) {
            return null;
        }

        if (quoteData.currentPrice() != null && quoteData.currentPrice() > 0d) {
            return quoteData;
        }

        Double fallbackClose = resolveLatestHistoricalClose(ticker);
        if (fallbackClose == null || fallbackClose <= 0d) {
            return quoteData;
        }

        Double previousClose = quoteData.previousClose() != null ? quoteData.previousClose() : fallbackClose;
        return new ResolvedQuoteData(
                fallbackClose,
                previousClose,
                quoteData.marketCap(),
                quoteData.marketCapSource(),
                quoteData.currency(),
                quoteData.ticker());
    }

    private Double resolveLatestHistoricalClose(String ticker) {
        try {
            MarketDataResponse response = historicalMarketDataService.getDailyPrices(
                    ticker,
                    LocalDate.now().minusDays(7),
                    LocalDate.now());

            if (response == null || response.getPrices() == null || response.getPrices().isEmpty()) {
                return null;
            }

            return response.getPrices().stream()
                    .filter(priceBar -> priceBar.getDate() != null)
                    .filter(priceBar -> Double.isFinite(priceBar.getClose()) && priceBar.getClose() > 0d)
                    .max(java.util.Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                    .map(MarketDataResponse.PriceBar::getClose)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve historical fallback close for {}", ticker, e);
            return null;
        }
    }

    private boolean needsRefresh(CompanyMarketData marketData) {
        if (marketData == null) {
            return true;
        }

        if (marketData.getCurrentPrice() == null || marketData.getCurrentPrice() <= 0d) {
            return true;
        }

        if (marketData.getMarketCap() == null || marketData.getMarketCap() <= 0d) {
            return true;
        }

        if (marketData.getMarketCapSource() == null) {
            return true;
        }

        Instant lastUpdated = marketData.getLastUpdated();
        return lastUpdated == null || lastUpdated.isBefore(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private boolean needsMarketCapBackfill(CompanyMarketData marketData) {
        return !hasResolvedMarketCap(marketData);
    }

    private boolean hasValidMarketCap(CompanyMarketData marketData) {
        return marketData != null
                && marketData.getMarketCap() != null
                && marketData.getMarketCap() > 0d;
    }

    private boolean hasResolvedMarketCap(CompanyMarketData marketData) {
        return hasValidMarketCap(marketData)
                && marketData.getMarketCapSource() != null;
    }

    private void clearMarketDataLookupCaches() {
        clearCache(CacheConfig.CACHE_STOCK_PRICES);
        clearCache(CacheConfig.CACHE_COMPANY_PROFILES);
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private Long resolveSharesOutstandingFromCompanyFacts(String cik, String ticker) {
        if (cik == null || cik.isBlank()) {
            return null;
        }

        try {
            return extractLatestSharesOutstanding(secApiClient.fetchCompanyFacts(cik));
        } catch (Exception e) {
            log.debug("Could not resolve SEC companyfacts shares outstanding for {} ({})", ticker, cik, e);
            return null;
        }
    }

    private Long resolveSharesOutstandingFromFilingXbrl(String cik, String ticker) {
        if (cik == null || cik.isBlank()) {
            return null;
        }

        try {
            for (Filling filling : loadRecentXbrlFilings(cik, ticker)) {
                Long sharesOutstanding = extractSharesOutstandingFromFilingXbrl(filling, ticker);
                if (sharesOutstanding != null && sharesOutstanding > 0L) {
                    return sharesOutstanding;
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve filing XBRL shares outstanding for {} ({})", ticker, cik, e);
        }

        return null;
    }

    private List<Filling> loadRecentXbrlFilings(String cik, String ticker) {
        List<Filling> localFilings = sortXbrlFilingCandidates(fillingRepository.findRecentXbrlFilingsByCik(
                        cik,
                        PageRequest.of(0, XBRL_FILING_SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "fillingDate")))
                .getContent());
        if (!localFilings.isEmpty()) {
            return localFilings;
        }

        return loadRecentXbrlFilingsFromSecSubmissions(cik, ticker);
    }

    private List<Filling> loadRecentXbrlFilingsFromSecSubmissions(String cik, String ticker) {
        try {
            List<Filling> remoteFilings = sortXbrlFilingCandidates(
                    secResponseParser.toFillings(secResponseParser.parseSubmissionResponse(secApiClient.fetchSubmissions(cik))));
            if (!remoteFilings.isEmpty()) {
                log.debug("Using live SEC submissions fallback for XBRL filing lookup on {} ({})", ticker, cik);
            }
            return remoteFilings;
        } catch (Exception e) {
            log.debug("Could not load live SEC submissions for XBRL filing lookup on {} ({})", ticker, cik, e);
            return List.of();
        }
    }

    private List<Filling> sortXbrlFilingCandidates(List<Filling> filings) {
        if (filings == null || filings.isEmpty()) {
            return List.of();
        }

        return filings.stream()
                .filter(this::hasUsableXbrlDocument)
                .sorted(Comparator
                        .comparingInt(this::resolveXbrlFilingPriority)
                        .thenComparing(Filling::getFillingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(XBRL_FILING_SCAN_LIMIT)
                .toList();
    }

    private Long extractSharesOutstandingFromFilingXbrl(Filling filling, String ticker) {
        String filingUrl = resolveFilingUrl(filling);
        if (filingUrl == null) {
            return null;
        }

        try {
            var instance = xbrlService.parseFromUrl(filingUrl).block(XBRL_FILING_TIMEOUT);
            if (instance == null) {
                return null;
            }

            var secMetadata = xbrlService.extractSecMetadata(instance);
            Long sharesOutstanding = secMetadata != null ? secMetadata.getSharesOutstanding() : null;
            if (sharesOutstanding != null && sharesOutstanding > 0L) {
                log.debug("Resolved filing XBRL shares outstanding for {} from accession {}",
                        ticker,
                        filling.getAccessionNumber());
                return sharesOutstanding;
            }
        } catch (Exception e) {
            log.debug("Could not extract filing XBRL shares outstanding for {} from accession {}",
                    ticker,
                    filling.getAccessionNumber(),
                    e);
        }

        return null;
    }

    Long extractLatestSharesOutstanding(String companyFactsJson) {
        if (companyFactsJson == null || companyFactsJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(companyFactsJson);
            Long deiSharesOutstanding = extractLatestSharesOutstanding(
                    root.path("facts").path("dei").path("EntityCommonStockSharesOutstanding"));
            if (deiSharesOutstanding != null) {
                return deiSharesOutstanding;
            }

            return extractLatestSharesOutstanding(
                    root.path("facts").path("us-gaap").path("CommonStockSharesOutstanding"));
        } catch (Exception e) {
            log.debug("Could not parse SEC companyfacts response for shares outstanding", e);
            return null;
        }
    }

    private Long extractLatestSharesOutstanding(JsonNode conceptNode) {
        JsonNode units = conceptNode.path("units");
        if (!units.isObject()) {
            return null;
        }

        Long latestValue = null;
        LocalDate latestEndDate = null;
        LocalDate latestFiledDate = null;

        java.util.Iterator<Map.Entry<String, JsonNode>> fields = units.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode facts = entry.getValue();
            if (!facts.isArray()) {
                continue;
            }

            for (JsonNode fact : facts) {
                Long factValue = parsePositiveLong(fact.get("val"));
                if (factValue == null) {
                    continue;
                }

                LocalDate endDate = parseSecFactDate(fact.get("end"));
                LocalDate filedDate = parseSecFactDate(fact.get("filed"));

                if (isLaterFact(endDate, filedDate, latestEndDate, latestFiledDate)) {
                    latestValue = factValue;
                    latestEndDate = endDate;
                    latestFiledDate = filedDate;
                }
            }
        }

        return latestValue;
    }

    private boolean isLaterFact(
            LocalDate candidateEndDate,
            LocalDate candidateFiledDate,
            LocalDate currentEndDate,
            LocalDate currentFiledDate) {
        LocalDate normalizedCandidateEndDate = candidateEndDate != null ? candidateEndDate : LocalDate.MIN;
        LocalDate normalizedCurrentEndDate = currentEndDate != null ? currentEndDate : LocalDate.MIN;
        int endDateComparison = normalizedCandidateEndDate.compareTo(normalizedCurrentEndDate);
        if (endDateComparison != 0) {
            return endDateComparison > 0;
        }

        LocalDate normalizedCandidateFiledDate = candidateFiledDate != null ? candidateFiledDate : LocalDate.MIN;
        LocalDate normalizedCurrentFiledDate = currentFiledDate != null ? currentFiledDate : LocalDate.MIN;
        return normalizedCandidateFiledDate.isAfter(normalizedCurrentFiledDate);
    }

    private LocalDate parseSecFactDate(JsonNode dateNode) {
        String value = dateNode != null ? blankToNull(dateNode.asText(null)) : null;
        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.debug("Could not parse SEC fact date {}", value, e);
            return null;
        }
    }

    private Long parsePositiveLong(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        try {
            BigDecimal decimalValue = new BigDecimal(valueNode.asText());
            return decimalValue.signum() > 0 ? decimalValue.longValue() : null;
        } catch (NumberFormatException e) {
            log.debug("Could not parse positive long from {}", valueNode, e);
            return null;
        }
    }

    private Double deriveMarketCap(Double currentPrice, Long sharesOutstanding) {
        if (currentPrice == null || currentPrice <= 0d || sharesOutstanding == null || sharesOutstanding <= 0L) {
            return null;
        }

        return BigDecimal.valueOf(currentPrice)
                .multiply(BigDecimal.valueOf(sharesOutstanding))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private ResolvedQuoteData withResolvedMarketCap(
            ResolvedQuoteData quoteData,
            Double marketCap,
            MarketCapSource marketCapSource) {
        return new ResolvedQuoteData(
                quoteData.currentPrice(),
                quoteData.previousClose(),
                marketCap,
                marketCapSource,
                quoteData.currency(),
                quoteData.ticker());
    }

    private MarketCapSource resolveMergedMarketCapSource(
            ResolvedQuoteData quoteData,
            CompanyMarketData existingMarketData,
            Double mergedMarketCap) {
        if (!isPositive(mergedMarketCap)) {
            return null;
        }

        if (isPositive(quoteData.marketCap())) {
            return normalizeMarketCapSource(quoteData.marketCapSource(), quoteData.marketCap());
        }

        return normalizeMarketCapSource(existingMarketData.getMarketCapSource(), existingMarketData.getMarketCap());
    }

    private MarketCapSource normalizeMarketCapSource(MarketCapSource marketCapSource, Double marketCap) {
        if (!isPositive(marketCap)) {
            return null;
        }

        return marketCapSource != null ? marketCapSource : MarketCapSource.UNKNOWN;
    }

    private boolean hasUsableXbrlDocument(Filling filling) {
        return filling != null
                && (filling.isXBRL() || filling.isInlineXBRL())
                && resolveFilingUrl(filling) != null;
    }

    private int resolveXbrlFilingPriority(Filling filling) {
        String formType = normalizeFormType(filling != null && filling.getFormType() != null
                ? filling.getFormType().getNumber()
                : null);
        int preferredIndex = PREFERRED_XBRL_MARKET_CAP_FORMS.indexOf(formType);
        return preferredIndex >= 0 ? preferredIndex : PREFERRED_XBRL_MARKET_CAP_FORMS.size();
    }

    private String resolveCikFromSecCompanyTickers(String ticker) {
        CompanyTicker companyTicker = loadCompanyTickerFromSecFeed(ticker, secApiClient::fetchCompanyTickers);
        if (companyTicker == null) {
            companyTicker = loadCompanyTickerFromSecFeed(ticker, secApiClient::fetchCompanyTickersMutualFunds);
        }
        if (companyTicker == null) {
            return null;
        }

        persistCompanyTicker(companyTicker);
        return companyTicker.getCikPadded();
    }

    private CompanyTicker loadCompanyTickerFromSecFeed(String ticker, Supplier<String> feedLoader) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return null;
        }

        try {
            return secResponseParser.parseTickersJson(feedLoader.get()).stream()
                    .filter(Objects::nonNull)
                    .filter(secTicker -> normalizedTicker.equals(normalizeTicker(secTicker.getCode())))
                    .findFirst()
                    .map(this::toCompanyTicker)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve ticker {} from SEC company ticker feed", normalizedTicker, e);
            return null;
        }
    }

    private CompanyTicker toCompanyTicker(Ticker ticker) {
        if (ticker == null || ticker.getCik() == null || ticker.getCik().isBlank()) {
            return null;
        }

        try {
            return CompanyTicker.builder()
                    .ticker(normalizeTicker(ticker.getCode()))
                    .cikStr(Long.parseLong(ticker.getCik()))
                    .title(blankToNull(ticker.getName()))
                    .build();
        } catch (NumberFormatException e) {
            log.debug("Could not convert SEC ticker CIK {} for {}", ticker.getCik(), ticker.getCode(), e);
            return null;
        }
    }

    private void persistCompanyTicker(CompanyTicker companyTicker) {
        if (companyTicker == null || companyTicker.getTicker() == null || companyTicker.getCikStr() == null) {
            return;
        }

        try {
            if (companyTickerRepository.findByTickerIgnoreCase(companyTicker.getTicker()).isEmpty()) {
                companyTickerRepository.save(companyTicker);
            }
        } catch (Exception e) {
            log.debug("Could not persist SEC ticker fallback for {}", companyTicker.getTicker(), e);
        }
    }

    private String resolveFilingUrl(Filling filling) {
        if (filling == null) {
            return null;
        }

        String url = blankToNull(filling.getUrl());
        if (url != null) {
            return url.contains("://")
                    ? url
                    : secApiConfig.getArchiveUrl(url);
        }

        String cik = blankToNull(filling.getCik());
        String accessionNumber = blankToNull(filling.getAccessionNumber());
        String primaryDocument = blankToNull(filling.getPrimaryDocument());
        if (cik == null || accessionNumber == null || primaryDocument == null) {
            return null;
        }

        return secApiConfig.getFilingUrl(cik, accessionNumber, primaryDocument);
    }

    private String normalizeFormType(String formType) {
        return formType == null ? null : formType.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double toNullableDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private Double toNullableDouble(Long value) {
        return value != null ? value.doubleValue() : null;
    }

    private Double firstPositive(Double... values) {
        if (values == null) {
            return null;
        }

        for (Double value : values) {
            if (value != null && value > 0d) {
                return value;
            }
        }

        return null;
    }

    private boolean isPositive(Double value) {
        return value != null && value > 0d;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    record ResolvedQuoteData(
            Double currentPrice,
            Double previousClose,
            Double marketCap,
            MarketCapSource marketCapSource,
            String currency,
            String ticker) {
    }
}
