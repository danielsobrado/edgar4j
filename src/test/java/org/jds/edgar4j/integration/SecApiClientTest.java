package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("fetchCompanyConcept should request and cache the SEC companyconcept endpoint")
    void fetchCompanyConceptShouldRequestCompanyConceptEndpoint() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/xbrl/companyconcept/CIK0000320193/us-gaap/CommonStockDividendsPerShareDeclared.json",
                exchange -> writeResponse(exchange, 200, "{\"concept\":\"dps\"}"));
        server.start();

        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "http://127.0.0.1:" + server.getAddress().getPort());

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

        String response = client.fetchCompanyConcept(
                "320193",
                "us-gaap",
                "CommonStockDividendsPerShareDeclared");

        assertEquals("{\"concept\":\"dps\"}", response);
    }

    @Test
    @DisplayName("fetchCompanyFacts should return cached companyfacts without an HTTP request")
    void fetchCompanyFactsShouldReturnCachedCompanyFacts() {
        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "http://127.0.0.1:9");

        when(downloadedResourceStore.readText(anyString(), anyString(), any(Charset.class)))
                .thenReturn(Optional.of("{\"cached\":true}"));

        SecApiClient client = new SecApiClient(
                config,
                new SecRateLimiter(1000),
                settingsService,
                downloadedResourceStore);

        String response = client.fetchCompanyFacts("320193");

        assertEquals("{\"cached\":true}", response);
        verify(settingsService, never()).getUserAgent();
    }

    @Test
    @DisplayName("fetchCompanyFactsOptional should return empty for SEC 404 responses")
    void fetchCompanyFactsOptionalShouldReturnEmptyForNotFoundResponses() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/xbrl/companyfacts/CIK9999999999.json",
                exchange -> writeResponse(exchange, 404, "Not Found"));
        server.start();

        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "http://127.0.0.1:" + server.getAddress().getPort());

        when(settingsService.getUserAgent()).thenReturn("My Company sec-ops@mycompany.com");
        when(downloadedResourceStore.readText(anyString(), anyString(), any(Charset.class)))
                .thenReturn(Optional.empty());

        SecApiClient client = new SecApiClient(
                config,
                new SecRateLimiter(1000),
                settingsService,
                downloadedResourceStore);

        Optional<String> response = client.fetchCompanyFactsOptional("9999999999");

        assertTrue(response.isEmpty());
    }

    @Test
    @DisplayName("async companyfacts, companyconcept, and frame variants should request SEC endpoints")
    void asyncVariantsShouldRequestSecEndpoints() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/xbrl/companyfacts/CIK0000320193.json",
                exchange -> writeResponse(exchange, 200, "{\"facts\":true}"));
        server.createContext(
                "/api/xbrl/companyconcept/CIK0000320193/us-gaap/CommonStockDividendsPerShareDeclared.json",
                exchange -> writeResponse(exchange, 200, "{\"concept\":true}"));
        server.createContext(
                "/api/xbrl/frames/us-gaap/CommonStockDividendsPerShareDeclared/USD-per-shares/CY2023Q4I.json",
                exchange -> writeResponse(exchange, 200, "{\"frame\":true}"));
        server.start();

        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "http://127.0.0.1:" + server.getAddress().getPort());

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

        assertEquals("{\"facts\":true}", client.fetchCompanyFactsAsync("320193").join());
        assertEquals("{\"concept\":true}", client.fetchCompanyConceptAsync(
                "320193",
                "us-gaap",
                "CommonStockDividendsPerShareDeclared").join());
        assertEquals("{\"frame\":true}", client.fetchFrameAsync(
                "us-gaap",
                "CommonStockDividendsPerShareDeclared",
                "USD-per-shares",
                "CY2023Q4I").join());
    }

    @Test
    @DisplayName("fetchFrame should request and cache the SEC frames endpoint")
    void fetchFrameShouldRequestFramesEndpoint() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/xbrl/frames/us-gaap/CommonStockDividendsPerShareDeclared/USD-per-shares/CY2023Q4I.json",
                exchange -> writeResponse(exchange, 200, "{\"frame\":\"CY2023Q4I\"}"));
        server.start();

        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "http://127.0.0.1:" + server.getAddress().getPort());

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

        String response = client.fetchFrame(
                "us-gaap",
                "CommonStockDividendsPerShareDeclared",
                "USD-per-shares",
                "CY2023Q4I");

        assertEquals("{\"frame\":\"CY2023Q4I\"}", response);
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
