package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Service for downloading Insider Forms (3, 4, 5) from SEC
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
public interface InsiderFormDownloadService {

    /**
     * Download and process insider forms for a specific date
     * @param date the filing date
     * @param formTypes set of form types to download ("3", "4", "5")
     * @return number of filings processed
     */
    int downloadForDate(LocalDate date, Set<String> formTypes);

    /**
     * Download and process insider forms for a date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param formTypes set of form types to download ("3", "4", "5")
     * @return total number of filings processed
     */
    int downloadForDateRange(LocalDate startDate, LocalDate endDate, Set<String> formTypes);

    /**
     * Download latest insider forms (from RSS feed)
     * @param formTypes set of form types to download ("3", "4", "5")
     * @param maxCount maximum number of filings to download per form type
     * @return number of filings processed
     */
    int downloadLatestFilings(Set<String> formTypes, int maxCount);

    /**
     * Download a specific form by accession number
     * @param accessionNumber the accession number
     * @param formType the form type ("3", "4", or "5")
     * @return true if successful, false otherwise
     */
    boolean downloadByAccessionNumber(String accessionNumber, String formType);

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
        private long form3Count;
        private long form4Count;
        private long form5Count;

        public DownloadStatistics(long totalDownloads, long successfulDownloads,
                                  long failedDownloads, long pendingDownloads, long skippedDownloads,
                                  long form3Count, long form4Count, long form5Count) {
            this.totalDownloads = totalDownloads;
            this.successfulDownloads = successfulDownloads;
            this.failedDownloads = failedDownloads;
            this.pendingDownloads = pendingDownloads;
            this.skippedDownloads = skippedDownloads;
            this.form3Count = form3Count;
            this.form4Count = form4Count;
            this.form5Count = form5Count;
        }

        // Getters
        public long getTotalDownloads() { return totalDownloads; }
        public long getSuccessfulDownloads() { return successfulDownloads; }
        public long getFailedDownloads() { return failedDownloads; }
        public long getPendingDownloads() { return pendingDownloads; }
        public long getSkippedDownloads() { return skippedDownloads; }
        public long getForm3Count() { return form3Count; }
        public long getForm4Count() { return form4Count; }
        public long getForm5Count() { return form5Count; }
    }
}
