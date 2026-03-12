package org.jds.edgar4j.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.jds.edgar4j.exception.SecApiException;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.jds.edgar4j.service.SettingsService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String CACHE_NAMESPACE = "sec-api";

    private final SecApiConfig config;
    private final SecRateLimiter rateLimiter;
    private final SettingsService settingsService;
    private final DownloadedResourceStore downloadedResourceStore;

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

    public Optional<String> fetchDailyMasterIndex(LocalDate date) {
        for (String url : config.getDailyMasterIndexUrls(date)) {
            Optional<String> response = executeRequestOptional(url);
            if (response.isPresent()) {
                return response;
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<String> fetchSubmissionsAsync(String cik) {
        String url = config.getSubmissionUrl(cik);
        return executeRequestAsync(url);
    }

    public CompletableFuture<String> fetchForm4Async(String cik, String accessionNumber, String primaryDocument) {
        String url = config.getForm4Url(cik, accessionNumber, primaryDocument);
        return executeRequestAsync(url);
    }

    /**
     * Fetches recent filings from the SEC EDGAR Full-Text Search API.
     * EFTS responses are intentionally not cached because the index is used for
     * near-real-time polling.
     */
    public String fetchEftsSearch(String forms, LocalDate startDate, LocalDate endDate, int from, int size) {
        String url = config.getEftsSearchUrl(
                forms,
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                from,
                size);
        return executeRequestNoCache(url);
    }

    /**
     * Fetches any EDGAR filing document by CIK, accession number, and document name.
     */
    public String fetchFiling(String cik, String accessionNumber, String document) {
        String url = config.getFilingUrl(cik, accessionNumber, document);
        return executeRequest(url);
    }

    /**
     * Asynchronously fetches any EDGAR filing document.
     */
    public CompletableFuture<String> fetchFilingAsync(String cik, String accessionNumber, String document) {
        String url = config.getFilingUrl(cik, accessionNumber, document);
        return executeRequestAsync(url);
    }

    private String executeRequest(String url) {
        try {
            String cached = downloadedResourceStore.readText(CACHE_NAMESPACE, url, StandardCharsets.UTF_8).orElse(null);
            if (cached != null) {
                log.debug("Using cached SEC response for {}", url);
                return cached;
            }

            rateLimiter.acquire();
            log.debug("Fetching URL: {}", url);

            HttpRequest request = buildRequest(url);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            validateResponse(response, url);
            String body = readBody(response);
            downloadedResourceStore.writeText(CACHE_NAMESPACE, url, body, StandardCharsets.UTF_8);
            return body;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecApiException("Request interrupted", e);
        } catch (Exception e) {
            log.error("Error fetching URL: {}", url, e);
            throw new SecApiException("Failed to fetch data from SEC API: " + e.getMessage(), e);
        }
    }

    private Optional<String> executeRequestOptional(String url) {
        try {
            return Optional.of(executeRequest(url));
        } catch (SecApiException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Resource not found:")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private CompletableFuture<String> executeRequestAsync(String url) {
        String cached = downloadedResourceStore.readText(CACHE_NAMESPACE, url, StandardCharsets.UTF_8).orElse(null);
        if (cached != null) {
            log.debug("Using cached SEC response for {}", url);
            return CompletableFuture.completedFuture(cached);
        }

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
                        String body = readBody(response);
                        downloadedResourceStore.writeText(CACHE_NAMESPACE, u, body, StandardCharsets.UTF_8);
                        return body;
                    });
        });
    }

    private String executeRequestNoCache(String url) {
        try {
            rateLimiter.acquire();
            log.debug("Fetching URL (no cache): {}", url);

            HttpRequest request = buildRequest(url);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            validateResponse(response, url);
            return readBody(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecApiException("Request interrupted", e);
        } catch (Exception e) {
            log.error("Error fetching URL (no cache): {}", url, e);
            throw new SecApiException("Failed to fetch from SEC API: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildRequest(String url) {
        String userAgent = settingsService.getUserAgent();

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/html, application/xml, text/plain")
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
