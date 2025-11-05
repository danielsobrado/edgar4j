package org.jds.edgar4j.service;

import java.time.LocalDate;

/**
 * Service for backfilling missing Form 4 filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public interface BackfillService {

    /**
     * Backfill Form 4s for missing dates
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return number of Form 4s downloaded
     */
    int backfillDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Backfill Form 4s for the last N days
     * @param days number of days to backfill
     * @return number of Form 4s downloaded
     */
    int backfillRecentDays(int days);

    /**
     * Check for missing dates and backfill automatically
     * @return number of Form 4s downloaded
     */
    int autoBackfill();

    /**
     * Get list of dates with no downloads
     * @param startDate start date
     * @param endDate end date
     * @return list of dates with no downloads
     */
    java.util.List<LocalDate> findMissingDates(LocalDate startDate, LocalDate endDate);

    /**
     * Backfill statistics
     */
    class BackfillStatistics {
        private int totalDays;
        private int missingDays;
        private int backfilledDays;
        private int totalFilings;

        public BackfillStatistics(int totalDays, int missingDays, int backfilledDays, int totalFilings) {
            this.totalDays = totalDays;
            this.missingDays = missingDays;
            this.backfilledDays = backfilledDays;
            this.totalFilings = totalFilings;
        }

        public int getTotalDays() { return totalDays; }
        public int getMissingDays() { return missingDays; }
        public int getBackfilledDays() { return backfilledDays; }
        public int getTotalFilings() { return totalFilings; }
    }
}
