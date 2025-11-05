package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for downloading Form 4 filings from SEC
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public interface Form4DownloadService {

    /**
     * Download and process Form 4 filings for a specific date
     * @param date the filing date
     * @return number of filings processed
     */
    int downloadForDate(LocalDate date);

    /**
     * Download and process Form 4 filings for a date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return total number of filings processed
     */
    int downloadForDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Download latest Form 4 filings (from RSS feed)
     * @param maxCount maximum number of filings to download
     * @return number of filings processed
     */
    int downloadLatestFilings(int maxCount);

    /**
     * Download a specific Form 4 by accession number
     * @param accessionNumber the accession number
     * @return true if successful, false otherwise
     */
    boolean downloadByAccessionNumber(String accessionNumber);

    /**
     * Retry failed downloads
     * @param maxRetries maximum number of retries
     * @return number of successfully retried downloads
     */
    int retryFailedDownloads(int maxRetries);

    /**
     * Get download statistics
     * @return download statistics
     */
    DownloadStatistics getStatistics();

    /**
     * Download statistics
     */
    class DownloadStatistics {
        private long totalDownloads;
        private long successfulDownloads;
        private long failedDownloads;
        private long pendingDownloads;
        private long skippedDownloads;

        public DownloadStatistics(long totalDownloads, long successfulDownloads,
                                  long failedDownloads, long pendingDownloads, long skippedDownloads) {
            this.totalDownloads = totalDownloads;
            this.successfulDownloads = successfulDownloads;
            this.failedDownloads = failedDownloads;
            this.pendingDownloads = pendingDownloads;
            this.skippedDownloads = skippedDownloads;
        }

        // Getters
        public long getTotalDownloads() { return totalDownloads; }
        public long getSuccessfulDownloads() { return successfulDownloads; }
        public long getFailedDownloads() { return failedDownloads; }
        public long getPendingDownloads() { return pendingDownloads; }
        public long getSkippedDownloads() { return skippedDownloads; }
    }
}
