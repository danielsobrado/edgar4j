package org.jds.edgar4j.service;

import java.time.LocalDate;

/**
 * Service for downloading Form 13F filings from SEC
 * Form 13F is filed quarterly by institutional investors
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
public interface Form13FDownloadService {

    /**
     * Download Form 13Fs for a specific quarter
     * @param year the year
     * @param quarter the quarter (1-4)
     * @return number of filings processed
     */
    int downloadForQuarter(int year, int quarter);

    /**
     * Download Form 13Fs for a date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return total number of filings processed
     */
    int downloadForDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Download latest Form 13Fs (from RSS feed)
     * @param maxCount maximum number of filings to download
     * @return number of filings processed
     */
    int downloadLatestFilings(int maxCount);

    /**
     * Download a specific Form 13F by accession number
     * @param accessionNumber the accession number
     * @return true if successful, false otherwise
     */
    boolean downloadByAccessionNumber(String accessionNumber);

    /**
     * Download latest Form 13F for a specific institution
     * @param filerCik the institution's CIK
     * @return true if successful, false otherwise
     */
    boolean downloadLatestForInstitution(String filerCik);

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
     * Download statistics for Form 13F
     */
    class DownloadStatistics {
        private long totalDownloads;
        private long successfulDownloads;
        private long failedDownloads;
        private long pendingDownloads;
        private long skippedDownloads;
        private long totalInstitutions;
        private long totalHoldings;

        public DownloadStatistics(long totalDownloads, long successfulDownloads,
                                  long failedDownloads, long pendingDownloads, long skippedDownloads,
                                  long totalInstitutions, long totalHoldings) {
            this.totalDownloads = totalDownloads;
            this.successfulDownloads = successfulDownloads;
            this.failedDownloads = failedDownloads;
            this.pendingDownloads = pendingDownloads;
            this.skippedDownloads = skippedDownloads;
            this.totalInstitutions = totalInstitutions;
            this.totalHoldings = totalHoldings;
        }

        // Getters
        public long getTotalDownloads() { return totalDownloads; }
        public long getSuccessfulDownloads() { return successfulDownloads; }
        public long getFailedDownloads() { return failedDownloads; }
        public long getPendingDownloads() { return pendingDownloads; }
        public long getSkippedDownloads() { return skippedDownloads; }
        public long getTotalInstitutions() { return totalInstitutions; }
        public long getTotalHoldings() { return totalHoldings; }
    }
}
