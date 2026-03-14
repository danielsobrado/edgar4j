package org.jds.edgar4j.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataSyncJobTest {

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private Sp500Service sp500Service;

    @Mock
    private Form4DataPort form4Repository;

    @Test
    @DisplayName("syncMarketData should skip execution when disabled")
    void syncMarketDataShouldSkipWhenDisabled() {
                MarketDataSyncJob job = new MarketDataSyncJob(companyMarketDataService, sp500Service, form4Repository, false, 50);

        job.syncMarketData();

        verifyNoInteractions(companyMarketDataService, sp500Service, form4Repository);
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncMarketData should batch the union of S&P 500 and recent insider tickers")
    void syncMarketDataShouldBatchUnionOfTickerSources() {
                MarketDataSyncJob job = new MarketDataSyncJob(companyMarketDataService, sp500Service, form4Repository, true, 2);

        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(
                Form4.builder()
                        .tradingSymbol("TSLA")
                        .transactionDate(LocalDate.now().minusDays(60))
                        .transactions(List.of(Form4Transaction.builder()
                                .transactionCode("P")
                                .acquiredDisposedCode("A")
                                .transactionDate(LocalDate.now().minusDays(2))
                                .build()))
                        .build(),
                Form4.builder()
                        .tradingSymbol("aapl")
                        .transactionDate(LocalDate.now().minusDays(1))
                        .acquiredDisposedCode("A")
                        .build(),
                Form4.builder().tradingSymbol(" ").transactionDate(LocalDate.now().minusDays(1)).build()));
        when(companyMarketDataService.fetchAndSaveQuotesBatch(any(List.class))).thenReturn(List.of(
                CompanyMarketData.builder().ticker("AAPL").build()));

        job.syncMarketData();

        ArgumentCaptor<List<String>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(companyMarketDataService, times(2)).fetchAndSaveQuotesBatch(batchCaptor.capture());
        List<List<String>> batches = batchCaptor.getAllValues();

        assertEquals(2, batches.size());
        assertTrue(batches.stream().allMatch(batch -> batch.size() <= 2));
        assertEquals(Set.of("AAPL", "MSFT", "TSLA"),
                batches.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet()));
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("triggerMarketCapBackfill should delegate tracked tickers to the company market-data service")
    void triggerMarketCapBackfillShouldDelegateTrackedTickers() {
        MarketDataSyncJob job = new MarketDataSyncJob(companyMarketDataService, sp500Service, form4Repository, true, 2);
        MarketCapBackfillResponse expectedResponse = MarketCapBackfillResponse.builder()
                .trackedTickers(3)
                .candidateTickers(2)
                .processedTickers(2)
                .updatedTickers(1)
                .build();

        when(sp500Service.getAllTickers()).thenReturn(Set.of("MSFT", "AAPL"));
        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(
                Form4.builder()
                        .tradingSymbol("tsla")
                        .transactions(List.of(Form4Transaction.builder()
                                .transactionCode("P")
                                .acquiredDisposedCode("A")
                                .transactionDate(LocalDate.now().minusDays(1))
                                .build()))
                        .build()));
        when(companyMarketDataService.backfillMissingMarketCaps(List.of("AAPL", "MSFT", "TSLA"), 2, 25))
                .thenReturn(expectedResponse);

        MarketCapBackfillResponse actualResponse = job.triggerMarketCapBackfill(25, 30);

        assertSame(expectedResponse, actualResponse);
        verify(companyMarketDataService).backfillMissingMarketCaps(List.of("AAPL", "MSFT", "TSLA"), 2, 25);
        assertFalse(job.isRunning());
    }
}
