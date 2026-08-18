package org.jds.edgar4j.job;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.ServerDownloadWorker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ServerWorkerJob {

    private final ServerDownloadWorker serverDownloadWorker;
    private final DistributedWorkerProperties properties;
    private final Executor downloadExecutor;
    private final AtomicInteger inFlight = new AtomicInteger();

    public ServerWorkerJob(
            ServerDownloadWorker serverDownloadWorker,
            DistributedWorkerProperties properties,
            @Qualifier("downloadExecutor") Executor downloadExecutor) {
        this.serverDownloadWorker = serverDownloadWorker;
        this.properties = properties;
        this.downloadExecutor = downloadExecutor;
    }

    @Scheduled(fixedDelayString = "#{@distributedWorkerProperties.serverWorker.pollInterval.toMillis()}")
    public void dispatch() {
        if (!properties.isEnabled() || !properties.getServerWorker().isEnabled()) {
            return;
        }

        int available = Math.max(0, properties.getServerWorker().getMaxConcurrency() - inFlight.get());
        for (int i = 0; i < available; i++) {
            if (inFlight.incrementAndGet() > properties.getServerWorker().getMaxConcurrency()) {
                inFlight.decrementAndGet();
                break;
            }
            try {
                downloadExecutor.execute(this::runOne);
            } catch (RejectedExecutionException e) {
                inFlight.decrementAndGet();
                log.debug("Server worker dispatch skipped because download executor is saturated");
                break;
            }
        }
    }

    private void runOne() {
        try {
            serverDownloadWorker.drain(1);
        } catch (RuntimeException e) {
            log.error("Server worker dispatch failed", e);
        } finally {
            inFlight.decrementAndGet();
        }
    }
}
