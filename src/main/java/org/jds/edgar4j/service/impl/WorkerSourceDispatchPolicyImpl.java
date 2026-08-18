package org.jds.edgar4j.service.impl;

import org.jds.edgar4j.integration.SecRateLimiter;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.service.WorkerSourceDispatchPolicy;
import org.springframework.stereotype.Service;

@Service
public class WorkerSourceDispatchPolicyImpl implements WorkerSourceDispatchPolicy {

    private final SecRateLimiter secRateLimiter;

    public WorkerSourceDispatchPolicyImpl(SecRateLimiter secRateLimiter) {
        this.secRateLimiter = secRateLimiter;
    }

    @Override
    public void reserveRemoteDispatch(WorkerSource source) {
        if (source != WorkerSource.SEC_EDGAR) {
            throw new IllegalArgumentException("Unsupported distributed worker source: " + source);
        }

        try {
            secRateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reserving source dispatch capacity", e);
        }
    }
}
