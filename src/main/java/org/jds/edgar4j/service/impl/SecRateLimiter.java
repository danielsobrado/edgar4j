package org.jds.edgar4j.service.impl;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jds.edgar4j.config.PipelineProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiter for SEC API requests
 * SEC requires no more than 10 requests per second
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Component
public class SecRateLimiter {

    private final Semaphore semaphore;
    private final int requestsPerSecond;
    private final long intervalMs;

    @Autowired
    public SecRateLimiter(PipelineProperties properties) {
        this.requestsPerSecond = properties.getRateLimitPerSecond();
        this.semaphore = new Semaphore(requestsPerSecond);
        this.intervalMs = 1000 / requestsPerSecond;

        log.info("Initialized SEC Rate Limiter: {} requests/second", requestsPerSecond);

        // Token replenishment thread
        startTokenReplenishment();
    }

    /**
     * Acquire a permit to make an SEC API request
     * Blocks until a permit is available
     */
    public void acquire() {
        try {
            semaphore.acquire();
            log.trace("Rate limit permit acquired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rate limiter interrupted", e);
        }
    }

    /**
     * Try to acquire a permit with timeout
     * @param timeoutMs timeout in milliseconds
     * @return true if permit was acquired, false if timeout
     */
    public boolean tryAcquire(long timeoutMs) {
        try {
            boolean acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (acquired) {
                log.trace("Rate limit permit acquired");
            } else {
                log.warn("Failed to acquire rate limit permit within {}ms", timeoutMs);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Start background thread to replenish tokens at fixed rate
     */
    private void startTokenReplenishment() {
        Thread replenishmentThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMs);
                    if (semaphore.availablePermits() < requestsPerSecond) {
                        semaphore.release();
                        log.trace("Released rate limit permit (available: {})",
                            semaphore.availablePermits());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        replenishmentThread.setDaemon(true);
        replenishmentThread.setName("SEC-RateLimiter-Replenishment");
        replenishmentThread.start();
    }

    /**
     * Get current available permits
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Reset the rate limiter (for testing)
     */
    public void reset() {
        semaphore.drainPermits();
        semaphore.release(requestsPerSecond);
        log.debug("Rate limiter reset to {} permits", requestsPerSecond);
    }
}
