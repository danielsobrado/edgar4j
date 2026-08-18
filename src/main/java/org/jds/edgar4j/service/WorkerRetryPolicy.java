package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerFailureCode;

public interface WorkerRetryPolicy {

    boolean isRetryable(WorkerFailureCode failureCode);
}
