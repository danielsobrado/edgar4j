package org.jds.edgar4j.service;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.service.impl.DownloadTickersServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
class DownloadTickersServiceTests {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser responseParser;

    @Mock
    private TickerRepository tickerRepository;

    @InjectMocks
    private DownloadTickersServiceImpl downloadTickersService;

    @DisplayName("Should download and save parsed company tickers")
    @Test
    void testDownloadTickers_SavesParsedTickers() {
        // Given
        String jsonResponse = "{\"0\":{\"ticker\":\"MSFT\"}}";
        List<Ticker> parsedTickers = List.of(
                Ticker.builder().code("MSFT").name("Microsoft Corporation").cik("0000789019").build());

        when(secApiClient.fetchCompanyTickers()).thenReturn(jsonResponse);
        when(responseParser.parseTickersJson(jsonResponse)).thenReturn(parsedTickers);
        when(tickerRepository.findByCodeIn(List.of("MSFT"))).thenReturn(List.of());
        
        // When
        downloadTickersService.downloadTickers();

        // Then
        verify(tickerRepository).saveAll(parsedTickers);
    }

    @DisplayName("Should download and save exchange tickers")
    @Test
    void testDownloadTickersExchanges_SavesParsedTickers() {
        // Given
        String jsonResponse = "{\"data\":[]}";
        List<Ticker> parsedTickers = List.of(
                Ticker.builder().code("MSFT").name("Microsoft Corporation").cik("0000789019").build());

        when(secApiClient.fetchCompanyTickersExchanges()).thenReturn(jsonResponse);
        when(responseParser.parseTickersExchangeJson(jsonResponse)).thenReturn(parsedTickers);
        when(tickerRepository.findByCodeIn(List.of("MSFT"))).thenReturn(List.of());
        
        // When
        downloadTickersService.downloadTickersExchanges();

        // Then
        verify(tickerRepository).saveAll(parsedTickers);
    }

    @DisplayName("Should download and save mutual fund tickers")
    @Test
    void testDownloadTickersMFs_SavesParsedTickers() {
        // Given
        String jsonResponse = "{\"0\":{\"ticker\":\"VFINX\"}}";
        List<Ticker> parsedTickers = List.of(
                Ticker.builder().code("VFINX").name("Vanguard 500 Index Fund").cik("0000000001").build());

        when(secApiClient.fetchCompanyTickersMutualFunds()).thenReturn(jsonResponse);
        when(responseParser.parseTickersJson(jsonResponse)).thenReturn(parsedTickers);
        when(tickerRepository.findByCodeIn(List.of("VFINX"))).thenReturn(List.of());
        
        // When
        downloadTickersService.downloadTickersMFs();

        // Then
        verify(tickerRepository).saveAll(parsedTickers);
    }

    @DisplayName("Should reuse existing ticker ids before saving")
    @Test
    void testDownloadTickers_ReusesExistingTickerIds() {
        // Given
        String jsonResponse = "{\"0\":{\"ticker\":\"MSFT\"}}";
        Ticker parsedTicker = Ticker.builder().code("MSFT").name("Microsoft Corporation").cik("0000789019").build();
        Ticker existingTicker = Ticker.builder().id("ticker-123").code("MSFT").build();

        when(secApiClient.fetchCompanyTickers()).thenReturn(jsonResponse);
        when(responseParser.parseTickersJson(jsonResponse)).thenReturn(List.of(parsedTicker));
        when(tickerRepository.findByCodeIn(List.of("MSFT"))).thenReturn(List.of(existingTicker));
        
        // When
        downloadTickersService.downloadTickers();

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticker>> tickerCaptor = ArgumentCaptor.forClass(List.class);
        verify(tickerRepository).saveAll(tickerCaptor.capture());
        assertEquals("ticker-123", tickerCaptor.getValue().get(0).getId());
    }
}
