package org.jds.edgar4j.service.impl;

import java.util.EnumSet;
import java.util.Set;

import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.service.WorkerRetryPolicy;
import org.springframework.stereotype.Service;

@Service
public class WorkerRetryPolicyImpl implements WorkerRetryPolicy {

    private static final Set<WorkerFailureCode> RETRYABLE = EnumSet.of(
            WorkerFailureCode.SOURCE_TIMEOUT,
            WorkerFailureCode.SOURCE_RATE_LIMITED,
            WorkerFailureCode.NETWORK_UNAVAILABLE,
            WorkerFailureCode.CHECKSUM_MISMATCH,
            WorkerFailureCode.CONTENT_INVALID,
            WorkerFailureCode.UPLOAD_FAILED,
            WorkerFailureCode.LEASE_EXPIRED,
            WorkerFailureCode.WORKER_CANCELLED,
            WorkerFailureCode.INTERNAL_ERROR);

    @Override
    public boolean isRetryable(WorkerFailureCode failureCode) {
        return failureCode != null && RETRYABLE.contains(failureCode);
    }
}
