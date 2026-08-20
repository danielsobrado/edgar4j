package org.jds.edgar4j.integration;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SecRateLimiter {

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final Semaphore semaphore = new Semaphore(1, true);
    private final long minimumIntervalNanos;
    private volatile long nextPermitNanos;

    public SecRateLimiter(@Value("${edgar4j.sec.rate-limit-per-second:10}") int maxRequestsPerSecond) {
        int safeRate = Math.max(1, maxRequestsPerSecond);
        this.minimumIntervalNanos = Math.max(
                1L,
                (NANOS_PER_SECOND + safeRate - 1L) / safeRate);
        this.nextPermitNanos = System.nanoTime();
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
        try {
            waitForPermit(Long.MAX_VALUE);
        } finally {
            semaphore.release();
        }
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        long timeoutNanos = Math.max(0L, unit.toNanos(timeout));
        long startedAt = System.nanoTime();
        if (!semaphore.tryAcquire(timeoutNanos, TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            long elapsed = Math.max(0L, System.nanoTime() - startedAt);
            long remaining = Math.max(0L, timeoutNanos - elapsed);
            return waitForPermit(remaining);
        } finally {
            semaphore.release();
        }
    }

    public int getAvailablePermits() {
        return System.nanoTime() >= nextPermitNanos ? 1 : 0;
    }

    private boolean waitForPermit(long maximumWaitNanos) throws InterruptedException {
        long now = System.nanoTime();
        long waitNanos = Math.max(0L, nextPermitNanos - now);
        if (waitNanos > maximumWaitNanos) {
            return false;
        }
        if (waitNanos > 0L) {
            log.debug("SEC request rate limited for {} ms", TimeUnit.NANOSECONDS.toMillis(waitNanos));
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
        nextPermitNanos = saturatingAdd(System.nanoTime(), minimumIntervalNanos);
        return true;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
