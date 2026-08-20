package org.jds.edgar4j.service;

public interface WorkerSourcePermitService {

    void reserve(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken);
}
