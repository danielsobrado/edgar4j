package org.jds.edgar4j.service.provider.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.jds.edgar4j.service.provider.MarketDataProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class YahooFinanceProviderTest {

    @Mock
    private MarketDataProviderSettingsResolver settingsResolver;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("getCompanyProfile should parse market cap from Yahoo quote response")
    void getCompanyProfileShouldParseMarketCap() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v7/finance/quote", exchange -> writeResponse(exchange, """
                {
                  "quoteResponse": {
                    "result": [
                      {
                        "symbol": "AAPL",
                        "longName": "Apple Inc.",
                        "currency": "USD",
                        "fullExchangeName": "NasdaqGS",
                        "marketCap": 3250000000000,
                        "sharesOutstanding": 15000000000
                      }
                    ]
                  }
                }
                """));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v8/finance/chart";
        when(settingsResolver.resolve(MarketDataProviders.YAHOO_FINANCE)).thenReturn(
                new MarketDataProviderSettingsResolver.ResolvedProviderConfig(
                        MarketDataProviders.YAHOO_FINANCE,
                        true,
                        true,
                        true,
                        baseUrl,
                        null,
                        1,
                        new MarketDataProviderProperties.RateLimitConfig(1000, Duration.ofSeconds(1)),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(1)));

        YahooFinanceProvider provider = new YahooFinanceProvider(new ObjectMapper(), settingsResolver);

        MarketDataProvider.CompanyProfile profile = provider.getCompanyProfile("AAPL").join();

        assertNotNull(profile);
        assertEquals("AAPL", profile.getSymbol());
        assertEquals("Apple Inc.", profile.getName());
        assertEquals("USD", profile.getCurrency());
        assertEquals("NasdaqGS", profile.getExchange());
        assertEquals(3_250_000_000_000L, profile.getMarketCapitalization());
        assertEquals(15_000_000_000L, profile.getSharesOutstanding());
    }

    @Test
    @DisplayName("getCompanyProfile should derive market cap from price and shares when Yahoo omits market cap")
    void getCompanyProfileShouldDeriveMarketCapWhenMissing() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v7/finance/quote", exchange -> writeResponse(exchange, """
                {
                  "quoteResponse": {
                    "result": [
                      {
                        "symbol": "AAPL",
                        "shortName": "Apple",
                        "currency": "USD",
                        "exchange": "NMS",
                        "regularMarketPrice": 210.5,
                        "sharesOutstanding": 1000000000
                      }
                    ]
                  }
                }
                """));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v8/finance/chart";
        when(settingsResolver.resolve(MarketDataProviders.YAHOO_FINANCE)).thenReturn(
                new MarketDataProviderSettingsResolver.ResolvedProviderConfig(
                        MarketDataProviders.YAHOO_FINANCE,
                        true,
                        true,
                        true,
                        baseUrl,
                        null,
                        1,
                        new MarketDataProviderProperties.RateLimitConfig(1000, Duration.ofSeconds(1)),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(1)));

        YahooFinanceProvider provider = new YahooFinanceProvider(new ObjectMapper(), settingsResolver);

        MarketDataProvider.CompanyProfile profile = provider.getCompanyProfile("AAPL").join();

        assertNotNull(profile);
        assertEquals("Apple", profile.getName());
        assertEquals(210_500_000_000L, profile.getMarketCapitalization());
        assertEquals(1_000_000_000L, profile.getSharesOutstanding());
    }

    @Test
    @DisplayName("getCompanyProfile should fall back to crumb-backed quote summary when direct quote is unauthorized")
    void getCompanyProfileShouldFallbackToQuoteSummaryWhenQuoteEndpointUnauthorized() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "A1=test-cookie; Path=/");
            writeResponse(exchange, "{}");
        });
        server.createContext("/v1/test/getcrumb", exchange -> writeResponse(exchange, "crumb-123"));
        server.createContext("/v7/finance/quote", exchange -> writeResponse(exchange, 401, """
                {
                  "finance": {
                    "result": null,
                    "error": {
                      "code": "Unauthorized",
                      "description": "User is unable to access this feature"
                    }
                  }
                }
                """));
        server.createContext("/v10/finance/quoteSummary/AAPL", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("crumb=crumb-123")) {
                writeResponse(exchange, 401, "{\"finance\":{\"error\":{\"code\":\"Unauthorized\"}}}");
                return;
            }

            writeResponse(exchange, """
                    {
                      "quoteSummary": {
                        "result": [
                          {
                            "price": {
                              "longName": "Apple Inc.",
                              "currency": "USD",
                              "fullExchangeName": "NasdaqGS",
                              "marketCap": { "raw": 3250000000000 },
                              "regularMarketPrice": { "raw": 210.5 }
                            },
                            "defaultKeyStatistics": {
                              "sharesOutstanding": { "raw": 15000000000 }
                            }
                          }
                        ]
                      }
                    }
                    """);
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v8/finance/chart";
        when(settingsResolver.resolve(MarketDataProviders.YAHOO_FINANCE)).thenReturn(
                new MarketDataProviderSettingsResolver.ResolvedProviderConfig(
                        MarketDataProviders.YAHOO_FINANCE,
                        true,
                        true,
                        true,
                        baseUrl,
                        null,
                        1,
                        new MarketDataProviderProperties.RateLimitConfig(1000, Duration.ofSeconds(1)),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(1)));

        YahooFinanceProvider provider = new YahooFinanceProvider(new ObjectMapper(), settingsResolver);

        MarketDataProvider.CompanyProfile profile = provider.getCompanyProfile("AAPL").join();

        assertNotNull(profile);
        assertEquals("Apple Inc.", profile.getName());
        assertEquals("USD", profile.getCurrency());
        assertEquals("NasdaqGS", profile.getExchange());
        assertEquals(3_250_000_000_000L, profile.getMarketCapitalization());
        assertEquals(15_000_000_000L, profile.getSharesOutstanding());
    }

    @Test
    @DisplayName("invalid Yahoo symbols should not put the provider into global cooldown")
    void invalidSymbolsShouldNotPutProviderIntoCooldown() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/finance/chart/N/A", exchange -> writeResponse(exchange, 404, """
                {
                  "chart": {
                    "result": null,
                    "error": {
                      "code": "Not Found"
                    }
                  }
                }
                """));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v8/finance/chart";
        when(settingsResolver.resolve(MarketDataProviders.YAHOO_FINANCE)).thenReturn(
                new MarketDataProviderSettingsResolver.ResolvedProviderConfig(
                        MarketDataProviders.YAHOO_FINANCE,
                        true,
                        true,
                        true,
                        baseUrl,
                        null,
                        1,
                        new MarketDataProviderProperties.RateLimitConfig(1000, Duration.ofSeconds(1)),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)));

        YahooFinanceProvider provider = new YahooFinanceProvider(new ObjectMapper(), settingsResolver);

        provider.getCurrentPrice("N/A").join();

        assertTrue(provider.isAvailable());
    }

    private void writeResponse(HttpExchange exchange, String body) throws IOException {
        writeResponse(exchange, 200, body);
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
