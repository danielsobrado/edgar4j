package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort.WorkerTaskCounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerParentJobServiceImplTest {

    @Mock
    private DownloadJobDataPort downloadJobDataPort;

    @Mock
    private WorkerTaskDataPort workerTaskDataPort;

    @Test
    void refreshProgressDoesNotOverwriteBusinessCounters() {
        WorkerParentJobServiceImpl service = new WorkerParentJobServiceImpl(downloadJobDataPort, workerTaskDataPort);
        DownloadJob job = DownloadJob.builder()
                .id("job-1")
                .status(JobStatus.IN_PROGRESS)
                .filesDownloaded(500)
                .totalFiles(1_000)
                .build();
        when(downloadJobDataPort.findById("job-1")).thenReturn(Optional.of(job));
        when(workerTaskDataPort.countByParentDownloadJobId("job-1"))
                .thenReturn(new WorkerTaskCounts(1, 0, 0, 1, 0, 0));

        service.refreshProgress("job-1");

        assertEquals(50, job.getProgress());
        assertEquals(500, job.getFilesDownloaded());
        assertEquals(1_000, job.getTotalFiles());
        verify(downloadJobDataPort).save(job);
    }

    @Test
    void terminalJobsAreNeverMutated() {
        WorkerParentJobServiceImpl service = new WorkerParentJobServiceImpl(downloadJobDataPort, workerTaskDataPort);
        DownloadJob job = DownloadJob.builder()
                .id("job-1")
                .status(JobStatus.CANCELLED)
                .progress(20)
                .build();
        when(downloadJobDataPort.findById("job-1")).thenReturn(Optional.of(job));

        service.refreshProgress("job-1");

        assertEquals(20, job.getProgress());
        verify(workerTaskDataPort, never()).countByParentDownloadJobId("job-1");
        verify(downloadJobDataPort, never()).save(job);
    }
}
