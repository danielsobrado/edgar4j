package org.jds.edgar4j.service;

import java.util.Locale;

import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerPlatform;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class WorkerMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter sessionsOpened;
    private final Counter leasesExpired;
    private final Counter artifactBytes;

    public WorkerMetrics(MeterRegistry meterRegistry, WorkerTaskDataPort taskDataPort) {
        this.meterRegistry = meterRegistry;
        this.sessionsOpened = Counter.builder("edgar4j.worker.sessions.opened").register(meterRegistry);
        this.leasesExpired = Counter.builder("edgar4j.worker.leases.expired").register(meterRegistry);
        this.artifactBytes = Counter.builder("edgar4j.worker.artifact.bytes").register(meterRegistry);

        for (WorkerTaskStatus status : WorkerTaskStatus.values()) {
            Gauge.builder("edgar4j.worker.tasks", taskDataPort, port -> port.countByStatus(status))
                    .tag("status", tag(status.name()))
                    .register(meterRegistry);
        }
    }

    public void sessionOpened() {
        sessionsOpened.increment();
    }

    public void leaseIssued(WorkerSource source, WorkerPlatform platform) {
        meterRegistry.counter(
                "edgar4j.worker.leases.issued",
                "source", tag(source.name()),
                "platform", tag(platform.name()))
                .increment();
    }

    public void leasesExpired(int count) {
        if (count > 0) {
            leasesExpired.increment(count);
        }
    }

    public void taskCompleted(WorkerSource source) {
        meterRegistry.counter("edgar4j.worker.tasks.completed", "source", tag(source.name())).increment();
    }

    public void taskFailed(WorkerFailureCode failureCode) {
        meterRegistry.counter("edgar4j.worker.tasks.failed", "code", tag(failureCode.name())).increment();
    }

    public void artifactAccepted(long bytes) {
        if (bytes > 0) {
            artifactBytes.increment(bytes);
        }
    }

    public void artifactRejected(WorkerFailureCode failureCode) {
        meterRegistry.counter("edgar4j.worker.artifacts.rejected", "code", tag(failureCode.name())).increment();
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
