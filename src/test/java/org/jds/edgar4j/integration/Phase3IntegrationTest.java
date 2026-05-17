package org.jds.edgar4j.integration;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.service.analytics.InsiderAnalyticsService;
import org.jds.edgar4j.service.enrichment.CompanyEnrichmentService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 3 market data and enrichment features
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "edgar4j.providers.cache.enabled=false",
        "edgar4j.providers.alpha-vantage.enabled=false",
        "edgar4j.providers.finnhub.enabled=false",
        "edgar4j.providers.yahoo-finance.enabled=false",
        "spring.cache.type=caffeine"
})
class Phase3IntegrationTest {

    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 1, 20);
    private static final BigDecimal TEST_PRICE = new BigDecimal("123.45");

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private CompanyEnrichmentService companyEnrichmentService;

    @Autowired
    private InsiderAnalyticsService insiderAnalyticsService;

    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
    }

    @Test
    void testMarketDataServiceAvailability() {
        assertNotNull(marketDataService, "MarketDataService should be available");

        Map<String, MarketDataService.ProviderStatus> providerStatus = marketDataService.getProviderStatus();

        assertNotNull(providerStatus, "Provider status should not be null");
        assertTrue(providerStatus.containsKey("DeterministicTestProvider"), "Test provider should be configured");
        assertTrue(providerStatus.get("DeterministicTestProvider").isAvailable(), "Test provider should be available");
    }

    @Test
    void testGetCurrentPriceForMajorStock() throws Exception {
        String symbol = "AAPL";

        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = marketDataService.getCurrentPrice(symbol);
        assertNotNull(priceFuture, "Price future should not be null");

        MarketDataProvider.StockPrice stockPrice = priceFuture.get();

        assertNotNull(stockPrice, "Stock price should be returned by the test provider");
        assertEquals(symbol, stockPrice.getSymbol(), "Symbol should match");
        assertEquals(0, TEST_PRICE.compareTo(stockPrice.getPrice()), "Price should match the deterministic provider");
        assertEquals(PRICE_DATE, stockPrice.getDate(), "Price date should match the deterministic provider");
    }

    @Test
    void testHistoricalPricesRetrieval() throws Exception {
        String symbol = "MSFT";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        CompletableFuture<List<MarketDataProvider.StockPrice>> pricesFuture =
                marketDataService.getHistoricalPrices(symbol, startDate, endDate);
        assertNotNull(pricesFuture, "Prices future should not be null");

        List<MarketDataProvider.StockPrice> prices = pricesFuture.get();
        assertEquals(1, prices.size(), "The test provider should return one historical price");

        MarketDataProvider.StockPrice firstPrice = prices.get(0);
        assertEquals(symbol, firstPrice.getSymbol(), "Symbol should match");
        assertEquals(startDate, firstPrice.getDate(), "Historical price date should match the requested start date");
        assertFalse(firstPrice.getDate().isBefore(startDate), "Price date should be >= start date");
        assertFalse(firstPrice.getDate().isAfter(endDate), "Price date should be <= end date");
    }

    @Test
    void testCompanyProfileRetrieval() throws Exception {
        String symbol = "GOOGL";

        CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture =
                marketDataService.getCompanyProfile(symbol);
        assertNotNull(profileFuture, "Profile future should not be null");

        MarketDataProvider.CompanyProfile profile = profileFuture.get();

        assertNotNull(profile, "Company profile should be returned by the test provider");
        assertEquals(symbol, profile.getSymbol(), "Symbol should match");
        assertEquals("GOOGL Test Company", profile.getName(), "Company name should match the test provider");
        assertEquals("Software", profile.getIndustry(), "Industry should match the test provider");
        assertEquals("Technology", profile.getSector(), "Sector should match the test provider");
    }

    @Test
    void testEnhancedMarketDataRetrieval() throws Exception {
        String symbol = "TSLA";

        CompletableFuture<MarketDataService.EnhancedMarketData> enhancedFuture =
                marketDataService.getEnhancedMarketData(symbol);
        assertNotNull(enhancedFuture, "Enhanced data future should not be null");

        MarketDataService.EnhancedMarketData enhancedData = enhancedFuture.get();

        assertNotNull(enhancedData, "Enhanced data should not be null");
        assertEquals(symbol, enhancedData.getSymbol(), "Symbol should match");
        assertTrue(enhancedData.hasPrice(), "Enhanced data should include a stock price");
        assertTrue(enhancedData.hasProfile(), "Enhanced data should include a company profile");
        assertTrue(enhancedData.hasMetrics(), "Enhanced data should include financial metrics");
    }

    @Test
    void testCompanyEnrichmentService() {
        Company testCompany = new Company();
        testCompany.setCik("0000789019");
        testCompany.setTickerSymbol("MSFT");
        testCompany.setCompanyName("Microsoft Corporation");

        Company savedCompany = companyRepository.save(testCompany);
        assertNotNull(savedCompany.getId(), "Company should be saved with ID");

        CompanyEnrichmentService.EnrichmentStatus statusBefore =
                companyEnrichmentService.getEnrichmentStatus(savedCompany.getCik());
        assertNotNull(statusBefore, "Enrichment status should not be null");
        assertFalse(statusBefore.isEnriched(), "Company should not be enriched initially");

        boolean needsEnrichment = companyEnrichmentService.needsEnrichment(savedCompany);
        assertTrue(needsEnrichment, "New company should need enrichment");
    }

    @Test
    void testCompanyEnrichmentProcess() throws Exception {
        Company testCompany = new Company();
        testCompany.setCik("0000320193");
        testCompany.setTickerSymbol("AAPL");
        testCompany.setCompanyName("Apple Inc.");

        Company savedCompany = companyRepository.save(testCompany);

        CompletableFuture<Company> enrichmentFuture =
                companyEnrichmentService.enrichCompanyData(savedCompany);
        assertNotNull(enrichmentFuture, "Enrichment future should not be null");

        Company enrichedCompany = enrichmentFuture.get();
        assertNotNull(enrichedCompany, "Enriched company should not be null");
        assertEquals(savedCompany.getId(), enrichedCompany.getId(), "Should be the same company");
        assertEquals(0, TEST_PRICE.compareTo(enrichedCompany.getCurrentStockPrice()), "Stock price should be enriched");
        assertEquals("Technology", enrichedCompany.getSector(), "Sector should be enriched");
        assertNotNull(enrichedCompany.getLastMarketDataUpdate(), "Last update should be populated");
        assertTrue(enrichedCompany.getLastMarketDataUpdate().isAfter(LocalDateTime.now().minusMinutes(1)),
                "Last update should be recent");
    }

    @Test
    void testPriceForDateRetrieval() throws Exception {
        String symbol = "AMZN";
        LocalDate testDate = LocalDate.now().minusDays(1);

        CompletableFuture<BigDecimal> priceFuture = marketDataService.getPriceForDate(symbol, testDate);
        assertNotNull(priceFuture, "Price future should not be null");

        BigDecimal price = priceFuture.get();

        assertNotNull(price, "Price should be returned by the test provider");
        assertTrue(price.compareTo(BigDecimal.ZERO) > 0, "Price should be positive");
    }

    @Test
    void testAnalyticsServiceAvailability() {
        assertNotNull(insiderAnalyticsService, "InsiderAnalyticsService should be available");
    }

    @Test
    void testRepeatedMarketDataCallsAreStable() throws Exception {
        String symbol = "NFLX";

        MarketDataProvider.StockPrice firstCall = marketDataService.getCurrentPrice(symbol).get();
        MarketDataProvider.StockPrice secondCall = marketDataService.getCurrentPrice(symbol).get();

        assertNotNull(firstCall, "First call should return stock data");
        assertNotNull(secondCall, "Second call should return stock data");
        assertEquals(firstCall.getSymbol(), secondCall.getSymbol(), "Repeated calls should return the same symbol");
        assertEquals(0, firstCall.getPrice().compareTo(secondCall.getPrice()), "Repeated calls should return the same price");
    }

    @Test
    void testErrorHandlingForInvalidSymbol() throws Exception {
        String invalidSymbol = "INVALID_SYMBOL_TEST_123";

        CompletableFuture<MarketDataProvider.StockPrice> priceFuture =
                marketDataService.getCurrentPrice(invalidSymbol);
        assertNotNull(priceFuture, "Price future should not be null even for invalid symbol");

        assertNull(priceFuture.get(), "Invalid symbols should return no price data");
    }

    @Test
    void testMultipleProviderFailover() {
        assertDoesNotThrow(() -> {
            MarketDataProvider.StockPrice result = marketDataService.getCurrentPrice("TEST_SYMBOL").get();
            assertNull(result, "Provider failures should resolve to no price data when no fallback succeeds");
        }, "Service should handle provider failures gracefully");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicMarketDataProviderConfig {

        @Bean
        MarketDataProvider deterministicMarketDataProvider() {
            return new MarketDataProvider() {
                @Override
                public String getProviderName() {
                    return "DeterministicTestProvider";
                }

                @Override
                public int getPriority() {
                    return 0;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public CompletableFuture<StockPrice> getCurrentPrice(String symbol) {
                    if (isInvalidSymbol(symbol)) {
                        return CompletableFuture.completedFuture(null);
                    }

                    StockPrice price = new StockPrice(symbol, TEST_PRICE, PRICE_DATE);
                    price.setVolume(1_000_000L);
                    price.setCurrency("USD");
                    price.setExchange("NASDAQ");
                    return CompletableFuture.completedFuture(price);
                }

                @Override
                public CompletableFuture<List<StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
                    if (isInvalidSymbol(symbol)) {
                        return CompletableFuture.completedFuture(List.of());
                    }

                    StockPrice price = new StockPrice(symbol, TEST_PRICE, startDate);
                    price.setCurrency("USD");
                    price.setExchange("NASDAQ");
                    return CompletableFuture.completedFuture(List.of(price));
                }

                @Override
                public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
                    if (isInvalidSymbol(symbol)) {
                        return CompletableFuture.completedFuture(null);
                    }

                    CompanyProfile profile = new CompanyProfile();
                    profile.setSymbol(symbol);
                    profile.setName(symbol + " Test Company");
                    profile.setIndustry("Software");
                    profile.setSector("Technology");
                    profile.setCountry("United States");
                    profile.setCurrency("USD");
                    profile.setExchange("NASDAQ");
                    profile.setMarketCapitalization(500_000_000L);
                    profile.setSharesOutstanding(4_000_000L);
                    profile.setWebsite("https://example.test/" + symbol.toLowerCase());
                    return CompletableFuture.completedFuture(profile);
                }

                @Override
                public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
                    if (isInvalidSymbol(symbol)) {
                        return CompletableFuture.completedFuture(null);
                    }

                    FinancialMetrics metrics = new FinancialMetrics();
                    metrics.setSymbol(symbol);
                    metrics.setPeRatio(new BigDecimal("25.10"));
                    metrics.setPriceToBook(new BigDecimal("8.20"));
                    metrics.setBeta(new BigDecimal("1.15"));
                    metrics.setDividendYield(new BigDecimal("0.55"));
                    metrics.setFiftyTwoWeekHigh(new BigDecimal("150.00"));
                    metrics.setFiftyTwoWeekLow(new BigDecimal("90.00"));
                    return CompletableFuture.completedFuture(metrics);
                }

                private boolean isInvalidSymbol(String symbol) {
                    return symbol == null || symbol.startsWith("INVALID") || symbol.startsWith("TEST_");
                }
            };
        }
    }
}
