package org.jds.edgar4j.service;

import java.time.LocalDate;

/**
 * Service for downloading Form 8-K current event reports from SEC EDGAR
 * Form 8-K is filed when material events occur
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-09
 */
public interface Form8KDownloadService {

    /**
     * Download Form 8-Ks for a specific date
     *
     * @param date filing date
     * @return number of Form 8-Ks downloaded
     */
    int downloadForDate(LocalDate date);

    /**
     * Download Form 8-Ks for a date range
     *
     * @param startDate start of date range
     * @param endDate end of date range
     * @return total number of Form 8-Ks downloaded
     */
    int downloadForDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Download latest Form 8-K filings from RSS feed
     *
     * @param maxCount maximum number of filings to download
     * @return number of Form 8-Ks downloaded
     */
    int downloadLatestFilings(int maxCount);

    /**
     * Download specific Form 8-K by accession number
     *
     * @param accessionNumber SEC accession number
     * @return true if downloaded successfully
     */
    boolean downloadByAccessionNumber(String accessionNumber);

    /**
     * Download latest Form 8-Ks for a specific company
     *
     * @param companyCik company CIK
     * @param maxCount maximum number of filings to download
     * @return number of Form 8-Ks downloaded
     */
    int downloadForCompany(String companyCik, int maxCount);

    /**
     * Retry failed downloads
     *
     * @param maxRetries maximum number of retries allowed
     * @return number of successfully retried downloads
     */
    int retryFailedDownloads(int maxRetries);

    /**
     * Get download statistics
     *
     * @return download statistics
     */
    DownloadStatistics getStatistics();

    /**
     * Download statistics
     */
    class DownloadStatistics {
        private final long totalDownloads;
        private final long successfulDownloads;
        private final long failedDownloads;
        private final long pendingDownloads;
        private final long skippedDownloads;
        private final long totalCompanies;
        private final long totalEventItems;

        public DownloadStatistics(long totalDownloads, long successfulDownloads, long failedDownloads,
                                   long pendingDownloads, long skippedDownloads,
                                   long totalCompanies, long totalEventItems) {
            this.totalDownloads = totalDownloads;
            this.successfulDownloads = successfulDownloads;
            this.failedDownloads = failedDownloads;
            this.pendingDownloads = pendingDownloads;
            this.skippedDownloads = skippedDownloads;
            this.totalCompanies = totalCompanies;
            this.totalEventItems = totalEventItems;
        }

        public long getTotalDownloads() { return totalDownloads; }
        public long getSuccessfulDownloads() { return successfulDownloads; }
        public long getFailedDownloads() { return failedDownloads; }
        public long getPendingDownloads() { return pendingDownloads; }
        public long getSkippedDownloads() { return skippedDownloads; }
        public long getTotalCompanies() { return totalCompanies; }
        public long getTotalEventItems() { return totalEventItems; }
    }
}
