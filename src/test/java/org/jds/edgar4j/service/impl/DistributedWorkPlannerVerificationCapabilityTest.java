package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.DistributedWorkPlanner.DownloadTaskSpec;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributedWorkPlannerVerificationCapabilityTest {

    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerSourceResourcePolicy sourceResourcePolicy;
    @Mock
    private WorkerParentJobService parentJobService;

    @Test
    void checksumlessTaskRequiresServerOnlyTrustedSourceCapability() {
        DistributedWorkPlannerImpl planner = planner();

        WorkerTask task = planner.planDownload(spec(null));

        assertTrue(task.getRequiredCapabilities().contains(WorkerCapability.TRUSTED_SOURCE));
    }

    @Test
    void trustedChecksumMakesTaskEligibleForUntrustedRemoteWorker() {
        DistributedWorkPlannerImpl planner = planner();

        WorkerTask task = planner.planDownload(spec("a".repeat(64)));

        assertFalse(task.getRequiredCapabilities().contains(WorkerCapability.TRUSTED_SOURCE));
    }

    private DistributedWorkPlannerImpl planner() {
        when(taskDataPort.createIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new DistributedWorkPlannerImpl(
                taskDataPort,
                sourceResourcePolicy,
                parentJobService,
                new DistributedWorkerProperties(),
                Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC));
    }

    private static DownloadTaskSpec spec(String expectedSha256) {
        return new DownloadTaskSpec(
                "job-1",
                "sec:test:resource",
                WorkerSource.SEC_EDGAR,
                "https://data.sec.gov/submissions/CIK0000320193.json",
                "application/json",
                null,
                expectedSha256,
                1024 * 1024,
                10);
    }
}
