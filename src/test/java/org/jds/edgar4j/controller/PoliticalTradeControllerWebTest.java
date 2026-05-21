package org.jds.edgar4j.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import org.jds.edgar4j.exception.GlobalExceptionHandler;
import org.jds.edgar4j.exception.PoliticalTradeSyncException;
import org.jds.edgar4j.service.PoliticalTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(MockitoExtension.class)
class PoliticalTradeControllerWebTest {

    @Mock
    private PoliticalTradeService politicalTradeService;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToController(new PoliticalTradeController(politicalTradeService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void syncAcceptsQueryOnlyPostWithoutContentType() {
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(false)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(response("stock", 1));

        webClient.post()
                .uri("/api/political-trades/sync?assetType=stock&maxPages=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assetType").isEqualTo("stock")
                .jsonPath("$.data.requestedPages").isEqualTo(1);

        verify(politicalTradeService).sync(request);
    }

    @Test
    void syncAcceptsJsonBodyOptions() {
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("crypto")
                .maxPages(2)
                .force(true)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(response("crypto", 2));

        webClient.post()
                .uri("/api/political-trades/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("assetType", "crypto", "maxPages", 2, "force", true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assetType").isEqualTo("crypto")
                .jsonPath("$.data.requestedPages").isEqualTo(2);

        verify(politicalTradeService).sync(request);
    }

    @Test
    void syncAcceptsJsonContentTypeWithEmptyBodyAndQueryOptions() {
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(false)
                .build();
        when(politicalTradeService.sync(request)).thenReturn(response("stock", 1));

        webClient.post()
                .uri("/api/political-trades/sync?assetType=stock&maxPages=1")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assetType").isEqualTo("stock");

        verify(politicalTradeService).sync(request);
    }

    @Test
    void syncRejectsMalformedJsonWithoutCallingService() {
        webClient.post()
                .uri("/api/political-trades/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Bad request");

        verifyNoInteractions(politicalTradeService);
    }

    @Test
    void syncReturnsConflictWhenAnotherSyncIsRunning() {
        PoliticalTradeSyncRequest request = PoliticalTradeSyncRequest.builder()
                .assetType("stock")
                .maxPages(1)
                .force(false)
                .build();
        when(politicalTradeService.sync(request)).thenThrow(new PoliticalTradeSyncException(
                "Political trade sync is already running",
                "POLITICAL_TRADE_SYNC_IN_PROGRESS",
                HttpStatus.CONFLICT));

        webClient.post()
                .uri("/api/political-trades/sync?assetType=stock&maxPages=1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Political trade sync is already running");

        verify(politicalTradeService).sync(request);
    }

    private PoliticalTradeSyncResponse response(String assetType, int requestedPages) {
        return PoliticalTradeSyncResponse.builder()
                .source("CAPITOL_TRADES")
                .assetType(assetType)
                .requestedPages(requestedPages)
                .syncedAt(Instant.parse("2026-05-21T10:00:00Z"))
                .build();
    }
}
