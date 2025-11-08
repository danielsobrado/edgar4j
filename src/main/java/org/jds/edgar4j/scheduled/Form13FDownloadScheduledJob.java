package org.jds.edgar4j.scheduled;

import java.time.LocalDate;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.service.Form13FDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job for quarterly Form 13F downloads
 * Form 13F is due 45 days after quarter end
 *
 * Recommended schedule: Run on the 15th of Feb, May, Aug, Nov (covers Q4, Q1, Q2, Q3)
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "edgar4j.form13f",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class Form13FDownloadScheduledJob {

    @Autowired
    private Form13FDownloadService downloadService;

    @Autowired
    private PipelineProperties properties;

    /**
     * Download Form 13Fs quarterly
     * Runs on 15th of Feb, May, Aug, Nov at 6 PM (covers all quarters)
     *
     * Q4 (Dec 31) -> due Feb 14 -> run Feb 15
     * Q1 (Mar 31) -> due May 15 -> run May 15
     * Q2 (Jun 30) -> due Aug 14 -> run Aug 15
     * Q3 (Sep 30) -> due Nov 14 -> run Nov 15
     */
    @Scheduled(cron = "0 0 18 15 2,5,8,11 ?")
    public void downloadQuarterlyFilings() {
        log.info("Starting scheduled Form 13F quarterly download job");

        try {
            LocalDate now = LocalDate.now();
            int currentMonth = now.getMonthValue();

            // Determine which quarter we're downloading
            int year = now.getYear();
            int quarter;

            if (currentMonth == 2) {
                // February - download Q4 from previous year
                quarter = 4;
                year = year - 1;
            } else if (currentMonth == 5) {
                // May - download Q1
                quarter = 1;
            } else if (currentMonth == 8) {
                // August - download Q2
                quarter = 2;
            } else {
                // November - download Q3
                quarter = 3;
            }

            log.info("Downloading Form 13Fs for Q{} {}", quarter, year);

            int downloaded = downloadService.downloadForQuarter(year, quarter);

            log.info("Scheduled download completed: {} new Form 13Fs", downloaded);

            // Optionally retry failed downloads
            if (downloaded > 0) {
                log.info("Retrying failed downloads");
                int retried = downloadService.retryFailedDownloads(properties.getMaxRetries());
                log.info("Successfully retried {} failed downloads", retried);
            }

            // Log statistics
            Form13FDownloadService.DownloadStatistics stats = downloadService.getStatistics();
            log.info("Form 13F statistics - Total: {}, Successful: {}, Failed: {}, Institutions: {}, Holdings: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getTotalInstitutions(), stats.getTotalHoldings());

        } catch (Exception e) {
            log.error("Error in scheduled Form 13F download job", e);
        }
    }

    /**
     * Download latest Form 13Fs daily (catches any late filings)
     * Runs daily at 8 PM
     */
    @Scheduled(cron = "${edgar4j.form13f.schedule:0 0 20 * * ?}")
    public void downloadLatestFilings() {
        log.info("Starting scheduled Form 13F latest filings check");

        try {
            // Download latest 50 filings to catch any late submissions
            int downloaded = downloadService.downloadLatestFilings(50);

            if (downloaded > 0) {
                log.info("Downloaded {} new Form 13F filings", downloaded);
            } else {
                log.debug("No new Form 13F filings found");
            }

        } catch (Exception e) {
            log.error("Error in Form 13F latest filings check", e);
        }
    }

    /**
     * Health check - logs pipeline status every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    public void logPipelineStatus() {
        try {
            Form13FDownloadService.DownloadStatistics stats = downloadService.getStatistics();

            log.info("Form 13F Pipeline Status - Filings: {}, Success: {}, Failed: {}, Institutions: {}, Holdings: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getTotalInstitutions(), stats.getTotalHoldings());

        } catch (Exception e) {
            log.error("Error logging Form 13F pipeline status", e);
        }
    }
}
