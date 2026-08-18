package org.jds.edgar4j.dto.worker;

import java.util.List;

public record WorkerLeaseResponse(
        List<WorkerTaskResponse> tasks,
        int retryAfterSeconds) {
}
