package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import org.jds.edgar4j.service.PoliticalTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PoliticalTradeControllerTest {

    @Mock
    private PoliticalTradeService politicalTradeService;

    private PoliticalTradeController controller;

    @BeforeEach
    void setUp() {
        controller = new PoliticalTradeController(politicalTradeService);
    }

    @Test
    void screenBuildsRequestAndReturnsResults() {
        PaginatedResponse<PoliticalTradeResponse> expected = PaginatedResponse.of(List.of(PoliticalTradeResponse.builder()
                .politicianName("Tim Moore")
                .ticker("T")
                .build()), 0, 50, 1);
        when(politicalTradeService.screen(any(PoliticalTradeScreenRequest.class))).thenReturn(expected);

        ResponseEntity<ApiResponse<PaginatedResponse<PoliticalTradeResponse>>> response = controller.screen(
                "tim",
                "$T:US",
                "AT&T",
                "Republican",
                "House",
                "NC",
                "stock",
                "BUY",
                "Undisclosed",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 21),
                10_000d,
                100_000d,
                "disclosureDate",
                "desc",
                0,
                50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(expected, response.getBody().getData());

        ArgumentCaptor<PoliticalTradeScreenRequest> captor = ArgumentCaptor.forClass(PoliticalTradeScreenRequest.class);
        verify(politicalTradeService).screen(captor.capture());
        assertEquals("tim", captor.getValue().getPolitician());
        assertEquals("$T:US", captor.getValue().getTicker());
        assertEquals(10_000d, captor.getValue().getMinAmount());
    }

    @Test
    void exportReturnsCsvAttachment() {
        when(politicalTradeService.export(any(PoliticalTradeScreenRequest.class), org.mockito.Mockito.eq("CSV")))
                .thenReturn("ticker\nT\n".getBytes());

        ResponseEntity<byte[]> response = controller.export(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "desc", "CSV");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertEquals("ticker\nT\n", new String(response.getBody()));
    }

    @Test
    void syncDelegatesAndWrapsResponse() {
        PoliticalTradeSyncResponse expected = PoliticalTradeSyncResponse.builder()
                .source("CAPITOL_TRADES")
                .assetType("stock")
                .requestedPages(25)
                .syncedAt(Instant.parse("2026-05-21T10:00:00Z"))
                .build();
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(25)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>> response = controller.sync(
                request.getAssetType(),
                request.getMaxPages(),
                request.isForce(),
                Mono.just(Map.of()))
                .block();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Political trade sync completed", response.getBody().getMessage());
        assertEquals(expected, response.getBody().getData());
    }

    @Test
    void syncReadsQueryParametersWithoutBody() {
        PoliticalTradeSyncResponse expected = PoliticalTradeSyncResponse.builder()
                .source("CAPITOL_TRADES")
                .assetType("stock")
                .requestedPages(1)
                .syncedAt(Instant.parse("2026-05-21T10:00:00Z"))
                .build();
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(false)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>> response = controller.sync("stock", 1, false).block();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(expected, response.getBody().getData());
    }

    @Test
    void syncReadsJsonBodyOptionsWhenQueryParametersAreAbsent() {
        PoliticalTradeSyncResponse expected = PoliticalTradeSyncResponse.builder()
                .source("CAPITOL_TRADES")
                .assetType("stock")
                .requestedPages(1)
                .syncedAt(Instant.parse("2026-05-21T10:00:00Z"))
                .build();
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(true)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>> response = controller.sync(
                null,
                null,
                null,
                Mono.just(Map.of("assetType", "stock", "maxPages", 1, "force", true)))
                .block();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(expected, response.getBody().getData());
    }
}
