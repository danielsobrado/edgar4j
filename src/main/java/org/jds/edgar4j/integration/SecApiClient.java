package org.jds.edgar4j.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.jds.edgar4j.exception.SecApiException;
import org.jds.edgar4j.service.SettingsService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final SecApiConfig config;
    private final SecRateLimiter rateLimiter;
    private final SettingsService settingsService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String fetchSubmissions(String cik) {
        String url = config.getSubmissionUrl(cik);
        return executeRequest(url);
    }

    public String fetchCompanyTickers() {
        return executeRequest(config.getCompanyTickersUrl());
    }

    public String fetchCompanyTickersExchanges() {
        return executeRequest(config.getCompanyTickersExchangesUrl());
    }

    public String fetchCompanyTickersMutualFunds() {
        return executeRequest(config.getCompanyTickersMFsUrl());
    }

    public String fetchForm4(String cik, String accessionNumber, String primaryDocument) {
        String url = config.getForm4Url(cik, accessionNumber, primaryDocument);
        return executeRequest(url);
    }

    public CompletableFuture<String> fetchSubmissionsAsync(String cik) {
        String url = config.getSubmissionUrl(cik);
        return executeRequestAsync(url);
    }

    public CompletableFuture<String> fetchForm4Async(String cik, String accessionNumber, String primaryDocument) {
        String url = config.getForm4Url(cik, accessionNumber, primaryDocument);
        return executeRequestAsync(url);
    }

    private String executeRequest(String url) {
        try {
            rateLimiter.acquire();
            log.debug("Fetching URL: {}", url);

            HttpRequest request = buildRequest(url);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            validateResponse(response, url);
            return readBody(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecApiException("Request interrupted", e);
        } catch (Exception e) {
            log.error("Error fetching URL: {}", url, e);
            throw new SecApiException("Failed to fetch data from SEC API: " + e.getMessage(), e);
        }
    }

    private CompletableFuture<String> executeRequestAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecApiException("Request interrupted", e);
            }
            return url;
        }).thenCompose(u -> {
            log.debug("Fetching URL async: {}", u);
            HttpRequest request = buildRequest(u);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(response -> {
                        validateResponse(response, u);
                        return readBody(response);
                    });
        });
    }

    private HttpRequest buildRequest(String url) {
        String userAgent = settingsService.getUserAgent();

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/html, application/xml")
                .GET()
                .build();
    }

    private void validateResponse(HttpResponse<?> response, String url) {
        int statusCode = response.statusCode();

        if (statusCode == 404) {
            throw new SecApiException("Resource not found: " + url);
        } else if (statusCode == 429) {
            throw new SecApiException("Rate limit exceeded. Please try again later.");
        } else if (statusCode >= 400) {
            throw new SecApiException("SEC API error: HTTP " + statusCode + " for URL: " + url);
        }

        log.debug("Successfully fetched {} (status: {})", url, statusCode);
    }

    private String readBody(HttpResponse<InputStream> response) {
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        try (InputStream rawStream = response.body();
             InputStream decodedStream = wrapStream(rawStream, encoding)) {
            return new String(decodedStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SecApiException("Failed to read SEC response body", e);
        }
    }

    private InputStream wrapStream(InputStream inputStream, String encoding) throws IOException {
        if ("gzip".equalsIgnoreCase(encoding)) {
            return new GZIPInputStream(inputStream);
        }
        if ("deflate".equalsIgnoreCase(encoding)) {
            return new InflaterInputStream(inputStream);
        }
        return inputStream;
    }
}
