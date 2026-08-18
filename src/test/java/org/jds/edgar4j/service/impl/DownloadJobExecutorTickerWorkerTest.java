package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.jds.edgar4j.service.UsaSpendingDownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadJobExecutorTickerWorkerTest {

    @Mock
    private DownloadJobDataPort downloadJobDataPort;
    @Mock
    private DownloadTickersService downloadTickersService;
    @Mock
    private DownloadSubmissionsService downloadSubmissionsService;
    @Mock
    private DownloadBulkDataService downloadBulkDataService;
    @Mock
    private Edgar4JProperties edgar4JProperties;
    @Mock
    private RemoteEdgarService remoteEdgarService;
    @Mock
    private UsaSpendingDownloadService usaSpendingDownloadService;

    @Test
    void tickerJobPassesParentIdIntoDistributedAcquisition() {
        DownloadJob job = DownloadJob.builder()
                .id("ticker-job")
                .type(JobType.TICKERS_ALL)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        when(downloadJobDataPort.findById("ticker-job")).thenReturn(Optional.of(job));
        when(downloadJobDataPort.save(job)).thenReturn(job);
        when(downloadTickersService.downloadTickers("ticker-job")).thenReturn(3_000);

        DownloadJobExecutor executor = new DownloadJobExecutor(
                downloadJobDataPort,
                downloadTickersService,
                downloadSubmissionsService,
                downloadBulkDataService,
                edgar4JProperties,
                remoteEdgarService,
                usaSpendingDownloadService);

        executor.executeDownloadAsync(
                "ticker-job",
                DownloadRequest.builder().type(DownloadRequest.DownloadType.TICKERS_ALL).build());

        verify(downloadTickersService).downloadTickers("ticker-job");
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(3_000, job.getFilesDownloaded());
        assertEquals(100, job.getProgress());
    }
}
