package org.jds.edgar4j.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.jds.edgar4j.integration.SecRateLimiter;
import org.jds.edgar4j.model.WorkerSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerSourceDispatchPolicyImplTest {

    @Mock
    private SecRateLimiter secRateLimiter;

    @Test
    void leaseValidationDoesNotConsumeSecRateLimitCapacity() throws Exception {
        WorkerSourceDispatchPolicyImpl policy = new WorkerSourceDispatchPolicyImpl(secRateLimiter);

        policy.reserveRemoteDispatch(WorkerSource.SEC_EDGAR);

        verify(secRateLimiter, never()).acquire();
    }

    @Test
    void sourcePermitUsesApplicationWideSecLimiter() throws Exception {
        WorkerSourceDispatchPolicyImpl policy = new WorkerSourceDispatchPolicyImpl(secRateLimiter);

        policy.reserveSourceRequest(WorkerSource.SEC_EDGAR);

        verify(secRateLimiter).acquire();
    }
}
