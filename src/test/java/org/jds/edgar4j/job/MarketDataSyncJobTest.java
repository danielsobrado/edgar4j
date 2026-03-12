package org.jds.edgar4j.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MarketDataSyncJobTest {

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private Sp500Service sp500Service;

    @Mock
    private Form4Repository form4Repository;

    @Test
    @DisplayName("syncMarketData should skip execution when disabled")
    void syncMarketDataShouldSkipWhenDisabled() {
        MarketDataSyncJob job = new MarketDataSyncJob(companyMarketDataService, sp500Service, form4Repository);
        ReflectionTestUtils.setField(job, "enabled", false);

        job.syncMarketData();

        verifyNoInteractions(companyMarketDataService, sp500Service, form4Repository);
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncMarketData should batch the union of S&P 500 and recent insider tickers")
    void syncMarketDataShouldBatchUnionOfTickerSources() {
        MarketDataSyncJob job = new MarketDataSyncJob(companyMarketDataService, sp500Service, form4Repository);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "batchSize", 2);

        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(form4Repository.findByTransactionDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                Form4.builder().tradingSymbol("TSLA").build(),
                Form4.builder().tradingSymbol("aapl").build(),
                Form4.builder().tradingSymbol(" ").build()));
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
}
