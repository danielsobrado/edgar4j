package org.jds.edgar4j.scheduled;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.jds.edgar4j.config.PipelineProperties;
import org.jds.edgar4j.service.InsiderFormDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job for daily insider form downloads (Forms 3, 4, 5)
 * Runs daily at configured time (default: 6 PM EST)
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "edgar4j.pipeline",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class InsiderFormDownloadScheduledJob {

    @Autowired
    private InsiderFormDownloadService downloadService;

    @Autowired
    private PipelineProperties properties;

    /**
     * Download insider form filings for recent days
     * Runs according to configured schedule (default: daily at 6 PM)
     */
    @Scheduled(cron = "${edgar4j.pipeline.schedule:0 0 18 * * ?}")
    public void downloadRecentFilings() {
        if (!properties.isEnabled()) {
            log.debug("Pipeline is disabled, skipping scheduled download");
            return;
        }

        log.info("Starting scheduled insider form download job");

        try {
            // Get configured form types (default: 3, 4, 5)
            Set<String> formTypes = getFormTypes();

            // Download filings for the last N days (to catch any delayed filings)
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(properties.getLookbackDays());

            log.info("Downloading insider forms {} from {} to {}", formTypes, startDate, endDate);

            int downloaded = downloadService.downloadForDateRange(startDate, endDate, formTypes);

            log.info("Scheduled download completed: {} new insider forms", downloaded);

            // Optionally retry failed downloads
            if (downloaded > 0) {
                log.info("Retrying failed downloads");
                int retried = downloadService.retryFailedDownloads(properties.getMaxRetries());
                log.info("Successfully retried {} failed downloads", retried);
            }

            // Log statistics
            InsiderFormDownloadService.DownloadStatistics stats = downloadService.getStatistics();
            log.info("Download statistics - Total: {}, Successful: {}, Failed: {}, Pending: {}, Skipped: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getPendingDownloads(), stats.getSkippedDownloads());
            log.info("Forms breakdown - Form3: {}, Form4: {}, Form5: {}",
                stats.getForm3Count(), stats.getForm4Count(), stats.getForm5Count());

        } catch (Exception e) {
            log.error("Error in scheduled insider form download job", e);
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
            InsiderFormDownloadService.DownloadStatistics stats = downloadService.getStatistics();

            log.info("Pipeline Status - Total: {}, Success: {}, Failed: {}, Pending: {}",
                stats.getTotalDownloads(), stats.getSuccessfulDownloads(),
                stats.getFailedDownloads(), stats.getPendingDownloads());
            log.info("Forms - Form3: {}, Form4: {}, Form5: {}",
                stats.getForm3Count(), stats.getForm4Count(), stats.getForm5Count());

        } catch (Exception e) {
            log.error("Error logging pipeline status", e);
        }
    }

    /**
     * Get form types to download from configuration
     */
    private Set<String> getFormTypes() {
        Set<String> formTypes = new HashSet<>();

        // Default to all three forms
        String formTypesConfig = properties.getFormTypes();
        if (formTypesConfig == null || formTypesConfig.isEmpty()) {
            formTypes.add("3");
            formTypes.add("4");
            formTypes.add("5");
        } else {
            String[] types = formTypesConfig.split(",");
            for (String type : types) {
                formTypes.add(type.trim());
            }
        }

        return formTypes;
    }
}
