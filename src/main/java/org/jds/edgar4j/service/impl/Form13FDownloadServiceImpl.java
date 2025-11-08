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
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import org.jds.edgar4j.repository.Form13FRepository;
import org.jds.edgar4j.service.Form13FDownloadService;
import org.jds.edgar4j.service.Form13FService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 13F download service
 * Downloads Form 13F quarterly institutional holdings reports from SEC EDGAR
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Service
public class Form13FDownloadServiceImpl implements Form13FDownloadService {

    @Autowired
    private PipelineProperties properties;

    @Autowired
    private SecRateLimiter rateLimiter;

    @Autowired
    private Form13FService form13FService;

    @Autowired
    private Form13FRepository form13FRepository;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    private final HttpClient httpClient;
    private static final Pattern ACCESSION_PATTERN = Pattern.compile("(\\d{10})-(\\d{2})-(\\d{6})");

    public Form13FDownloadServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public int downloadForQuarter(int year, int quarter) {
        log.info("Starting Form 13F download for Q{} {}", quarter, year);

        if (quarter < 1 || quarter > 4) {
            log.error("Invalid quarter: {}. Must be 1-4", quarter);
            return 0;
        }

        // Calculate quarter end date
        LocalDate quarterEnd = getQuarterEndDate(year, quarter);

        // Form 13F is due 45 days after quarter end
        LocalDate filingDeadline = quarterEnd.plusDays(45);
        LocalDate filingStart = quarterEnd;

        return downloadForDateRange(filingStart, filingDeadline);
    }

    @Override
    public int downloadForDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Starting Form 13F download for date range: {} to {}", startDate, endDate);

        int totalDownloaded = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            totalDownloaded += downloadFromRssFeed(currentDate, 0, properties.getBatchSize());

            // Add delay between dates to respect rate limits
            if (!currentDate.equals(endDate)) {
                sleep(properties.getBatchDelayMs());
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Completed date range download: {} Form 13Fs", totalDownloaded);
        return totalDownloaded;
    }

    @Override
    public int downloadLatestFilings(int maxCount) {
        log.info("Downloading latest {} Form 13F filings", maxCount);

        try {
            return downloadFromRssFeed(null, 0, maxCount);
        } catch (Exception e) {
            log.error("Error downloading latest Form 13Fs", e);
            return 0;
        }
    }

    @Override
    public boolean downloadByAccessionNumber(String accessionNumber) {
        log.info("Downloading Form 13F by accession number: {}", accessionNumber);

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
    public boolean downloadLatestForInstitution(String filerCik) {
        log.info("Downloading latest Form 13F for institution CIK: {}", filerCik);

        // This would require querying the SEC submissions API for the institution
        // For now, we'll use the RSS feed approach
        // In production, you'd query: https://data.sec.gov/submissions/CIK{filerCik}.json

        log.warn("downloadLatestForInstitution not fully implemented - using RSS feed");
        return downloadLatestFilings(100) > 0;
    }

    @Override
    public int retryFailedDownloads(int maxRetries) {
        log.info("Retrying failed Form 13F downloads (max retries: {})", maxRetries);

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

        // Count unique institutions
        long totalInstitutions = form13FRepository.count();

        // Calculate total holdings across all filings
        long totalHoldings = 0;
        Iterable<Form13F> allForms = form13FRepository.findAll();
        for (Form13F form : allForms) {
            if (form.getHoldingsCount() != null) {
                totalHoldings += form.getHoldingsCount();
            }
        }

        return new DownloadStatistics(total, successful, failed, pending, skipped,
            totalInstitutions, totalHoldings);
    }

    /**
     * Download Form 13Fs from SEC RSS feed
     */
    private int downloadFromRssFeed(LocalDate date, int start, int count) {
        int downloaded = 0;
        int currentStart = start;
        int remaining = count;

        while (remaining > 0) {
            try {
                int batchSize = Math.min(remaining, properties.getBatchSize());

                // Build RSS feed URL for Form 13F
                String url = buildRssFeedUrl(date, currentStart, batchSize);

                log.debug("Fetching Form 13F RSS feed: start={}, count={}", currentStart, batchSize);

                // Rate limit the request
                rateLimiter.acquire();

                // Fetch RSS feed
                String rssFeed = fetchUrl(url);
                if (rssFeed == null || rssFeed.isEmpty()) {
                    log.warn("Empty RSS feed response for Form 13F");
                    break;
                }

                // Parse RSS feed to extract accession numbers and URLs
                List<FilingEntry> entries = parseRssFeed(rssFeed, date);

                if (entries.isEmpty()) {
                    log.debug("No more Form 13Fs found in RSS feed");
                    break;
                }

                log.debug("Found {} Form 13F entries in RSS feed", entries.size());

                // Download and process each filing
                for (FilingEntry entry : entries) {
                    try {
                        if (downloadAndProcess(entry.accessionNumber, entry.url, entry.filingDate)) {
                            downloaded++;
                        }

                        // Small delay between downloads
                        sleep(100);

                    } catch (Exception e) {
                        log.error("Error processing Form 13F {}", entry.accessionNumber, e);
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
                log.error("Error fetching Form 13F RSS feed", e);
                break;
            }
        }

        return downloaded;
    }

    /**
     * Download and process a single Form 13F filing
     */
    private boolean downloadAndProcess(String accessionNumber, String url, LocalDate filingDate) {
        // Check if already downloaded
        if (downloadHistoryRepository.existsByAccessionNumber(accessionNumber)) {
            log.trace("Skipping already downloaded: {}", accessionNumber);
            recordDownloadHistory(accessionNumber, url, "13F", filingDate, ProcessingStatus.SKIPPED, null, null);
            return false;
        }

        long startTime = System.currentTimeMillis();
        DownloadHistory history = null;

        try {
            // Create download history record
            history = recordDownloadHistory(accessionNumber, url, "13F", filingDate,
                ProcessingStatus.DOWNLOADING, null, null);

            // Rate limit the download
            rateLimiter.acquire();

            // Download the Form 13F XML
            String xml = fetchUrl(url);

            if (xml == null || xml.isEmpty()) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Empty response", null);
                return false;
            }

            // Update status to parsing
            history.setStatus(ProcessingStatus.PARSING);
            downloadHistoryRepository.save(history);

            // Parse the Form 13F
            Form13F form13F = form13FService.parseForm13F(xml);

            if (form13F == null || form13F.getAccessionNumber() == null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Parsing returned null or invalid form", null);
                return false;
            }

            // Save to Elasticsearch
            form13FRepository.save(form13F);

            // Update status to completed
            long duration = System.currentTimeMillis() - startTime;
            updateDownloadHistory(history, ProcessingStatus.COMPLETED, null, duration);

            log.debug("Successfully processed Form 13F: {} ({} holdings, {}ms)",
                accessionNumber, form13F.getHoldingsCount(), duration);
            return true;

        } catch (Exception e) {
            log.error("Error processing Form 13F {}", accessionNumber, e);

            if (history != null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, e.getMessage(), null);
            }

            return false;
        }
    }

    /**
     * Parse RSS feed to extract Form 13F filing entries
     */
    private List<FilingEntry> parseRssFeed(String rssFeed, LocalDate targetDate) {
        List<FilingEntry> entries = new ArrayList<>();

        try {
            // Parse Atom feed entries
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
            log.error("Error parsing Form 13F RSS feed", e);
        }

        return entries;
    }

    /**
     * Build RSS feed URL for Form 13F
     */
    private String buildRssFeedUrl(LocalDate date, int start, int count) {
        // Form 13F can be 13F-HR (holdings report) or 13F-NT (notice)
        String url = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&type=13F&company=&dateb=&owner=include&start="
            + start + "&count=" + count + "&output=atom";

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
    private DownloadHistory recordDownloadHistory(String accessionNumber, String url, String formType,
                                                    LocalDate filingDate, ProcessingStatus status,
                                                    String errorMessage, Long durationMs) {
        DownloadHistory history = DownloadHistory.builder()
            .accessionNumber(accessionNumber)
            .formType(formType)
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
     * Get quarter end date
     */
    private LocalDate getQuarterEndDate(int year, int quarter) {
        return switch (quarter) {
            case 1 -> LocalDate.of(year, 3, 31);
            case 2 -> LocalDate.of(year, 6, 30);
            case 3 -> LocalDate.of(year, 9, 30);
            case 4 -> LocalDate.of(year, 12, 31);
            default -> throw new IllegalArgumentException("Invalid quarter: " + quarter);
        };
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
