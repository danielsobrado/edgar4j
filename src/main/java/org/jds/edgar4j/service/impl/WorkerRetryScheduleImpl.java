package org.jds.edgar4j.service.impl;

import java.time.Duration;
import java.time.Instant;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerRetrySchedule;
import org.springframework.stereotype.Service;

@Service
public class WorkerRetryScheduleImpl implements WorkerRetrySchedule {

    private static final int MAX_SHIFT = 30;

    private final DistributedWorkerProperties properties;

    public WorkerRetryScheduleImpl(DistributedWorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Instant nextAttemptAt(WorkerTask task, Instant now) {
        Duration base = properties.getCoordinator().getRetryBackoff();
        Duration maximum = properties.getCoordinator().getRetryBackoffMax();
        int exponent = Math.max(0, Math.min(task.getAttemptCount() - 1, MAX_SHIFT));
        Duration delay;
        try {
            delay = base.multipliedBy(1L << exponent);
        } catch (ArithmeticException e) {
            delay = maximum;
        }
        if (delay.compareTo(maximum) > 0) {
            delay = maximum;
        }
        return now.plus(delay);
    }
}
