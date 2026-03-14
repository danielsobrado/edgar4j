package org.jds.edgar4j.job;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jds.edgar4j.service.DividendSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DividendSyncJob {

    private final DividendSyncService dividendSyncService;
    private final boolean enabled;
    private final int maxCompanies;
    private final boolean refreshMarketData;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public DividendSyncJob(
            DividendSyncService dividendSyncService,
            @Value("${edgar4j.jobs.dividend-sync.enabled:true}") boolean enabled,
            @Value("${edgar4j.jobs.dividend-sync.max-companies:25}") int maxCompanies,
            @Value("${edgar4j.jobs.dividend-sync.refresh-market-data:false}") boolean refreshMarketData) {
        this.dividendSyncService = dividendSyncService;
        this.enabled = enabled;
        this.maxCompanies = maxCompanies;
        this.refreshMarketData = refreshMarketData;
    }

    @Scheduled(cron = "${edgar4j.jobs.dividend-sync.cron:0 15 6 * * *}")
    public void syncTrackedCompanies() {
        if (!enabled) {
            log.debug("Dividend sync job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Dividend sync job is already running, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            log.info("Starting dividend sync job at {} for up to {} tracked companies",
                    LocalDateTime.now(), maxCompanies);
            dividendSyncService.syncTrackedCompanies(maxCompanies, refreshMarketData);
            log.info("Dividend sync job completed in {} ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Error during dividend sync job", e);
        } finally {
            isRunning.set(false);
        }
    }

    public void triggerSync() {
        log.info("Manual dividend sync triggered");
        syncTrackedCompanies();
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
