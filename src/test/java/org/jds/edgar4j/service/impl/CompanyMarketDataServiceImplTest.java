package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.repository.CompanyMarketDataRepository;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.service.MarketDataService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyMarketDataServiceImplTest {

    @Mock
    private CompanyMarketDataRepository companyMarketDataRepository;

    @Mock
    private CompanyTickerRepository companyTickerRepository;

    @Mock
    private MarketDataService historicalMarketDataService;

    @Mock
    private org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;

    private CompanyMarketDataServiceImpl companyMarketDataService;

    @BeforeEach
    void setUp() {
        companyMarketDataService = org.mockito.Mockito.spy(new CompanyMarketDataServiceImpl(
                companyMarketDataRepository,
                companyTickerRepository,
                historicalMarketDataService,
                providerMarketDataService));
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
        when(companyTickerRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(
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
                Arrays.asList(" aapl ", "AAPL", null, "", "msft"));

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
}
