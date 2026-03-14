package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.config.CacheConfig;
import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.model.MarketCapSource;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.CompanyMarketDataDataPort;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.MarketDataService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.xbrl.XbrlService;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CompanyMarketDataServiceImplTest {

    @Mock
    private CompanyMarketDataDataPort companyMarketDataRepository;

    @Mock
    private CompanyTickerDataPort companyTickerDataPort;

    @Mock
    private FillingDataPort fillingRepository;

    @Mock
    private MarketDataService historicalMarketDataService;

    @Mock
    private org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecApiConfig secApiConfig;

    @Mock
    private SecResponseParser secResponseParser;

    @Mock
    private XbrlService xbrlService;

    @Mock
    private Cache stockPricesCache;

    @Mock
    private Cache companyProfilesCache;

    private CompanyMarketDataServiceImpl companyMarketDataService;

    @BeforeEach
    void setUp() {
        companyMarketDataService = org.mockito.Mockito.spy(new CompanyMarketDataServiceImpl(
                companyMarketDataRepository,
                companyTickerDataPort,
                fillingRepository,
                historicalMarketDataService,
                providerMarketDataService,
                cacheManager,
                secApiConfig,
                secApiClient,
                secResponseParser,
                new ObjectMapper(),
                xbrlService));
    }

    @Test
    @DisplayName("fetchAndSaveQuote should persist merged provider quote and profile data")
    void fetchAndSaveQuoteShouldPersistMergedProviderData() {
        CompanyMarketData existing = CompanyMarketData.builder()
                .id("market-data-1")
                .ticker("AAPL")
                .currency("USD")
                .lastUpdated(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setSymbol("AAPL");
        stockPrice.setPrice(BigDecimal.valueOf(181.32d));
        stockPrice.setPreviousClose(BigDecimal.valueOf(179.55d));
        stockPrice.setMarketCap(3_250_000_000_000L);
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("AAPL");
        companyProfile.setMarketCapitalization(3_000_000_000_000L);
        companyProfile.setCurrency("USD");

        when(providerMarketDataService.getCurrentPrice("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(providerMarketDataService.getCompanyProfile("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(existing));
        when(companyTickerDataPort.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(
                CompanyTicker.builder()
                        .ticker("AAPL")
                        .cikStr(320193L)
                        .build()));
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("aapl");

        ArgumentCaptor<CompanyMarketData> savedCaptor = ArgumentCaptor.forClass(CompanyMarketData.class);
        verify(companyMarketDataRepository).save(savedCaptor.capture());

        assertNotNull(marketData);
        assertEquals("market-data-1", marketData.getId());
        assertEquals("AAPL", marketData.getTicker());
        assertEquals("0000320193", marketData.getCik());
        assertEquals(181.32d, marketData.getCurrentPrice());
        assertEquals(179.55d, marketData.getPreviousClose());
        assertEquals(3_250_000_000_000d, marketData.getMarketCap());
        assertEquals(MarketCapSource.PROVIDER_MARKET_CAP, marketData.getMarketCapSource());
        assertEquals("USD", marketData.getCurrency());
        assertNotNull(marketData.getLastUpdated());

        assertEquals("AAPL", savedCaptor.getValue().getTicker());
        assertEquals("0000320193", savedCaptor.getValue().getCik());
    }

    @Test
    @DisplayName("getHistoricalClosePrice should return the latest close on or before the requested date")
    void getHistoricalClosePriceShouldUseLatestAvailableBar() {
        LocalDate targetDate = LocalDate.of(2026, 3, 10);
        when(historicalMarketDataService.getDailyPrices("AAPL", targetDate.minusDays(7), targetDate))
                .thenReturn(MarketDataResponse.builder()
                        .ticker("AAPL")
                        .prices(List.of(
                                MarketDataResponse.PriceBar.builder().date(LocalDate.of(2026, 3, 6)).close(175.0).build(),
                                MarketDataResponse.PriceBar.builder().date(LocalDate.of(2026, 3, 10)).close(181.5).build()))
                        .build());

        Double historicalClose = companyMarketDataService.getHistoricalClosePrice("aapl", targetDate);

        assertEquals(181.5d, historicalClose);
    }

    @Test
    @DisplayName("fetchAndSaveQuotesBatch should normalize, de-duplicate, and skip blank tickers")
    void fetchAndSaveQuotesBatchShouldNormalizeAndDedupe() {
        CompanyMarketData aapl = CompanyMarketData.builder().ticker("AAPL").build();
        CompanyMarketData msft = CompanyMarketData.builder().ticker("MSFT").build();

        doReturn(aapl).when(companyMarketDataService).fetchAndSaveQuote("AAPL");
        doReturn(msft).when(companyMarketDataService).fetchAndSaveQuote("MSFT");

        List<CompanyMarketData> results = companyMarketDataService.fetchAndSaveQuotesBatch(
                Arrays.asList(" aapl ", "AAPL", null, "", "msft", "N/A", "NONE"));

        assertEquals(List.of("AAPL", "MSFT"),
                results.stream().map(CompanyMarketData::getTicker).toList());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should return null when no provider returns a valid quote")
    void fetchAndSaveQuoteShouldReturnNullWhenQuoteMissing() {
        when(providerMarketDataService.getCurrentPrice("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(providerMarketDataService.getCompanyProfile("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("AAPL");

        assertNull(marketData);
    }

    @Test
    @DisplayName("fetchAndSaveQuote should fall back to the latest historical close when provider current price is missing")
    void fetchAndSaveQuoteShouldFallbackToHistoricalClose() {
        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("AAPL");
        companyProfile.setMarketCapitalization(3_000_000_000_000L);
        companyProfile.setCurrency("USD");

        when(providerMarketDataService.getCurrentPrice("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(providerMarketDataService.getCompanyProfile("AAPL"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(historicalMarketDataService.getDailyPrices("AAPL", LocalDate.now().minusDays(7), LocalDate.now()))
                .thenReturn(MarketDataResponse.builder()
                        .ticker("AAPL")
                        .prices(List.of(
                                MarketDataResponse.PriceBar.builder().date(LocalDate.now().minusDays(1)).close(182.4d).build()))
                        .build());
        when(companyMarketDataRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.empty());
        when(companyTickerDataPort.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(
                CompanyTicker.builder()
                        .ticker("AAPL")
                        .cikStr(320193L)
                        .build()));
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("AAPL");

        assertNotNull(marketData);
        assertEquals(182.4d, marketData.getCurrentPrice());
        assertEquals(182.4d, marketData.getPreviousClose());
        assertEquals(3_000_000_000_000d, marketData.getMarketCap());
        assertEquals(MarketCapSource.PROVIDER_MARKET_CAP, marketData.getMarketCapSource());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should reuse stored price data when a provider only returns market cap")
    void fetchAndSaveQuoteShouldReuseStoredPriceWhenProviderOnlyReturnsMarketCap() {
        CompanyMarketData existing = CompanyMarketData.builder()
                .id("market-data-2")
                .ticker("PFE")
                .currentPrice(26.58d)
                .previousClose(26.86d)
                .currency("USD")
                .lastUpdated(Instant.parse("2026-03-13T00:00:00Z"))
                .build();
        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("PFE");
        companyProfile.setMarketCapitalization(151_140_990_976L);
        companyProfile.setCurrency("USD");

        when(providerMarketDataService.getCurrentPrice("PFE"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(providerMarketDataService.getCompanyProfile("PFE"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("PFE")).thenReturn(Optional.of(existing));
        when(companyTickerDataPort.findByTickerIgnoreCase("PFE")).thenReturn(Optional.empty());
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("pfe");

        assertNotNull(marketData);
        assertEquals(26.58d, marketData.getCurrentPrice());
        assertEquals(26.86d, marketData.getPreviousClose());
        assertEquals(151_140_990_976d, marketData.getMarketCap());
        assertEquals(MarketCapSource.PROVIDER_MARKET_CAP, marketData.getMarketCapSource());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should derive market cap from provider shares outstanding when market cap is absent")
    void fetchAndSaveQuoteShouldDeriveMarketCapFromProviderSharesOutstanding() {
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setSymbol("PANW");
        stockPrice.setPrice(BigDecimal.valueOf(215.25d));
        stockPrice.setPreviousClose(BigDecimal.valueOf(214.10d));
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("PANW");
        companyProfile.setSharesOutstanding(327_000_000L);
        companyProfile.setCurrency("USD");

        when(providerMarketDataService.getCurrentPrice("PANW"))
                .thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(providerMarketDataService.getCompanyProfile("PANW"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("PANW")).thenReturn(Optional.empty());
        when(companyTickerDataPort.findByTickerIgnoreCase("PANW")).thenReturn(Optional.empty());
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("panw");

        assertNotNull(marketData);
        assertEquals(70_386_750_000d, marketData.getMarketCap());
        assertEquals(215.25d, marketData.getCurrentPrice());
        assertEquals(MarketCapSource.PROVIDER_SHARES_OUTSTANDING, marketData.getMarketCapSource());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should derive market cap from SEC companyfacts shares outstanding when providers do not return it")
    void fetchAndSaveQuoteShouldDeriveMarketCapFromSecCompanyFacts() {
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setSymbol("OXY");
        stockPrice.setPrice(BigDecimal.valueOf(61.40d));
        stockPrice.setPreviousClose(BigDecimal.valueOf(60.85d));
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("OXY");
        companyProfile.setCurrency("USD");

        when(providerMarketDataService.getCurrentPrice("OXY"))
                .thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(providerMarketDataService.getCompanyProfile("OXY"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("OXY")).thenReturn(Optional.empty());
        when(companyTickerDataPort.findByTickerIgnoreCase("OXY")).thenReturn(Optional.of(
                CompanyTicker.builder()
                        .ticker("OXY")
                        .cikStr(797468L)
                        .build()));
        when(secApiClient.fetchCompanyFacts("0000797468")).thenReturn("""
                {
                  "facts": {
                    "dei": {
                      "EntityCommonStockSharesOutstanding": {
                        "units": {
                          "shares": [
                            { "end": "2025-12-31", "filed": "2026-01-15", "val": 962000000 },
                            { "end": "2024-12-31", "filed": "2025-01-15", "val": 970000000 }
                          ]
                        }
                      }
                    }
                  }
                }
                """);
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("OXY");

        assertNotNull(marketData);
        assertEquals("0000797468", marketData.getCik());
        assertEquals(59_066_800_000d, marketData.getMarketCap());
        assertEquals(MarketCapSource.SEC_COMPANYFACTS_SHARES_OUTSTANDING, marketData.getMarketCapSource());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should derive market cap from filing XBRL shares outstanding when companyfacts is empty")
    void fetchAndSaveQuoteShouldDeriveMarketCapFromFilingXbrl() {
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setSymbol("FSSL");
        stockPrice.setPrice(BigDecimal.valueOf(12.38d));
        stockPrice.setPreviousClose(BigDecimal.valueOf(12.31d));
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("FSSL");
        companyProfile.setCurrency("USD");

        Filling filling = Filling.builder()
                .cik("0002065812")
                .accessionNumber("0002065812-26-000001")
                .primaryDocument("fssl-20251231.htm")
                .formType(FormType.builder().number("10-K").build())
                .isInlineXBRL(true)
                .build();

        XbrlInstance instance = XbrlInstance.builder()
                .documentUri("https://www.sec.gov/Archives/edgar/data/2065812/000206581226000001/fssl-20251231.htm")
                .build();

        when(providerMarketDataService.getCurrentPrice("FSSL"))
                .thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(providerMarketDataService.getCompanyProfile("FSSL"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("FSSL")).thenReturn(Optional.empty());
        when(companyTickerDataPort.findByTickerIgnoreCase("FSSL")).thenReturn(Optional.of(
                CompanyTicker.builder()
                        .ticker("FSSL")
                        .cikStr(2_065_812L)
                        .build()));
        when(secApiClient.fetchCompanyFacts("0002065812")).thenReturn("""
                {
                  "cik": "0002065812",
                  "entityName": "",
                  "facts": {}
                }
                """);
        when(fillingRepository.findRecentXbrlFilingsByCik(any(), any()))
                .thenReturn(new PageImpl<>(List.of(filling)));
        when(secApiConfig.getFilingUrl("0002065812", "0002065812-26-000001", "fssl-20251231.htm"))
                .thenReturn("https://www.sec.gov/Archives/edgar/data/2065812/000206581226000001/fssl-20251231.htm");
        when(xbrlService.parseFromUrl("https://www.sec.gov/Archives/edgar/data/2065812/000206581226000001/fssl-20251231.htm"))
                .thenReturn(Mono.just(instance));
        when(xbrlService.extractSecMetadata(instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .sharesOutstanding(113_000_000L)
                .build());
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("FSSL");

        assertNotNull(marketData);
        assertEquals(1_398_940_000d, marketData.getMarketCap());
        assertEquals(MarketCapSource.SEC_FILING_XBRL_SHARES_OUTSTANDING, marketData.getMarketCapSource());
    }

    @Test
    @DisplayName("fetchAndSaveQuote should resolve CIK and XBRL filings from live SEC metadata when local collections are incomplete")
    void fetchAndSaveQuoteShouldFallbackToLiveSecMetadata() {
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setSymbol("FSSL");
        stockPrice.setPrice(BigDecimal.valueOf(12.38d));
        stockPrice.setPreviousClose(BigDecimal.valueOf(12.31d));
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setSymbol("FSSL");
        companyProfile.setCurrency("USD");

        Filling remoteFiling = Filling.builder()
                .cik("0002065812")
                .accessionNumber("0001410368-26-020653")
                .primaryDocument("fssl123125n-csr.htm")
                .formType(FormType.builder().number("N-CSR").build())
                .isInlineXBRL(true)
                .build();

        XbrlInstance instance = XbrlInstance.builder()
                .documentUri("https://www.sec.gov/Archives/edgar/data/2065812/000141036826020653/fssl123125n-csr.htm")
                .build();

        when(providerMarketDataService.getCurrentPrice("FSSL"))
                .thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(providerMarketDataService.getCompanyProfile("FSSL"))
                .thenReturn(CompletableFuture.completedFuture(companyProfile));
        when(companyMarketDataRepository.findByTickerIgnoreCase("FSSL")).thenReturn(Optional.empty());
        when(companyTickerDataPort.findByTickerIgnoreCase("FSSL"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(secApiClient.fetchCompanyTickers()).thenReturn("{\"0\":{\"cik_str\":2065812,\"ticker\":\"FSSL\",\"title\":\"FS Specialty Lending Fund\"}}");
        when(secResponseParser.parseTickersJson(any())).thenReturn(List.of(
                Ticker.builder()
                        .cik("0002065812")
                        .code("FSSL")
                        .name("FS Specialty Lending Fund")
                        .build()));
        when(secApiClient.fetchCompanyFacts("0002065812")).thenReturn("""
                {
                  "cik": "0002065812",
                  "entityName": "",
                  "facts": {}
                }
                """);
        when(fillingRepository.findRecentXbrlFilingsByCik(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(secApiClient.fetchSubmissions("0002065812")).thenReturn("{\"filings\":{\"recent\":{}}}");
        when(secResponseParser.parseSubmissionResponse(any())).thenReturn(new SecSubmissionResponse());
        when(secResponseParser.toFillings(any(SecSubmissionResponse.class))).thenReturn(List.of(remoteFiling));
        when(secApiConfig.getFilingUrl("0002065812", "0001410368-26-020653", "fssl123125n-csr.htm"))
                .thenReturn("https://www.sec.gov/Archives/edgar/data/2065812/000141036826020653/fssl123125n-csr.htm");
        when(xbrlService.parseFromUrl("https://www.sec.gov/Archives/edgar/data/2065812/000141036826020653/fssl123125n-csr.htm"))
                .thenReturn(Mono.just(instance));
        when(xbrlService.extractSecMetadata(instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .sharesOutstanding(113_000_000L)
                .build());
        when(companyMarketDataRepository.save(any(CompanyMarketData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMarketData marketData = companyMarketDataService.fetchAndSaveQuote("FSSL");

        assertNotNull(marketData);
        assertEquals("0002065812", marketData.getCik());
        assertEquals(1_398_940_000d, marketData.getMarketCap());
        assertEquals(MarketCapSource.SEC_FILING_XBRL_SHARES_OUTSTANDING, marketData.getMarketCapSource());
        verify(companyTickerDataPort).save(any(CompanyTicker.class));
    }

    @Test
    @DisplayName("getMarketData should refresh incomplete stored market data on demand")
    void getMarketDataShouldRefreshIncompleteStoredMarketData() {
        CompanyMarketData incomplete = CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(181.32d)
                .marketCap(null)
                .lastUpdated(Instant.now())
                .build();
        CompanyMarketData refreshed = CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(181.32d)
                .marketCap(3_250_000_000_000d)
                .lastUpdated(Instant.now())
                .build();

        when(companyMarketDataRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(incomplete));
        doReturn(refreshed).when(companyMarketDataService).fetchAndSaveQuote("AAPL");

        Optional<CompanyMarketData> marketData = companyMarketDataService.getMarketData("aapl");

        assertNotNull(marketData);
        assertEquals(Optional.of(refreshed), marketData);
    }

    @Test
    @DisplayName("getMarketData should keep existing data when refresh fails")
    void getMarketDataShouldKeepExistingDataWhenRefreshFails() {
        CompanyMarketData incomplete = CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(181.32d)
                .marketCap(null)
                .lastUpdated(Instant.now())
                .build();

        when(companyMarketDataRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(incomplete));
        doReturn(null).when(companyMarketDataService).fetchAndSaveQuote("AAPL");

        Optional<CompanyMarketData> marketData = companyMarketDataService.getMarketData("AAPL");

        assertNotNull(marketData);
        assertSame(incomplete, marketData.orElseThrow());
    }

    @Test
    @DisplayName("backfillMissingMarketCaps should only refresh candidates and report unresolved tickers")
    void backfillMissingMarketCapsShouldOnlyRefreshCandidates() {
        CompanyMarketData upToDate = CompanyMarketData.builder()
                .ticker("AAPL")
                .marketCap(3_000_000_000_000d)
                .marketCapSource(MarketCapSource.PROVIDER_MARKET_CAP)
                .build();
        CompanyMarketData missingCap = CompanyMarketData.builder()
                .ticker("MSFT")
                .marketCap(null)
                .build();

        when(companyMarketDataRepository.findByTickerIn(List.of("AAPL", "MSFT", "TSLA")))
                .thenReturn(List.of(upToDate, missingCap));
        when(cacheManager.getCache(CacheConfig.CACHE_STOCK_PRICES)).thenReturn(stockPricesCache);
        when(cacheManager.getCache(CacheConfig.CACHE_COMPANY_PROFILES)).thenReturn(companyProfilesCache);
        doReturn(List.of(
                CompanyMarketData.builder()
                        .ticker("MSFT")
                        .marketCap(2_900_000_000_000d)
                        .marketCapSource(MarketCapSource.PROVIDER_MARKET_CAP)
                        .build(),
                CompanyMarketData.builder().ticker("TSLA").marketCap(null).build()))
                .when(companyMarketDataService).fetchAndSaveQuotesBatch(List.of("MSFT", "TSLA"));

        MarketCapBackfillResponse response = companyMarketDataService.backfillMissingMarketCaps(
                List.of("aapl", "msft", "tsla"),
                10,
                10);

        assertEquals(3, response.getTrackedTickers());
        assertEquals(2, response.getCandidateTickers());
        assertEquals(2, response.getProcessedTickers());
        assertEquals(1, response.getUpdatedTickers());
        assertEquals(1, response.getUnresolvedTickersCount());
        assertEquals(1, response.getUpToDateTickers());
        assertEquals(0, response.getDeferredTickers());
        assertEquals(List.of("TSLA"), response.getSampleUnresolvedTickers());
        verify(stockPricesCache).clear();
        verify(companyProfilesCache).clear();
    }

    @Test
    @DisplayName("backfillMissingMarketCaps should respect the max ticker limit")
    void backfillMissingMarketCapsShouldRespectLimit() {
        when(companyMarketDataRepository.findByTickerIn(List.of("AAPL", "MSFT", "TSLA")))
                .thenReturn(List.of());
        doReturn(List.of(
                CompanyMarketData.builder()
                        .ticker("AAPL")
                        .marketCap(1d)
                        .marketCapSource(MarketCapSource.PROVIDER_MARKET_CAP)
                        .build()))
                .when(companyMarketDataService).fetchAndSaveQuotesBatch(List.of("AAPL"));

        MarketCapBackfillResponse response = companyMarketDataService.backfillMissingMarketCaps(
                List.of("AAPL", "MSFT", "TSLA"),
                5,
                1);

        assertEquals(3, response.getTrackedTickers());
        assertEquals(3, response.getCandidateTickers());
        assertEquals(1, response.getProcessedTickers());
        assertEquals(1, response.getUpdatedTickers());
        assertEquals(0, response.getUnresolvedTickersCount());
        assertEquals(2, response.getDeferredTickers());
        assertTrue(response.getSampleUnresolvedTickers().isEmpty());
    }

    @Test
    @DisplayName("resolveQuoteData should fall back to profile market cap and quote close when needed")
    void resolveQuoteDataShouldFallbackToProfileAndClose() {
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setPrice(BigDecimal.valueOf(98.25d));
        stockPrice.setClose(BigDecimal.valueOf(97.5d));
        stockPrice.setCurrency("USD");

        MarketDataProvider.CompanyProfile companyProfile = new MarketDataProvider.CompanyProfile();
        companyProfile.setMarketCapitalization(123_456_789L);
        companyProfile.setCurrency("USD");

        CompanyMarketDataServiceImpl.ResolvedQuoteData resolvedQuoteData =
                companyMarketDataService.resolveQuoteData("xyz", stockPrice, companyProfile);

        assertNotNull(resolvedQuoteData);
        assertEquals(98.25d, resolvedQuoteData.currentPrice());
        assertEquals(97.5d, resolvedQuoteData.previousClose());
        assertEquals(123_456_789d, resolvedQuoteData.marketCap());
        assertEquals("USD", resolvedQuoteData.currency());
        assertEquals("XYZ", resolvedQuoteData.ticker());
    }

    @Test
    @DisplayName("extractLatestSharesOutstanding should prefer the latest end date then latest filed date")
    void extractLatestSharesOutstandingShouldPreferLatestFact() {
        Long sharesOutstanding = companyMarketDataService.extractLatestSharesOutstanding("""
                {
                  "facts": {
                    "dei": {
                      "EntityCommonStockSharesOutstanding": {
                        "units": {
                          "shares": [
                            { "end": "2025-09-30", "filed": "2025-11-01", "val": 14840390000 },
                            { "end": "2025-12-31", "filed": "2026-01-30", "val": 14681140000 },
                            { "end": "2025-12-31", "filed": "2026-01-15", "val": 14690000000 }
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(14_681_140_000L, sharesOutstanding);
    }
}
