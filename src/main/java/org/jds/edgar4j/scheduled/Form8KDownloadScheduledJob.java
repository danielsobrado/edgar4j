package org.jds.edgar4j.scheduled;

import java.time.LocalDate;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.service.Form8KDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job for daily Form 8-K downloads
 * Form 8-K is filed within 4 business days of a material event
 *
 * Recommended schedule: Daily at 10 PM to catch all filings from the day
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-09
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "edgar4j.form8k",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class Form8KDownloadScheduledJob {

    @Autowired
    private Form8KDownloadService downloadService;

    @Autowired
    private PipelineProperties properties;

    /**
     * Download latest Form 8-K filings daily
     * Runs daily at 10 PM to catch all filings from the day
     */
    @Scheduled(cron = "${edgar4j.form8k.schedule:0 0 22 * * ?}")
    public void downloadDailyFilings() {
        log.info("Starting scheduled Form 8-K daily download job");

        try {
            // Download filings from today
            LocalDate today = LocalDate.now();
            int downloaded = downloadService.downloadForDate(today);

            log.info("Downloaded {} Form 8-K filings for {}", downloaded, today);

            // Optionally retry failed downloads
            if (downloaded > 0) {
                log.info("Retrying failed downloads");
                int retried = downloadService.retryFailedDownloads(properties.getMaxRetries());
                log.info("Successfully retried {} failed downloads", retried);
            }

            // Log statistics
            Form8KDownloadService.DownloadStatistics stats = downloadService.getStatistics();
            log.info("Form 8-K statistics - Total: {}, Successful: {}, Failed: {}, Companies: {}, Events: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getTotalCompanies(), stats.getTotalEventItems());

        } catch (Exception e) {
            log.error("Error in scheduled Form 8-K download job", e);
        }
    }

    /**
     * Download latest Form 8-K filings multiple times per day
     * Runs every 6 hours to catch filings throughout the day
     */
    @Scheduled(cron = "0 0 0,6,12,18 * * ?")
    public void downloadLatestFilings() {
        log.info("Starting Form 8-K latest filings check");

        try {
            // Download latest 100 filings to catch recent submissions
            int downloaded = downloadService.downloadLatestFilings(100);

            if (downloaded > 0) {
                log.info("Downloaded {} new Form 8-K filings", downloaded);
            } else {
                log.debug("No new Form 8-K filings found");
            }

        } catch (Exception e) {
            log.error("Error in Form 8-K latest filings check", e);
        }
    }

    /**
     * Backfill missing Form 8-Ks from the last week
     * Runs weekly on Sundays at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void backfillWeeklyFilings() {
        log.info("Starting Form 8-K weekly backfill job");

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(7);

            int downloaded = downloadService.downloadForDateRange(startDate, endDate);

            log.info("Backfilled {} Form 8-K filings from past week ({} to {})",
                downloaded, startDate, endDate);

        } catch (Exception e) {
            log.error("Error in Form 8-K weekly backfill job", e);
        }
    }

    /**
     * Retry failed downloads periodically
     * Runs daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void retryFailedDownloads() {
        log.info("Starting Form 8-K retry failed downloads job");

        try {
            int retried = downloadService.retryFailedDownloads(properties.getMaxRetries());

            if (retried > 0) {
                log.info("Successfully retried {} failed Form 8-K downloads", retried);
            } else {
                log.debug("No failed Form 8-K downloads to retry");
            }

        } catch (Exception e) {
            log.error("Error in Form 8-K retry failed downloads job", e);
        }
    }

    /**
     * Health check - logs pipeline status every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    public void logPipelineStatus() {
        try {
            Form8KDownloadService.DownloadStatistics stats = downloadService.getStatistics();

            log.info("Form 8-K Pipeline Status - Filings: {}, Success: {}, Failed: {}, Companies: {}, Events: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getTotalCompanies(), stats.getTotalEventItems());

        } catch (Exception e) {
            log.error("Error logging Form 8-K pipeline status", e);
        }
    }
}
