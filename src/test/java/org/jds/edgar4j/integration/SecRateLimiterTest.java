package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecRateLimiterTest {

    @Test
    @DisplayName("acquire should throttle requests that exceed the per-second window")
    void acquireShouldThrottleRequestsThatExceedThePerSecondWindow() throws InterruptedException {
        SecRateLimiter limiter = new SecRateLimiter(1);

        long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis >= 900L);
    }
}
