package org.jds.edgar4j.scheduled;

import java.time.LocalDate;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.service.Form4DownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job for daily Form 4 downloads
 * Runs daily at configured time (default: 6 PM EST)
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "edgar4j.pipeline",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class Form4DownloadScheduledJob {

    @Autowired
    private Form4DownloadService downloadService;

    @Autowired
    private PipelineProperties properties;

    /**
     * Download Form 4 filings for recent days
     * Runs according to configured schedule (default: daily at 6 PM)
     */
    @Scheduled(cron = "${edgar4j.pipeline.schedule:0 0 18 * * ?}")
    public void downloadRecentFilings() {
        if (!properties.isEnabled()) {
            log.debug("Pipeline is disabled, skipping scheduled download");
            return;
        }

        log.info("Starting scheduled Form 4 download job");

        try {
            // Download filings for the last N days (to catch any delayed filings)
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(properties.getLookbackDays());

            log.info("Downloading Form 4s from {} to {}", startDate, endDate);

            int downloaded = downloadService.downloadForDateRange(startDate, endDate);

            log.info("Scheduled download completed: {} new Form 4s", downloaded);

            // Optionally retry failed downloads
            if (downloaded > 0) {
                log.info("Retrying failed downloads");
                int retried = downloadService.retryFailedDownloads(properties.getMaxRetries());
                log.info("Successfully retried {} failed downloads", retried);
            }

            // Log statistics
            Form4DownloadService.DownloadStatistics stats = downloadService.getStatistics();
            log.info("Download statistics - Total: {}, Successful: {}, Failed: {}, Pending: {}, Skipped: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getPendingDownloads(), stats.getSkippedDownloads());

        } catch (Exception e) {
            log.error("Error in scheduled Form 4 download job", e);
        }
    }

    /**
     * Health check - logs pipeline status every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void logPipelineStatus() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            Form4DownloadService.DownloadStatistics stats = downloadService.getStatistics();

            log.info("Pipeline Status - Total: {}, Success: {}, Failed: {}, Pending: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getPendingDownloads());

        } catch (Exception e) {
            log.error("Error logging pipeline status", e);
        }
    }
}
