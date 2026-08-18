package org.jds.edgar4j.service;

import java.time.Instant;

import org.jds.edgar4j.model.WorkerTask;

public interface WorkerRetrySchedule {

    Instant nextAttemptAt(WorkerTask task, Instant now);
}
