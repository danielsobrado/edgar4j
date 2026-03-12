package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.repository.DownloadJobRepository;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.jds.edgar4j.service.RemoteEdgarService;
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
    private DownloadJobRepository downloadJobRepository;

    @Mock
    private DownloadTickersService downloadTickersService;

    @Mock
    private DownloadSubmissionsService downloadSubmissionsService;

    @Mock
    private DownloadBulkDataService downloadBulkDataService;

    @Mock
    private RemoteEdgarService remoteEdgarService;

    @InjectMocks
    private DownloadJobExecutor downloadJobExecutor;

    @Test
    @DisplayName("executeDownloadAsync should sync all companies matching a remote filing search")
    void executeDownloadAsyncShouldSyncRemoteFilingMatches() {
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
}
