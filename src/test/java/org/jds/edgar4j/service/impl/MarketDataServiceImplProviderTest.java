package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.repository.AppSettingsRepository;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplProviderTest {

    @Mock
    private AppSettingsRepository appSettingsRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private TiingoEnvProperties tiingoEnvProperties;

    @Mock
    private org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;

    @Mock
    private MarketDataProviderSettingsResolver marketDataProviderSettingsResolver;

    @Test
    @DisplayName("getDailyPrices should delegate to the provider service for non-Tiingo providers")
    void getDailyPricesShouldDelegateToProviderService() {
        MarketDataServiceImpl marketDataService = new MarketDataServiceImpl(
                appSettingsRepository,
                settingsService,
                new ObjectMapper(),
                tiingoEnvProperties,
                providerMarketDataService,
                marketDataProviderSettingsResolver);
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setDate(LocalDate.of(2026, 3, 12));
        stockPrice.setOpen(BigDecimal.valueOf(180));
        stockPrice.setHigh(BigDecimal.valueOf(186));
        stockPrice.setLow(BigDecimal.valueOf(179));
        stockPrice.setClose(BigDecimal.valueOf(185));
        stockPrice.setVolume(1_250_000L);

        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(AppSettings.builder()
                .id("default")
                .marketDataProvider("YAHOOFINANCE")
                .build()));
        when(marketDataProviderSettingsResolver.resolveSelectedProvider(org.mockito.ArgumentMatchers.any(AppSettings.class)))
                .thenReturn("YAHOOFINANCE");
        when(providerMarketDataService.getHistoricalPrices(
                "AAPL",
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 12),
                "YAHOOFINANCE"))
                .thenReturn(CompletableFuture.completedFuture(List.of(stockPrice)));

        MarketDataResponse response = marketDataService.getDailyPrices(
                "aapl",
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 12));

        assertEquals("YAHOOFINANCE", response.getProvider());
        assertEquals(1, response.getPrices().size());
        assertEquals(185d, response.getPrices().get(0).getClose());
        verify(providerMarketDataService).getHistoricalPrices(
                "AAPL",
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 12),
                "YAHOOFINANCE");
    }
}
