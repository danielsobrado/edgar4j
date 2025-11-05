package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.repository.DownloadHistoryRepository;
import org.jds.edgar4j.service.BackfillService;
import org.jds.edgar4j.service.Form4DownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of backfill service for Form 4 filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Service
public class BackfillServiceImpl implements BackfillService {

    @Autowired
    private Form4DownloadService downloadService;

    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;

    @Autowired
    private PipelineProperties properties;

    @Override
    public int backfillDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Starting backfill for date range: {} to {}", startDate, endDate);

        validateDateRange(startDate, endDate);

        return downloadService.downloadForDateRange(startDate, endDate);
    }

    @Override
    public int backfillRecentDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Days must be positive");
        }

        log.info("Starting backfill for last {} days", days);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        return backfillDateRange(startDate, endDate);
    }

    @Override
    public int autoBackfill() {
        if (!properties.isAutoBackfill()) {
            log.debug("Auto-backfill is disabled");
            return 0;
        }

        log.info("Starting auto-backfill (max {} days)", properties.getMaxBackfillDays());

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(properties.getMaxBackfillDays());

        // Find missing dates
        List<LocalDate> missingDates = findMissingDates(startDate, endDate);

        if (missingDates.isEmpty()) {
            log.info("No missing dates found for auto-backfill");
            return 0;
        }

        log.info("Found {} missing dates, starting backfill", missingDates.size());

        int totalDownloaded = 0;
        for (LocalDate date : missingDates) {
            try {
                log.debug("Backfilling date: {}", date);
                int downloaded = downloadService.downloadForDate(date);
                totalDownloaded += downloaded;

                // Add delay between dates
                Thread.sleep(properties.getBatchDelayMs());

            } catch (Exception e) {
                log.error("Error backfilling date {}", date, e);
            }
        }

        log.info("Auto-backfill completed: {} Form 4s from {} dates",
            totalDownloaded, missingDates.size());

        return totalDownloaded;
    }

    @Override
    public List<LocalDate> findMissingDates(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        List<LocalDate> missingDates = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // Skip weekends (SEC is closed)
            if (isWeekday(currentDate)) {
                long count = downloadHistoryRepository.countByFilingDate(currentDate);
                if (count == 0) {
                    missingDates.add(currentDate);
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        return missingDates;
    }

    /**
     * Validate date range
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("End date cannot be in the future");
        }
    }

    /**
     * Check if date is a weekday
     */
    private boolean isWeekday(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return dayOfWeek >= 1 && dayOfWeek <= 5; // Monday=1, Friday=5
    }
}
