package org.jds.edgar4j.service.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private MarketDataProvider yahooFinanceProvider;

    @Mock
    private MarketDataProvider finnhubProvider;

    @Mock
    private MarketDataProviderSettingsResolver settingsResolver;

    @Test
    @DisplayName("getHistoricalPrices should try the preferred provider before lower-priority providers")
    void getHistoricalPricesShouldPreferRequestedProvider() {
        MarketDataService marketDataService = new MarketDataService(
                List.of(yahooFinanceProvider, finnhubProvider),
                new MarketDataProviderProperties(),
                settingsResolver);
        MarketDataProvider.StockPrice finnhubPrice = new MarketDataProvider.StockPrice("AAPL", BigDecimal.valueOf(185.12), LocalDate.of(2026, 3, 12));

        when(yahooFinanceProvider.getProviderName()).thenReturn("YahooFinance");
        when(yahooFinanceProvider.isAvailable()).thenReturn(true);
        when(finnhubProvider.getProviderName()).thenReturn("Finnhub");
        when(finnhubProvider.isAvailable()).thenReturn(true);
        when(finnhubProvider.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(finnhubPrice)));

        List<MarketDataProvider.StockPrice> prices = marketDataService
                .getHistoricalPrices("AAPL", LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12), "FINNHUB")
                .join();

        assertEquals(1, prices.size());
        assertEquals(BigDecimal.valueOf(185.12), prices.get(0).getPrice());
        verify(finnhubProvider).getHistoricalPrices("AAPL", LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12));
        verify(yahooFinanceProvider, never()).getHistoricalPrices(any(), any(), any());
    }

    @Test
    @DisplayName("getHistoricalPrices should fall back to provider priority when the preferred provider is unknown")
    void getHistoricalPricesShouldFallBackToPriorityOrdering() {
        MarketDataService marketDataService = new MarketDataService(
                List.of(yahooFinanceProvider, finnhubProvider),
                new MarketDataProviderProperties(),
                settingsResolver);
        MarketDataProvider.StockPrice yahooPrice = new MarketDataProvider.StockPrice("AAPL", BigDecimal.valueOf(184.01), LocalDate.of(2026, 3, 11));

        when(yahooFinanceProvider.getProviderName()).thenReturn("YahooFinance");
        when(yahooFinanceProvider.getPriority()).thenReturn(1);
        when(yahooFinanceProvider.isAvailable()).thenReturn(true);
        when(finnhubProvider.getProviderName()).thenReturn("Finnhub");
        when(finnhubProvider.getPriority()).thenReturn(2);
        when(finnhubProvider.isAvailable()).thenReturn(true);
        when(yahooFinanceProvider.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(yahooPrice)));

        List<MarketDataProvider.StockPrice> prices = marketDataService
                .getHistoricalPrices("AAPL", LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 11), "UNKNOWN")
                .join();

        assertEquals(1, prices.size());
        assertEquals(BigDecimal.valueOf(184.01), prices.get(0).getPrice());
        verify(yahooFinanceProvider).getHistoricalPrices("AAPL", LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 11));
        verify(finnhubProvider, never()).getHistoricalPrices(any(), any(), any());
    }

    @Test
    @DisplayName("getCurrentPrice should prefer the selected provider before falling back")
    void getCurrentPriceShouldPreferSelectedProvider() {
        MarketDataService marketDataService = new MarketDataService(
                List.of(yahooFinanceProvider, finnhubProvider),
                new MarketDataProviderProperties(),
                settingsResolver);
        MarketDataProvider.StockPrice finnhubPrice = new MarketDataProvider.StockPrice(
                "AAPL",
                BigDecimal.valueOf(186.23),
                LocalDate.of(2026, 3, 12));

        when(settingsResolver.resolvePreferredProviderName()).thenReturn("FINNHUB");
        when(yahooFinanceProvider.getProviderName()).thenReturn("YahooFinance");
        when(yahooFinanceProvider.isAvailable()).thenReturn(true);
        when(finnhubProvider.getProviderName()).thenReturn("Finnhub");
        when(finnhubProvider.isAvailable()).thenReturn(true);
        when(finnhubProvider.getCurrentPrice("AAPL")).thenReturn(CompletableFuture.completedFuture(finnhubPrice));

        MarketDataProvider.StockPrice price = marketDataService.getCurrentPrice("AAPL").join();

        assertEquals(BigDecimal.valueOf(186.23), price.getPrice());
        verify(finnhubProvider).getCurrentPrice("AAPL");
        verify(yahooFinanceProvider, never()).getCurrentPrice(any());
    }
}
