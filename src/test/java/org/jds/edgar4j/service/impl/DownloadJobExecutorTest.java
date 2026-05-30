package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.jds.edgar4j.service.DownloadBulkDataService.BulkDownloadResult;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.jds.edgar4j.service.UsaSpendingDownloadService;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadJobExecutorTest {

    @Mock
    private DownloadJobDataPort downloadJobRepository;

    @Mock
    private DownloadTickersService downloadTickersService;

    @Mock
    private DownloadSubmissionsService downloadSubmissionsService;

    @Mock
    private DownloadBulkDataService downloadBulkDataService;

    @Mock
    private RemoteEdgarService remoteEdgarService;

    @Mock
    private UsaSpendingDownloadService usaSpendingDownloadService;

    @Mock
    private Edgar4JProperties edgar4JProperties;

    @InjectMocks
    private DownloadJobExecutor downloadJobExecutor;

    private void withRemoteSyncDefaults(int chunkDays, int pauseSeconds) {
        Edgar4JProperties.RemoteSync remoteSync = new Edgar4JProperties.RemoteSync();
        remoteSync.setChunkDays(chunkDays);
        remoteSync.setPauseSeconds(pauseSeconds);
        when(edgar4JProperties.getRemoteSync()).thenReturn(remoteSync);
    }

    @Test
    @DisplayName("executeDownloadAsync should sync all companies matching a remote filing search")
    void executeDownloadAsyncShouldSyncRemoteFilingMatches() {
        withRemoteSyncDefaults(0, 0);
        DownloadJob job = DownloadJob.builder()
                .id("job-1")
                .type(JobType.REMOTE_FILINGS_SYNC)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();

        when(downloadJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(downloadJobRepository.save(any(DownloadJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(remoteEdgarService.findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class)))
                .thenReturn(List.of("0001234567", "0002345678"));
        when(downloadSubmissionsService.downloadSubmissions("0001234567")).thenReturn(5L);
        when(downloadSubmissionsService.downloadSubmissions("0002345678")).thenReturn(7L);

        DownloadRequest request = DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.REMOTE_FILINGS_SYNC)
                .formType("13F")
                .dateFrom(LocalDate.of(2026, 3, 1))
                .dateTo(LocalDate.of(2026, 3, 12))
                .chunkDays(0)
                .build();

        downloadJobExecutor.executeDownloadAsync("job-1", request);

        ArgumentCaptor<RemoteFilingSearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(RemoteFilingSearchRequest.class);
        verify(remoteEdgarService).findMatchingCompanyCiks(searchRequestCaptor.capture());
        assertEquals("13F", searchRequestCaptor.getValue().getFormType());
        assertEquals(LocalDate.of(2026, 3, 1), searchRequestCaptor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 3, 12), searchRequestCaptor.getValue().getDateTo());

        verify(downloadSubmissionsService).downloadSubmissions("0001234567");
        verify(downloadSubmissionsService).downloadSubmissions("0002345678");
        verify(downloadJobRepository, atLeastOnce()).save(eq(job));

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals(12L, job.getFilesDownloaded());
        assertEquals(2L, job.getTotalFiles());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    @DisplayName("executeDownloadAsync should run filing-date sync when FILING_DATE mode is selected")
    void executeDownloadAsyncShouldSyncRemoteFilingsByDateWhenRequested() {
        withRemoteSyncDefaults(0, 0);
        DownloadJob job = DownloadJob.builder()
                .id("job-2")
                .type(JobType.REMOTE_FILINGS_SYNC)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();

        when(downloadJobRepository.findById("job-2")).thenReturn(Optional.of(job));
        when(downloadJobRepository.save(any(DownloadJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(remoteEdgarService.findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class)))
                .thenReturn(List.of("0001234567"));
        when(downloadSubmissionsService.downloadSubmissions("0001234567", "13F", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12)))
                .thenReturn(3L);

        DownloadRequest request = DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.REMOTE_FILINGS_SYNC)
                .formType("13F")
                .remoteFilingSyncMode(DownloadRequest.RemoteFilingSyncMode.FILING_DATE)
                .chunkDays(0)
                .dateFrom(LocalDate.of(2026, 3, 1))
                .dateTo(LocalDate.of(2026, 3, 12))
                .build();

        downloadJobExecutor.executeDownloadAsync("job-2", request);

        verify(downloadSubmissionsService).downloadSubmissions("0001234567", "13F", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12));
        verify(downloadJobRepository, atLeastOnce()).save(eq(job));
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
    }

    @Test
    @DisplayName("executeRemoteFilingSync should split range into explicit day chunks")
    void executeRemoteFilingSyncShouldSplitDateRangeIntoChunks() {
        withRemoteSyncDefaults(3, 0);
        DownloadJob job = DownloadJob.builder()
                .id("job-3")
                .type(JobType.REMOTE_FILINGS_SYNC)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();

        when(downloadJobRepository.findById("job-3")).thenReturn(Optional.of(job));
        when(downloadJobRepository.save(any(DownloadJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<RemoteFilingSearchRequest> capture = new ArrayList<>();
        when(remoteEdgarService.findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class)))
                .thenAnswer(invocation -> {
                    capture.add(invocation.getArgument(0));
                    return List.of("0001234567");
                });
        when(downloadSubmissionsService.downloadSubmissions(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class))).thenReturn(1L);

        DownloadRequest request = DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.REMOTE_FILINGS_SYNC)
                .formType("13F")
                .remoteFilingSyncMode(DownloadRequest.RemoteFilingSyncMode.FILING_DATE)
                .dateFrom(LocalDate.of(2026, 3, 1))
                .dateTo(LocalDate.of(2026, 3, 6))
                .chunkDays(3)
                .build();

        downloadJobExecutor.executeDownloadAsync("job-3", request);

        verify(remoteEdgarService, times(2)).findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class));
        assertEquals(LocalDate.of(2026, 3, 1), capture.get(0).getDateFrom());
        assertEquals(LocalDate.of(2026, 3, 3), capture.get(0).getDateTo());
        assertEquals(LocalDate.of(2026, 3, 4), capture.get(1).getDateFrom());
        assertEquals(LocalDate.of(2026, 3, 6), capture.get(1).getDateTo());
    }

    @Test
    @DisplayName("executeRemoteFilingSync should stop when cancelled during chunk processing")
    void executeRemoteFilingSyncShouldStopWhenCancelled() {
        withRemoteSyncDefaults(1, 0);
        DownloadJob job = DownloadJob.builder()
                .id("job-4")
                .type(JobType.REMOTE_FILINGS_SYNC)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();

        when(downloadJobRepository.findById("job-4")).thenReturn(Optional.of(job));
        when(downloadJobRepository.save(any(DownloadJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(remoteEdgarService.findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class)))
                .thenReturn(List.of("0001234567"));

        AtomicInteger syncCalls = new AtomicInteger();
        when(downloadSubmissionsService.downloadSubmissions(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    syncCalls.incrementAndGet();
                    if (syncCalls.get() == 1) {
                        job.setStatus(JobStatus.CANCELLED);
                    }
                    return 4L;
                });

        DownloadRequest request = DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.REMOTE_FILINGS_SYNC)
                .formType("13F")
                .remoteFilingSyncMode(DownloadRequest.RemoteFilingSyncMode.FILING_DATE)
                .chunkDays(1)
                .dateFrom(LocalDate.of(2026, 3, 1))
                .dateTo(LocalDate.of(2026, 3, 3))
                .build();

        downloadJobExecutor.executeDownloadAsync("job-4", request);

        assertEquals(JobStatus.CANCELLED, job.getStatus());
        verify(remoteEdgarService, times(1)).findMatchingCompanyCiks(any(RemoteFilingSearchRequest.class));
        verify(downloadSubmissionsService, times(1)).downloadSubmissions(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class));
        assertEquals(0L, job.getFilesDownloaded());
    }

    @Test
    @DisplayName("executeDownloadAsync should complete bulk jobs with saved archive metadata")
    void executeDownloadAsyncShouldCompleteBulkJobWithMetadata() {
        DownloadJob job = DownloadJob.builder()
                .id("job-2")
                .type(JobType.BULK_COMPANY_FACTS)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();

        Path outputPath = Path.of("data", "bulk-downloads", "companyfacts.zip");
        when(downloadJobRepository.findById("job-2")).thenReturn(Optional.of(job));
        when(downloadJobRepository.save(any(DownloadJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(downloadBulkDataService.downloadBulkCompanyFactsArchive())
                .thenReturn(new BulkDownloadResult(
                        1,
                        "https://www.sec.gov/Archives/edgar/daily-index/xbrl/companyfacts.zip",
                        outputPath));

        DownloadRequest request = DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.BULK_COMPANY_FACTS)
                .build();

        downloadJobExecutor.executeDownloadAsync("job-2", request);

        verify(downloadBulkDataService).downloadBulkCompanyFactsArchive();
        verify(downloadJobRepository, atLeastOnce()).save(eq(job));

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals(1L, job.getFilesDownloaded());
        assertEquals(1L, job.getTotalFiles());
        assertEquals("https://www.sec.gov/Archives/edgar/daily-index/xbrl/companyfacts.zip", job.getSourceUrl());
        assertEquals(outputPath.toString(), job.getOutputPath());
        assertNotNull(job.getCompletedAt());
    }
}
