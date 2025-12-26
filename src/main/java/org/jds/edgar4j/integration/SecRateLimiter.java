package org.jds.edgar4j.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SecRateLimiter {

    private static final int MAX_REQUESTS_PER_SECOND = 10;
    private static final long WINDOW_SIZE_MS = 1000;

    private final Semaphore semaphore;
    private volatile Instant windowStart;
    private volatile int requestsInWindow;

    public SecRateLimiter() {
        this.semaphore = new Semaphore(1);
        this.windowStart = Instant.now();
        this.requestsInWindow = 0;
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
        try {
            waitIfNeeded();
        } finally {
            semaphore.release();
        }
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (!semaphore.tryAcquire(timeout, unit)) {
            return false;
        }
        try {
            waitIfNeeded();
            return true;
        } finally {
            semaphore.release();
        }
    }

    private void waitIfNeeded() throws InterruptedException {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(windowStart, now);

        if (elapsed.toMillis() >= WINDOW_SIZE_MS) {
            windowStart = now;
            requestsInWindow = 0;
        }

        if (requestsInWindow >= MAX_REQUESTS_PER_SECOND) {
            long sleepTime = WINDOW_SIZE_MS - elapsed.toMillis();
            if (sleepTime > 0) {
                log.debug("Rate limit reached, sleeping for {} ms", sleepTime);
                Thread.sleep(sleepTime);
            }
            windowStart = Instant.now();
            requestsInWindow = 0;
        }

        requestsInWindow++;
    }

    public int getAvailablePermits() {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(windowStart, now);

        if (elapsed.toMillis() >= WINDOW_SIZE_MS) {
            return MAX_REQUESTS_PER_SECOND;
        }

        return Math.max(0, MAX_REQUESTS_PER_SECOND - requestsInWindow);
    }
}
