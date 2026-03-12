package org.jds.edgar4j.job;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jds.edgar4j.service.Sp500Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class Sp500SyncJob {

    private final Sp500Service sp500Service;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${edgar4j.jobs.sp500-sync.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${edgar4j.jobs.sp500-sync.cron:0 0 3 * * SUN}")
    public void syncSp500() {
        if (!enabled) {
            log.debug("S&P 500 sync job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("S&P 500 sync job is already running, skipping this execution");
            return;
        }

        try {
            log.info("Starting S&P 500 sync job at {}", LocalDateTime.now());
            long startTime = System.currentTimeMillis();

            sp500Service.syncFromWikipedia();

            long duration = System.currentTimeMillis() - startTime;
            log.info("S&P 500 sync job completed in {} ms", duration);
        } catch (Exception e) {
            log.error("Error during S&P 500 sync job", e);
        } finally {
            isRunning.set(false);
        }
    }

    public void triggerSync() {
        log.info("Manual S&P 500 sync triggered");
        syncSp500();
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
