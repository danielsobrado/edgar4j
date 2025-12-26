package org.jds.edgar4j.job;

import org.jds.edgar4j.service.DownloadTickersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled job for syncing company tickers from SEC.
 * Runs daily to keep ticker data up-to-date.
 */
@Component
public class TickerSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TickerSyncJob.class);

    private final DownloadTickersService downloadTickersService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${edgar4j.jobs.ticker-sync.enabled:true}")
    private boolean enabled;

    public TickerSyncJob(DownloadTickersService downloadTickersService) {
        this.downloadTickersService = downloadTickersService;
    }

    /**
     * Sync all tickers daily at 6 AM.
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "${edgar4j.jobs.ticker-sync.cron:0 0 6 * * *}")
    public void syncTickers() {
        if (!enabled) {
            log.debug("Ticker sync job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Ticker sync job is already running, skipping this execution");
            return;
        }

        try {
            log.info("Starting ticker sync job at {}", LocalDateTime.now());
            long startTime = System.currentTimeMillis();

            // Download all tickers
            downloadTickersService.downloadAllTickers();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Ticker sync job completed in {} ms", duration);

        } catch (Exception e) {
            log.error("Error during ticker sync job", e);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Manual trigger for ticker sync.
     */
    public void triggerSync() {
        log.info("Manual ticker sync triggered");
        syncTickers();
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
