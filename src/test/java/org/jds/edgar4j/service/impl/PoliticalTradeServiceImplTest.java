package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import org.jds.edgar4j.exception.PoliticalTradeSyncException;
import org.jds.edgar4j.integration.PoliticalTradeSource;
import org.jds.edgar4j.integration.PoliticalTradeSourceRequest;
import org.jds.edgar4j.model.PoliticalTrade;
import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PoliticalTradeServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-21T10:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private PoliticalTradeDataPort dataPort;

    @Mock
    private PoliticalTradeSource source;

    private PoliticalTradeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PoliticalTradeServiceImpl(dataPort, source, new ObjectMapper().findAndRegisterModules(), FIXED_CLOCK);
    }

    @Test
    void screenDefaultsToStockAndSortsByDisclosureDateDescending() {
        PoliticalTrade oldStock = trade("1", "Josh Gottheimer", "MLM", "stock", "SELL", 15_000d, 50_000d, LocalDate.of(2026, 5, 20));
        PoliticalTrade newestStock = trade("2", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 21));
        PoliticalTrade crypto = trade("3", "Tim Moore", null, "crypto", "BUY", 1_000d, 15_000d, LocalDate.of(2026, 5, 22));
        when(dataPort.findAll()).thenReturn(List.of(oldStock, newestStock, crypto));

        PaginatedResponse<PoliticalTradeResponse> result = service.screen(PoliticalTradeScreenRequest.builder()
                .page(0)
                .size(50)
                .build());

        assertEquals(2, result.getTotalElements());
        assertEquals("T", result.getContent().get(0).getTicker());
        assertEquals("MLM", result.getContent().get(1).getTicker());
    }

    @Test
    void screenSupportsFiltersAndAmountRangeOverlap() {
        PoliticalTrade match = trade("1", "Josh Gottheimer", "MLM", "stock", "SELL", 15_000d, 50_000d, LocalDate.of(2026, 5, 20));
        match.setParty("Democrat");
        match.setChamber("House");
        match.setState("NJ");
        match.setIssuerName("Martin Marietta Materials");
        match.setOwner("Spouse");
        match.setTradedDate(LocalDate.of(2026, 4, 9));
        PoliticalTrade miss = trade("2", "Tim Moore", "T", "stock", "BUY", 1_000d, 15_000d, LocalDate.of(2026, 5, 20));
        when(dataPort.findAll()).thenReturn(List.of(match, miss));

        PaginatedResponse<PoliticalTradeResponse> result = service.screen(PoliticalTradeScreenRequest.builder()
                .politician("gott")
                .ticker("$MLM:US")
                .issuer("marietta")
                .party("Democrat")
                .chamber("House")
                .state("NJ")
                .transactionType("sell")
                .owner("spouse")
                .tradedDateFrom(LocalDate.of(2026, 4, 1))
                .tradedDateTo(LocalDate.of(2026, 4, 30))
                .minAmount(20_000d)
                .maxAmount(60_000d)
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        assertEquals("Josh Gottheimer", result.getContent().get(0).getPoliticianName());
    }

    @Test
    void exportSupportsCsvAndJson() {
        when(dataPort.findAll()).thenReturn(List.of(trade("1", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 21))));

        String csv = new String(service.export(PoliticalTradeScreenRequest.builder().build(), "CSV"));
        String json = new String(service.export(PoliticalTradeScreenRequest.builder().build(), "JSON"));

        assertTrue(csv.contains("politicianName"));
        assertTrue(csv.contains("Tim Moore"));
        assertTrue(json.contains("\"politicianName\":\"Tim Moore\""));
    }

    @Test
    void syncInsertsNewRowsWithStableIdsAndDefaults() {
        PoliticalTrade fetched = trade("20003798315", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 21));
        fetched.setSourceTradeId("CAPITOL_TRADES:20003798315");
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of(fetched));
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");
        when(dataPort.findBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(Optional.empty());
        when(dataPort.existsBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(false);
        when(dataPort.count()).thenReturn(1L);

        PoliticalTradeSyncResponse response = service.sync(PoliticalTradeSyncRequest.builder().build());

        ArgumentCaptor<PoliticalTradeSourceRequest> requestCaptor = ArgumentCaptor.forClass(PoliticalTradeSourceRequest.class);
        verify(source).fetch(requestCaptor.capture());
        assertEquals("stock", requestCaptor.getValue().assetType());
        assertEquals(25, requestCaptor.getValue().maxPages());
        assertEquals(5, requestCaptor.getValue().chunkPages());
        assertEquals(2, requestCaptor.getValue().pauseSeconds());

        ArgumentCaptor<PoliticalTrade> savedCaptor = ArgumentCaptor.forClass(PoliticalTrade.class);
        verify(dataPort).save(savedCaptor.capture());
        assertEquals("CAPITOL_TRADES-20003798315", savedCaptor.getValue().getId());
        assertEquals(Instant.parse("2026-05-21T10:00:00Z"), savedCaptor.getValue().getFirstSeenAt());
        assertEquals(1, response.getInsertedRows());
        assertEquals(0, response.getUpdatedRows());
        assertEquals(0L, response.getDurationMillis());
    }

    @Test
    void syncUpdatesExistingRowsAndCapsMaxPages() {
        PoliticalTrade existing = trade("20003798315", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 20));
        existing.setId("CAPITOL_TRADES-20003798315");
        existing.setSourceTradeId("CAPITOL_TRADES:20003798315");
        existing.setFirstSeenAt(Instant.parse("2026-05-20T10:00:00Z"));
        PoliticalTrade fetched = trade("20003798315", "Tim Moore", "T", "stock", "SELL", 15_000d, 50_000d, LocalDate.of(2026, 5, 21));
        fetched.setSourceTradeId("CAPITOL_TRADES:20003798315");

        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of(fetched));
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");
        when(dataPort.findBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(Optional.of(existing));
        when(dataPort.existsBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(true);

        PoliticalTradeSyncResponse response = service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(9999)
                .force(true)
                .build());

        ArgumentCaptor<PoliticalTradeSourceRequest> requestCaptor = ArgumentCaptor.forClass(PoliticalTradeSourceRequest.class);
        verify(source).fetch(requestCaptor.capture());
        assertEquals("stock", requestCaptor.getValue().assetType());
        assertEquals(5000, requestCaptor.getValue().maxPages());
        assertEquals(5, requestCaptor.getValue().chunkPages());
        assertEquals(2, requestCaptor.getValue().pauseSeconds());

        ArgumentCaptor<PoliticalTrade> savedCaptor = ArgumentCaptor.forClass(PoliticalTrade.class);
        verify(dataPort).save(savedCaptor.capture());
        assertEquals("SELL", savedCaptor.getValue().getTransactionType());
        assertEquals(Instant.parse("2026-05-20T10:00:00Z"), savedCaptor.getValue().getFirstSeenAt());
        assertEquals(1, response.getUpdatedRows());
        assertEquals(0, response.getInsertedRows());
    }

    @Test
    void syncAllAssetTypesFetchesEachSupportedTypeSeparately() {
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of());
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");

        PoliticalTradeSyncResponse response = service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("ALL")
                .maxPages(2)
                .force(true)
                .build());

        ArgumentCaptor<PoliticalTradeSourceRequest> requestCaptor = ArgumentCaptor.forClass(PoliticalTradeSourceRequest.class);
        verify(source, times(5)).fetch(requestCaptor.capture());
        assertEquals(List.of("stock", "etf", "mutual-fund", "crypto", "corporate-bond"),
                requestCaptor.getAllValues().stream().map(PoliticalTradeSourceRequest::assetType).toList());
        assertEquals(List.of(2, 2, 2, 2, 2),
                requestCaptor.getAllValues().stream().map(PoliticalTradeSourceRequest::maxPages).toList());
        assertEquals("all", response.getAssetType());
        assertEquals(10, response.getFetchedPages());
    }

    @Test
    void syncPassesClampedThrottleOptionsToSource() {
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of());
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");

        service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .chunkPages(999)
                .pauseSeconds(999)
                .force(true)
                .build());

        ArgumentCaptor<PoliticalTradeSourceRequest> requestCaptor = ArgumentCaptor.forClass(PoliticalTradeSourceRequest.class);
        verify(source).fetch(requestCaptor.capture());
        assertEquals(50, requestCaptor.getValue().chunkPages());
        assertEquals(60, requestCaptor.getValue().pauseSeconds());
    }

    @Test
    void syncSkipsRowsOutsideRequestedDisclosureDateRange() {
        PoliticalTrade inRange = trade("20003798315", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 30));
        inRange.setSourceTradeId("CAPITOL_TRADES:20003798315");
        PoliticalTrade outOfRange = trade("20003798316", "Tim Moore", "MSFT", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 20));
        outOfRange.setSourceTradeId("CAPITOL_TRADES:20003798316");

        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of(inRange, outOfRange));
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");
        when(dataPort.findBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(Optional.empty());
        when(dataPort.existsBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(false);

        PoliticalTradeSyncResponse response = service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .disclosureDateFrom(LocalDate.of(2026, 5, 29))
                .disclosureDateTo(LocalDate.of(2026, 5, 31))
                .force(true)
                .build());

        verify(dataPort).save(inRange);
        assertEquals(2, response.getFetchedRows());
        assertEquals(1, response.getInsertedRows());
        assertEquals(1, response.getSkippedRows());
    }

    @Test
    void syncRejectsUnsupportedAssetTypesBeforeFetching() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("warrant")
                .build()));

        assertTrue(ex.getMessage().contains("Unsupported political trade asset type"));
        verifyNoInteractions(source);
    }

    @Test
    void syncRequiresForceForBackfillsOverDefaultLimit() {
        PoliticalTradeSyncException ex = assertThrows(PoliticalTradeSyncException.class, () -> service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(26)
                .build()));

        assertEquals("POLITICAL_TRADE_SYNC_FORCE_REQUIRED", ex.getErrorCode());
        verifyNoInteractions(source);
    }

    @Test
    void syncFailsClosedWhenSourceReturnsNoRowsWithoutForce() {
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of());

        PoliticalTradeSyncException ex = assertThrows(PoliticalTradeSyncException.class, () -> service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .build()));

        assertEquals("POLITICAL_TRADE_SYNC_EMPTY_SOURCE", ex.getErrorCode());
        verify(source).fetch(any(PoliticalTradeSourceRequest.class));
        verifyNoMoreInteractions(dataPort);
    }

    @Test
    void syncAllowsForcedEmptySourceForOperatorBackfills() {
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenReturn(List.of());
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");
        when(dataPort.count()).thenReturn(10L);

        PoliticalTradeSyncResponse response = service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(true)
                .build());

        assertEquals(0, response.getFetchedRows());
        assertEquals(10L, response.getTotalCachedRows());
        assertTrue(response.isForced());
    }

    @Test
    void syncRejectsConcurrentRuns() throws Exception {
        PoliticalTrade fetched = trade("20003798315", "Tim Moore", "T", "stock", "BUY", 15_000d, 50_000d, LocalDate.of(2026, 5, 21));
        fetched.setSourceTradeId("CAPITOL_TRADES:20003798315");
        CountDownLatch enteredFetch = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        when(source.fetch(any(PoliticalTradeSourceRequest.class))).thenAnswer(invocation -> {
            enteredFetch.countDown();
            assertTrue(releaseFetch.await(5, TimeUnit.SECONDS));
            return List.of(fetched);
        });
        when(source.sourceName()).thenReturn("CAPITOL_TRADES");
        when(dataPort.findBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(Optional.empty());
        when(dataPort.existsBySourceTradeId("CAPITOL_TRADES:20003798315")).thenReturn(false);

        CompletableFuture<PoliticalTradeSyncResponse> firstSync = CompletableFuture.supplyAsync(() -> service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .build()));
        assertTrue(enteredFetch.await(5, TimeUnit.SECONDS));

        PoliticalTradeSyncException ex = assertThrows(PoliticalTradeSyncException.class, () -> service.sync(PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .build()));

        assertEquals("POLITICAL_TRADE_SYNC_IN_PROGRESS", ex.getErrorCode());
        releaseFetch.countDown();
        assertEquals(1, firstSync.get(5, TimeUnit.SECONDS).getInsertedRows());
        assertFalse(firstSync.isCompletedExceptionally());
    }

    private PoliticalTrade trade(
            String id,
            String politician,
            String ticker,
            String assetType,
            String transactionType,
            Double amountMin,
            Double amountMax,
            LocalDate disclosureDate) {
        return PoliticalTrade.builder()
                .id("CAPITOL_TRADES-" + id)
                .sourceTradeId("CAPITOL_TRADES:" + id)
                .politicianName(politician)
                .party("Republican")
                .chamber("House")
                .state("NC")
                .issuerName(ticker == null ? "Bitcoin" : ticker + " Inc")
                .ticker(ticker)
                .disclosureDate(disclosureDate)
                .tradedDate(LocalDate.of(2026, 5, 18))
                .filedAfterDays(2)
                .owner("Undisclosed")
                .transactionType(transactionType)
                .amountLabel(amountMin == null ? "N/A" : "15K-50K")
                .amountMin(amountMin)
                .amountMax(amountMax)
                .price(24.43d)
                .assetType(assetType)
                .sourceTradeUrl("https://www.capitoltrades.com/trades/" + id)
                .source("CAPITOL_TRADES")
                .build();
    }
}
