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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.DownloadHistory.ProcessingStatus;
import org.jds.edgar4j.model.InsiderForm;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import org.jds.edgar4j.repository.InsiderFormRepository;
import org.jds.edgar4j.service.InsiderFormDownloadService;
import org.jds.edgar4j.service.InsiderFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Insider Form download service
 * Downloads Insider Forms (3, 4, 5) from SEC EDGAR and processes them
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Service
public class InsiderFormDownloadServiceImpl implements InsiderFormDownloadService {

    @Autowired
    private PipelineProperties properties;

    @Autowired
    private SecRateLimiter rateLimiter;

    @Autowired
    private InsiderFormService insiderFormService;

    @Autowired
    private InsiderFormRepository insiderFormRepository;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    private final HttpClient httpClient;
    private static final Pattern ACCESSION_PATTERN = Pattern.compile("(\\d{10})-(\\d{2})-(\\d{6})");

    public InsiderFormDownloadServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public int downloadForDate(LocalDate date, Set<String> formTypes) {
        log.info("Starting insider form download for date: {} (types: {})", date, formTypes);

        int totalDownloaded = 0;
        for (String formType : formTypes) {
            try {
                totalDownloaded += downloadFromRssFeed(formType, date, 0, properties.getBatchSize());
            } catch (Exception e) {
                log.error("Error downloading Form {} for date {}", formType, date, e);
            }
        }

        return totalDownloaded;
    }

    @Override
    public int downloadForDateRange(LocalDate startDate, LocalDate endDate, Set<String> formTypes) {
        log.info("Starting insider form download for date range: {} to {} (types: {})",
            startDate, endDate, formTypes);

        int totalDownloaded = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            totalDownloaded += downloadForDate(currentDate, formTypes);

            // Add delay between dates to respect rate limits
            if (!currentDate.equals(endDate)) {
                sleep(properties.getBatchDelayMs());
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Completed date range download: {} insider forms", totalDownloaded);
        return totalDownloaded;
    }

    @Override
    public int downloadLatestFilings(Set<String> formTypes, int maxCount) {
        log.info("Downloading latest {} filings for forms: {}", maxCount, formTypes);

        int totalDownloaded = 0;
        for (String formType : formTypes) {
            try {
                totalDownloaded += downloadFromRssFeed(formType, null, 0, maxCount);
            } catch (Exception e) {
                log.error("Error downloading latest Form {}s", formType, e);
            }
        }

        return totalDownloaded;
    }

    @Override
    public boolean downloadByAccessionNumber(String accessionNumber, String formType) {
        log.info("Downloading Form {} by accession number: {}", formType, accessionNumber);

        // Check if already downloaded
        if (downloadHistoryRepository.existsByAccessionNumber(accessionNumber)) {
            log.debug("Accession number {} already downloaded, skipping", accessionNumber);
            return true;
        }

        try {
            // Build URL for the filing
            String url = buildFilingUrl(accessionNumber);

            return downloadAndProcess(accessionNumber, url, formType, LocalDate.now());

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

                // Extract form type from accession number or default to 4
                String formType = history.getFormType() != null ? history.getFormType() : "4";
                boolean success = downloadByAccessionNumber(history.getAccessionNumber(), formType);
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

        // Count by form type
        long form3Count = insiderFormRepository.countByFormType("3");
        long form4Count = insiderFormRepository.countByFormType("4");
        long form5Count = insiderFormRepository.countByFormType("5");

        return new DownloadStatistics(total, successful, failed, pending, skipped,
            form3Count, form4Count, form5Count);
    }

    /**
     * Download insider forms from SEC RSS feed
     */
    private int downloadFromRssFeed(String formType, LocalDate date, int start, int count) {
        int downloaded = 0;
        int currentStart = start;
        int remaining = count;

        while (remaining > 0) {
            try {
                int batchSize = Math.min(remaining, properties.getBatchSize());

                // Build RSS feed URL
                String url = buildRssFeedUrl(formType, date, currentStart, batchSize);

                log.debug("Fetching Form {} RSS feed: start={}, count={}", formType, currentStart, batchSize);

                // Rate limit the request
                rateLimiter.acquire();

                // Fetch RSS feed
                String rssFeed = fetchUrl(url);
                if (rssFeed == null || rssFeed.isEmpty()) {
                    log.warn("Empty RSS feed response for Form {}", formType);
                    break;
                }

                // Parse RSS feed to extract accession numbers and URLs
                List<FilingEntry> entries = parseRssFeed(rssFeed, formType, date);

                if (entries.isEmpty()) {
                    log.debug("No more Form {}s found in RSS feed", formType);
                    break;
                }

                log.debug("Found {} Form {} entries in RSS feed", entries.size(), formType);

                // Download and process each filing
                for (FilingEntry entry : entries) {
                    try {
                        if (downloadAndProcess(entry.accessionNumber, entry.url,
                                entry.formType, entry.filingDate)) {
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
                log.error("Error fetching Form {} RSS feed", formType, e);
                break;
            }
        }

        return downloaded;
    }

    /**
     * Download and process a single insider form filing
     */
    private boolean downloadAndProcess(String accessionNumber, String url, String formType, LocalDate filingDate) {
        // Check if already downloaded
        if (downloadHistoryRepository.existsByAccessionNumber(accessionNumber)) {
            log.trace("Skipping already downloaded: {}", accessionNumber);
            recordDownloadHistory(accessionNumber, url, formType, filingDate, ProcessingStatus.SKIPPED, null, null);
            return false;
        }

        long startTime = System.currentTimeMillis();
        DownloadHistory history = null;

        try {
            // Create download history record
            history = recordDownloadHistory(accessionNumber, url, formType, filingDate,
                ProcessingStatus.DOWNLOADING, null, null);

            // Rate limit the download
            rateLimiter.acquire();

            // Download the form XML
            String xml = fetchUrl(url);

            if (xml == null || xml.isEmpty()) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Empty response", null);
                return false;
            }

            // Update status to parsing
            history.setStatus(ProcessingStatus.PARSING);
            downloadHistoryRepository.save(history);

            // Parse the insider form
            InsiderForm form = insiderFormService.parseInsiderForm(xml, formType);

            if (form == null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, "Parsing returned null", null);
                return false;
            }

            // Save to Elasticsearch
            insiderFormRepository.save(form);

            // Update status to completed
            long duration = System.currentTimeMillis() - startTime;
            updateDownloadHistory(history, ProcessingStatus.COMPLETED, null, duration);

            log.debug("Successfully processed Form {}: {} ({}ms)", formType, accessionNumber, duration);
            return true;

        } catch (Exception e) {
            log.error("Error processing Form {} {}", formType, accessionNumber, e);

            if (history != null) {
                updateDownloadHistory(history, ProcessingStatus.FAILED, e.getMessage(), null);
            }

            return false;
        }
    }

    /**
     * Parse RSS feed to extract filing entries
     */
    private List<FilingEntry> parseRssFeed(String rssFeed, String formType, LocalDate targetDate) {
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

                entries.add(new FilingEntry(accessionNumber, link, formType, filingDate));
            }

        } catch (Exception e) {
            log.error("Error parsing RSS feed for Form {}", formType, e);
        }

        return entries;
    }

    /**
     * Build RSS feed URL
     */
    private String buildRssFeedUrl(String formType, LocalDate date, int start, int count) {
        String url = properties.getRssFeedUrl()
            .replace("{type}", formType)
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
        final String formType;
        final LocalDate filingDate;

        FilingEntry(String accessionNumber, String url, String formType, LocalDate filingDate) {
            this.accessionNumber = accessionNumber;
            this.url = url;
            this.formType = formType;
            this.filingDate = filingDate;
        }
    }
}
