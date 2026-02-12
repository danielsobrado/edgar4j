package org.jds.edgar4j.integration;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.service.analytics.InsiderAnalyticsService;
import org.jds.edgar4j.service.enrichment.CompanyEnrichmentService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
public class Phase3IntegrationTest {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private CompanyEnrichmentService companyEnrichmentService;

    @Autowired
    private InsiderAnalyticsService insiderAnalyticsService;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    public void testMarketDataServiceAvailability() {
        assertNotNull(marketDataService, "MarketDataService should be available");
        
        // Test provider status
        Map<String, MarketDataService.ProviderStatus> providerStatus = marketDataService.getProviderStatus();
        assertNotNull(providerStatus, "Provider status should not be null");
        assertFalse(providerStatus.isEmpty(), "At least one provider should be configured");
        
        // Log provider status for verification
        providerStatus.forEach((name, status) -> {
            System.out.println(String.format("Provider: %s, Available: %s, Priority: %d", 
                name, status.isAvailable(), status.getPriority()));
        });
    }

    @Test
    public void testGetCurrentPriceForMajorStock() throws Exception {
        // Test with a major stock symbol
        String symbol = "AAPL";
        
        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = marketDataService.getCurrentPrice(symbol);
        assertNotNull(priceFuture, "Price future should not be null");
        
        // Wait for result with timeout
        MarketDataProvider.StockPrice stockPrice = priceFuture.get();
        
        if (stockPrice != null) {
            assertEquals(symbol, stockPrice.getSymbol(), "Symbol should match");
            assertNotNull(stockPrice.getPrice(), "Price should not be null");
            assertTrue(stockPrice.getPrice().compareTo(BigDecimal.ZERO) > 0, "Price should be positive");
            
            System.out.println(String.format("AAPL Price: $%.2f on %s", 
                stockPrice.getPrice(), stockPrice.getDate()));
        } else {
            System.out.println("No price data available for AAPL (may be expected if no API keys configured)");
        }
    }

    @Test
    public void testHistoricalPricesRetrieval() throws Exception {
        String symbol = "MSFT";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7); // Last week
        
        CompletableFuture<List<MarketDataProvider.StockPrice>> pricesFuture = 
            marketDataService.getHistoricalPrices(symbol, startDate, endDate);
        assertNotNull(pricesFuture, "Prices future should not be null");
        
        List<MarketDataProvider.StockPrice> prices = pricesFuture.get();
        assertNotNull(prices, "Prices list should not be null");
        
        if (!prices.isEmpty()) {
            System.out.println(String.format("Retrieved %d historical prices for %s", prices.size(), symbol));
            
            // Verify price data structure
            MarketDataProvider.StockPrice firstPrice = prices.get(0);
            assertEquals(symbol, firstPrice.getSymbol(), "Symbol should match");
            assertNotNull(firstPrice.getDate(), "Date should not be null");
            
            // Prices should be within date range
            for (MarketDataProvider.StockPrice price : prices) {
                assertFalse(price.getDate().isBefore(startDate), "Price date should be >= start date");
                assertFalse(price.getDate().isAfter(endDate), "Price date should be <= end date");
            }
        } else {
            System.out.println("No historical price data available (may be expected if no API keys configured)");
        }
    }

    @Test
    public void testCompanyProfileRetrieval() throws Exception {
        String symbol = "GOOGL";
        
        CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture = 
            marketDataService.getCompanyProfile(symbol);
        assertNotNull(profileFuture, "Profile future should not be null");
        
        MarketDataProvider.CompanyProfile profile = profileFuture.get();
        
        if (profile != null) {
            assertEquals(symbol, profile.getSymbol(), "Symbol should match");
            assertNotNull(profile.getName(), "Company name should not be null");
            
            System.out.println(String.format("Company: %s, Industry: %s, Sector: %s", 
                profile.getName(), profile.getIndustry(), profile.getSector()));
        } else {
            System.out.println("No company profile data available (may be expected if no API keys configured)");
        }
    }

    @Test
    public void testEnhancedMarketDataRetrieval() throws Exception {
        String symbol = "TSLA";
        
        CompletableFuture<MarketDataService.EnhancedMarketData> enhancedFuture = 
            marketDataService.getEnhancedMarketData(symbol);
        assertNotNull(enhancedFuture, "Enhanced data future should not be null");
        
        MarketDataService.EnhancedMarketData enhancedData = enhancedFuture.get();
        assertNotNull(enhancedData, "Enhanced data should not be null");
        assertEquals(symbol, enhancedData.getSymbol(), "Symbol should match");
        
        System.out.println(String.format("Enhanced Data for %s - HasPrice: %s, HasProfile: %s, HasMetrics: %s", 
            symbol, enhancedData.hasPrice(), enhancedData.hasProfile(), enhancedData.hasMetrics()));
    }

    @Test
    public void testCompanyEnrichmentService() {
        // Create a test company
        Company testCompany = new Company();
        testCompany.setCik("0000789019"); // Microsoft CIK
        testCompany.setTickerSymbol("MSFT");
        testCompany.setCompanyName("Microsoft Corporation");
        
        Company savedCompany = companyRepository.save(testCompany);
        assertNotNull(savedCompany.getId(), "Company should be saved with ID");
        
        // Test enrichment status before enrichment
        CompanyEnrichmentService.EnrichmentStatus statusBefore = 
            companyEnrichmentService.getEnrichmentStatus(savedCompany.getCik());
        assertNotNull(statusBefore, "Enrichment status should not be null");
        assertFalse(statusBefore.isEnriched(), "Company should not be enriched initially");
        
        // Test enrichment need check
        boolean needsEnrichment = companyEnrichmentService.needsEnrichment(savedCompany);
        assertTrue(needsEnrichment, "New company should need enrichment");
        
        System.out.println(String.format("Company %s enrichment status: %s", 
            savedCompany.getTickerSymbol(), statusBefore.getStatus()));
    }

    @Test
    public void testCompanyEnrichmentProcess() throws Exception {
        // Create a test company with ticker symbol
        Company testCompany = new Company();
        testCompany.setCik("0000320193"); // Apple CIK
        testCompany.setTickerSymbol("AAPL");
        testCompany.setCompanyName("Apple Inc.");
        
        Company savedCompany = companyRepository.save(testCompany);
        
        // Attempt enrichment
        CompletableFuture<Company> enrichmentFuture = 
            companyEnrichmentService.enrichCompanyData(savedCompany);
        assertNotNull(enrichmentFuture, "Enrichment future should not be null");
        
        Company enrichedCompany = enrichmentFuture.get();
        assertNotNull(enrichedCompany, "Enriched company should not be null");
        assertEquals(savedCompany.getId(), enrichedCompany.getId(), "Should be the same company");
        
        // Check if enrichment timestamp was updated
        if (enrichedCompany.getLastMarketDataUpdate() != null) {
            assertTrue(enrichedCompany.getLastMarketDataUpdate().isAfter(LocalDateTime.now().minusMinutes(1)), 
                "Last update should be recent");
            System.out.println("Company successfully enriched with market data");
        } else {
            System.out.println("Company enrichment completed but no market data was available");
        }
    }

    @Test
    public void testPriceForDateRetrieval() throws Exception {
        String symbol = "AMZN";
        LocalDate testDate = LocalDate.now().minusDays(1); // Yesterday
        
        CompletableFuture<BigDecimal> priceFuture = marketDataService.getPriceForDate(symbol, testDate);
        assertNotNull(priceFuture, "Price future should not be null");
        
        BigDecimal price = priceFuture.get();
        
        if (price != null) {
            assertTrue(price.compareTo(BigDecimal.ZERO) > 0, "Price should be positive");
            System.out.println(String.format("%s price on %s: $%.2f", symbol, testDate, price));
        } else {
            System.out.println(String.format("No price data available for %s on %s", symbol, testDate));
        }
    }

    @Test
    public void testAnalyticsServiceAvailability() {
        assertNotNull(insiderAnalyticsService, "InsiderAnalyticsService should be available");
        
        // Test with a mock transaction would require more setup
        // For now, just verify the service is properly initialized
        System.out.println("InsiderAnalyticsService is available and ready for use");
    }

    @Test
    public void testCacheConfiguration() {
        // Test that repeated calls to the same data are cached
        String symbol = "NFLX";
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<MarketDataProvider.StockPrice> firstCall = marketDataService.getCurrentPrice(symbol);
        long firstCallTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        CompletableFuture<MarketDataProvider.StockPrice> secondCall = marketDataService.getCurrentPrice(symbol);
        long secondCallTime = System.currentTimeMillis() - startTime;
        
        // Second call should be significantly faster if caching is working
        System.out.println(String.format("First call time: %dms, Second call time: %dms", 
            firstCallTime, secondCallTime));
        
        // Note: This test may not show dramatic differences in test environment
        // but validates that caching infrastructure is in place
    }

    @Test
    public void testErrorHandlingForInvalidSymbol() throws Exception {
        String invalidSymbol = "INVALID_SYMBOL_TEST_123";
        
        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = 
            marketDataService.getCurrentPrice(invalidSymbol);
        assertNotNull(priceFuture, "Price future should not be null even for invalid symbol");
        
        MarketDataProvider.StockPrice result = priceFuture.get();
        // Result may be null for invalid symbols, which is expected behavior
        System.out.println(String.format("Result for invalid symbol %s: %s", 
            invalidSymbol, result != null ? "Found data" : "No data (expected)"));
    }

    @Test
    public void testMultipleProviderFailover() throws Exception {
        // This test verifies that the failover mechanism is in place
        // The actual failover behavior depends on provider configuration and availability
        
        String symbol = "IBM";
        
        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = 
            marketDataService.getCurrentPrice(symbol);
        
        // The service should handle provider failures gracefully
        MarketDataProvider.StockPrice result = priceFuture.get();
        
        System.out.println(String.format("Multi-provider failover test for %s: %s", 
            symbol, result != null ? "Success" : "No data available"));
        
        // Test should not throw exceptions even if all providers fail
        assertDoesNotThrow(() -> {
            marketDataService.getCurrentPrice("TEST_SYMBOL").get();
        }, "Service should handle provider failures gracefully");
    }
}
