package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
class DistributedWorkPlannerImplTest {

    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerSourceResourcePolicy sourceResourcePolicy;
    @Mock
    private WorkerParentJobService parentJobService;

    @Test
    void sameResourceRequestedByDifferentJobsHasIndependentTaskOwnership() {
        when(taskDataPort.createIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DistributedWorkPlannerImpl planner = new DistributedWorkPlannerImpl(
                taskDataPort,
                sourceResourcePolicy,
                parentJobService,
                new DistributedWorkerProperties(),
                Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC));

        WorkerTask first = planner.planDownload(spec("job-a"));
        WorkerTask second = planner.planDownload(spec("job-b"));

        assertEquals(first.getResourceId(), second.getResourceId());
        assertNotEquals(first.getLogicalKey(), second.getLogicalKey());
        assertEquals("job-a", first.getParentDownloadJobId());
        assertEquals("job-b", second.getParentDownloadJobId());
    }

    private static DownloadTaskSpec spec(String parentJobId) {
        return new DownloadTaskSpec(
                parentJobId,
                "sec:company-tickers:2026-08-18",
                WorkerSource.SEC_EDGAR,
                "https://www.sec.gov/files/company_tickers.json",
                "application/json",
                null,
                null,
                1024 * 1024,
                10);
    }
}
