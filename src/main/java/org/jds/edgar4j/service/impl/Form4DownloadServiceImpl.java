package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.DownloadHistory.ProcessingStatus;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.Form4DownloadService;
import org.jds.edgar4j.service.Form4Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 4 download service
 * Downloads Form 4 filings from SEC EDGAR and processes them
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Service
public class Form4DownloadServiceImpl implements Form4DownloadService {

    @Autowired
    private PipelineProperties properties;

    @Autowired
    private SecRateLimiter rateLimiter;

    @Autowired
    private Form4Service form4Service;

    @Autowired
    private Form4Repository form4Repository;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    private final HttpClient httpClient;
    private static final Pattern ACCESSION_PATTERN = Pattern.compile("(\\d{10})-(\\d{2})-(\\d{6})");

    public Form4DownloadServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public int downloadForDate(LocalDate date) {
        log.info("Starting Form 4 download for date: {}", date);

        try {
            // Download from RSS feed for the specific date
            return downloadFromRssFeed(date, 0, properties.getBatchSize());

        } catch (Exception e) {
            log.error("Error downloading Form 4s for date {}", date, e);
            return 0;
        }
    }

    @Override
    public int downloadForDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Starting Form 4 download for date range: {} to {}", startDate, endDate);

        int totalDownloaded = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            totalDownloaded += downloadForDate(currentDate);

            // Add delay between dates to respect rate limits
            if (!currentDate.equals(endDate)) {
                sleep(properties.getBatchDelayMs());
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Completed date range download: {} Form 4s", totalDownloaded);
        return totalDownloaded;
    }

    @Override
    public int downloadLatestFilings(int maxCount) {
        log.info("Downloading latest {} Form 4 filings", maxCount);

        try {
            return downloadFromRssFeed(null, 0, maxCount);

        } catch (Exception e) {
            log.error("Error downloading latest Form 4s", e);
            return 0;
        }
    }

    @Override
    public boolean downloadByAccessionNumber(String accessionNumber) {
        log.info("Downloading Form 4 by accession number: {}", accessionNumber);

        // Check if already downloaded
        if (downloadHistoryRepository.existsByAccessionNumber(accessionNumber)) {
            log.debug("Accession number {} already downloaded, skipping", accessionNumber);
            return true;
        }

        try {
            // Build URL for the filing
            String url = buildFilingUrl(accessionNumber);

            return downloadAndProcess(accessionNumber, url, LocalDate.now());

        } catch (Exception e) {
            log.error("Error downloading accession number {}", accessionNumber, e);
            return false;
        }
    }

    @Override
    public int retryFailedDownloads(int maxRetries) {
        log.info("Retrying failed downloads (max retries: {})", maxRetries);

        // Get failed downloads that haven't exceeded max retries
        List<DownloadHistory> failedDownloads =
            downloadHistoryRepository.findByStatusAndRetryCountLessThan(
                ProcessingStatus.FAILED, maxRetries);

        log.info("Found {} failed downloads to retry", failedDownloads.size());

        int successCount = 0;
        for (DownloadHistory history : failedDownloads) {
            try {
                log.debug("Retrying accession number: {} (attempt {})",
                    history.getAccessionNumber(), history.getRetryCount() + 1);

                boolean success = downloadByAccessionNumber(history.getAccessionNumber());
                if (success) {
                    successCount++;
                }

                // Add delay between retries
                sleep(properties.getRetryDelayMs());

            } catch (Exception e) {
                log.error("Error retrying accession number {}", history.getAccessionNumber(), e);
            }
        }

        log.info("Successfully retried {}/{} failed downloads", successCount, failedDownloads.size());
        return successCount;
    }

    @Override
    public DownloadStatistics getStatistics() {
        long total = downloadHistoryRepository.count();
        long successful = downloadHistoryRepository.countByStatus(ProcessingStatus.COMPLETED);
        long failed = downloadHistoryRepository.countByStatus(ProcessingStatus.FAILED);
        long pending = downloadHistoryRepository.countByStatus(ProcessingStatus.PENDING);
        long skipped = downloadHistoryRepository.countByStatus(ProcessingStatus.SKIPPED);

        return new DownloadStatistics(total, successful, failed, pending, skipped);
    }

    /**
     * Download Form 4s from SEC RSS feed
     */
    private int downloadFromRssFeed(LocalDate date, int start, int count) {
        int downloaded = 0;
        int currentStart = start;
        int remaining = count;

        while (remaining > 0) {
            try {
                int batchSize = Math.min(remaining, properties.getBatchSize());

                // Build RSS feed URL
                String url = buildRssFeedUrl(date, currentStart, batchSize);

                log.debug("Fetching RSS feed: start={}, count={}", currentStart, batchSize);

                // Rate limit the request
                rateLimiter.acquire();

                // Fetch RSS feed
                String rssFeed = fetchUrl(url);
                if (rssFeed == null || rssFeed.isEmpty()) {
                    log.warn("Empty RSS feed response");
                    break;
                }

                // Parse RSS feed to extract accession numbers and URLs
                List<FilingEntry> entries = parseRssFeed(rssFeed, date);

                if (entries.isEmpty()) {
                    log.debug("No more Form 4s found in RSS feed");
                    break;
                }

                log.debug("Found {} Form 4 entries in RSS feed", entries.size());

                // Download and process each filing
                for (FilingEntry entry : entries) {
                    try {
                        if (downloadAndProcess(entry.accessionNumber, entry.url, entry.filingDate)) {
                            downloaded++;
                        }

                        // Small delay between downloads
                        sleep(100);

                    } catch (Exception e) {
                        log.error("Error processing filing {}", entry.accessionNumber, e);
                    }
                }

                currentStart += batchSize;
                remaining -= entries.size();

                // If we got fewer entries than requested, we've reached the end
                if (entries.size() < batchSize) {
                    break;
                }

                // Delay between batches
                sleep(properties.getBatchDelayMs());

            } catch (Exception e) {
                log.error("Error fetching RSS feed", e);
                break;
            }
        }

        return downloaded;
    }

    /**
     * Download and process a single Form 4 filing
     */
    private boolean downloadAndProcess(String accessionNumber, String url, LocalDate filingDate) {
        // Check if already downloaded
        if (downloadHistoryRepository.existsByAccessionNumber(accessionNumber)) {
            log.trace("Skipping already downloaded: {}", accessionNumber);
            recordDownloadHistory(accessionNumber, url, filingDate, ProcessingStatus.SKIPPED, null, null);
            return false;
        }

        long startTime = System.currentTimeMillis();
        DownloadHistory history = null;

        try {
            // Create download history record
            history = recordDownloadHistory(accessionNumber, url, filingDate, ProcessingStatus.DOWNLOADING, null, null);

            // Rate limit the download
            rateLimiter.acquire();

            // Download the Form 4 XML
            String xml = fetchUrl(url);

            if (xml == null || xml.isEmpty()) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Empty response", null);
                return false;
            }

            // Update status to parsing
            history.setStatus(ProcessingStatus.PARSING);
            downloadHistoryRepository.save(history);

            // Parse the Form 4
            Form4 form4 = form4Service.parseForm4(xml);

            if (form4 == null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Parsing returned null", null);
                return false;
            }

            // Save to Elasticsearch
            form4Repository.save(form4);

            // Update status to completed
            long duration = System.currentTimeMillis() - startTime;
            updateDownloadHistory(history, ProcessingStatus.COMPLETED, null, duration);

            log.debug("Successfully processed Form 4: {} ({}ms)", accessionNumber, duration);
            return true;

        } catch (Exception e) {
            log.error("Error processing Form 4 {}", accessionNumber, e);

            if (history != null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, e.getMessage(), null);
            }

            return false;
        }
    }

    /**
     * Parse RSS feed to extract filing entries
     */
    private List<FilingEntry> parseRssFeed(String rssFeed, LocalDate targetDate) {
        List<FilingEntry> entries = new ArrayList<>();

        try {
            // Parse Atom feed entries
            // This is a simplified parser - in production you'd use a proper XML parser
            String[] items = rssFeed.split("<entry>");

            for (String item : items) {
                if (!item.contains("</entry>")) {
                    continue;
                }

                // Extract accession number
                String accessionNumber = extractBetween(item, "<accession-number>", "</accession-number>");
                if (accessionNumber == null) {
                    continue;
                }

                // Extract filing date
                String filingDateStr = extractBetween(item, "<filing-date>", "</filing-date>");
                LocalDate filingDate = filingDateStr != null ? LocalDate.parse(filingDateStr) : LocalDate.now();

                // Skip if date filter is specified and doesn't match
                if (targetDate != null && !filingDate.equals(targetDate)) {
                    continue;
                }

                // Extract filing URL (look for .xml link)
                String link = extractBetween(item, "<link href=\"", "\"");
                if (link == null || !link.endsWith(".xml")) {
                    // Try to construct the URL
                    link = buildFilingUrl(accessionNumber);
                }

                entries.add(new FilingEntry(accessionNumber, link, filingDate));
            }

        } catch (Exception e) {
            log.error("Error parsing RSS feed", e);
        }

        return entries;
    }

    /**
     * Build RSS feed URL
     */
    private String buildRssFeedUrl(LocalDate date, int start, int count) {
        String url = properties.getRssFeedUrl()
            .replace("{start}", String.valueOf(start))
            .replace("{count}", String.valueOf(count));

        if (date != null) {
            url += "&dateb=" + date.toString();
        }

        return url;
    }

    /**
     * Build filing URL from accession number
     */
    private String buildFilingUrl(String accessionNumber) {
        Matcher matcher = ACCESSION_PATTERN.matcher(accessionNumber);
        if (matcher.matches()) {
            String cik = matcher.group(1);
            String accessionNoHyphens = accessionNumber.replace("-", "");
            return properties.getArchivesBaseUrl() + cik + "/" + accessionNoHyphens + "/" + accessionNumber + ".xml";
        }
        throw new IllegalArgumentException("Invalid accession number format: " + accessionNumber);
    }

    /**
     * Fetch URL content
     */
    private String fetchUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", properties.getUserAgent())
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.warn("HTTP {} fetching {}", response.statusCode(), url);
                return null;
            }

        } catch (Exception e) {
            log.error("Error fetching URL: {}", url, e);
            return null;
        }
    }

    /**
     * Record download history
     */
    private DownloadHistory recordDownloadHistory(String accessionNumber, String url, LocalDate filingDate,
                                                    ProcessingStatus status, String errorMessage, Long durationMs) {
        DownloadHistory history = DownloadHistory.builder()
            .accessionNumber(accessionNumber)
            .sourceUrl(url)
            .filingDate(filingDate)
            .status(status)
            .downloadedAt(LocalDateTime.now())
            .lastAttemptAt(LocalDateTime.now())
            .retryCount(0)
            .errorMessage(errorMessage)
            .processingDurationMs(durationMs)
            .build();

        return downloadHistoryRepository.save(history);
    }

    /**
     * Update download history
     */
    private void updateDownloadHistory(DownloadHistory history, ProcessingStatus status,
                                        String errorMessage, Long durationMs) {
        history.setStatus(status);
        history.setLastAttemptAt(LocalDateTime.now());

        if (status == ProcessingStatus.COMPLETED) {
            history.setProcessedAt(LocalDateTime.now());
        }

        if (status == ProcessingStatus.FAILED) {
            history.setRetryCount(history.getRetryCount() + 1);
            history.setErrorMessage(errorMessage);
        }

        if (durationMs != null) {
            history.setProcessingDurationMs(durationMs);
        }

        downloadHistoryRepository.save(history);
    }

    /**
     * Extract string between two markers
     */
    private String extractBetween(String text, String start, String end) {
        int startIdx = text.indexOf(start);
        if (startIdx == -1) {
            return null;
        }
        startIdx += start.length();

        int endIdx = text.indexOf(end, startIdx);
        if (endIdx == -1) {
            return null;
        }

        return text.substring(startIdx, endIdx).trim();
    }

    /**
     * Sleep for specified milliseconds
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Filing entry from RSS feed
     */
    private static class FilingEntry {
        final String accessionNumber;
        final String url;
        final LocalDate filingDate;

        FilingEntry(String accessionNumber, String url, LocalDate filingDate) {
            this.accessionNumber = accessionNumber;
            this.url = url;
            this.filingDate = filingDate;
        }
    }
}
