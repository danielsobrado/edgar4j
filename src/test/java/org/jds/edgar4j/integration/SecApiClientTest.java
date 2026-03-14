package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class SecApiClientTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private DownloadedResourceStore downloadedResourceStore;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("fetchDailyMasterIndex should fall back to the quarterly index URL when the legacy top-level URL returns 403")
    void fetchDailyMasterIndexShouldFallbackToQuarterlyIndexUrl() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Archives/edgar/daily-index/master.20260314.idx",
                exchange -> writeResponse(exchange, 403, "Forbidden"));
        server.createContext("/Archives/edgar/daily-index/2026/QTR1/master.20260314.idx",
                exchange -> writeResponse(exchange, 200, "CIK|Company Name|Form Type|Date Filed|Filename\n"));
        server.start();

        SecApiConfig config = new SecApiConfig();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(config, "baseSecUrl", baseUrl);

        when(settingsService.getUserAgent()).thenReturn("My Company sec-ops@mycompany.com");
        when(downloadedResourceStore.readText(anyString(), anyString(), any(Charset.class)))
                .thenReturn(Optional.empty());
        when(downloadedResourceStore.writeText(anyString(), anyString(), anyString(), any(Charset.class)))
                .thenReturn(Path.of("cache.txt"));

        SecApiClient client = new SecApiClient(
                config,
                new SecRateLimiter(1000),
                settingsService,
                downloadedResourceStore);

        Optional<String> response = client.fetchDailyMasterIndex(LocalDate.of(2026, 3, 14));

        assertTrue(response.isPresent());
        assertEquals("CIK|Company Name|Form Type|Date Filed|Filename\n", response.get());
    }

    @Test
    @DisplayName("fetchDailyMasterIndex should return empty when all daily index candidates are unavailable")
    void fetchDailyMasterIndexShouldReturnEmptyWhenAllCandidatesAreUnavailable() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Archives/edgar/daily-index/master.20260314.idx",
                exchange -> writeResponse(exchange, 403, ""));
        server.createContext("/Archives/edgar/daily-index/2026/QTR1/master.20260314.idx",
                exchange -> writeResponse(exchange, 403, ""));
        server.start();

        SecApiConfig config = new SecApiConfig();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(config, "baseSecUrl", baseUrl);

        when(settingsService.getUserAgent()).thenReturn("My Company sec-ops@mycompany.com");
        when(downloadedResourceStore.readText(anyString(), anyString(), any(Charset.class)))
                .thenReturn(Optional.empty());

        SecApiClient client = new SecApiClient(
                config,
                new SecRateLimiter(1000),
                settingsService,
                downloadedResourceStore);

        Optional<String> response = client.fetchDailyMasterIndex(LocalDate.of(2026, 3, 14));

        assertTrue(response.isEmpty());
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
