package org.jds.edgar4j.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobExecutor {

    private final DownloadJobDataPort downloadJobRepository;
    private final DownloadTickersService downloadTickersService;
    private final DownloadSubmissionsService downloadSubmissionsService;
    private final DownloadBulkDataService downloadBulkDataService;
    private final RemoteEdgarService remoteEdgarService;

    @Async("downloadExecutor")
    public void executeDownloadAsync(String jobId, DownloadRequest request) {
        log.info("Executing download job asynchronously: {}", jobId);

        Optional<DownloadJob> jobOpt = downloadJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("Job not found: {}", jobId);
            return;
        }

        DownloadJob job = jobOpt.get();
        job.setStatus(JobStatus.IN_PROGRESS);
        downloadJobRepository.save(job);

        try {
            long filesDownloaded = 0;

            switch (request.getType()) {
                case TICKERS_ALL:
                    filesDownloaded = downloadTickersService.downloadTickers();
                    break;
                case TICKERS_NYSE:
                case TICKERS_NASDAQ:
                    filesDownloaded = downloadTickersService.downloadTickersExchanges();
                    break;
                case TICKERS_MF:
                    filesDownloaded = downloadTickersService.downloadTickersMFs();
                    break;
                case SUBMISSIONS:
                    if (request.getCik() != null) {
                        filesDownloaded = downloadSubmissionsService.downloadSubmissions(request.getCik());
                    }
                    break;
                case REMOTE_FILINGS_SYNC:
                    filesDownloaded = executeRemoteFilingSync(jobId, request);
                    if (isCancelled(jobId)) {
                        return;
                    }
                    break;
                case BULK_SUBMISSIONS:
                    downloadBulkDataService.downloadBulkSubmissionsArchive();
                    filesDownloaded = 1;
                    break;
                case BULK_COMPANY_FACTS:
                    downloadBulkDataService.downloadBulkCompanyFactsArchive();
                    filesDownloaded = 1;
                    break;
                default:
                    log.warn("Unsupported download type: {}", request.getType());
            }

            markCompleted(jobId, filesDownloaded);
        } catch (Exception e) {
            log.error("Download job failed: {}", jobId, e);
            markFailed(jobId, e.getMessage());
        }
    }

    private long executeRemoteFilingSync(String jobId, DownloadRequest request) {
        RemoteFilingSearchRequest searchRequest = RemoteFilingSearchRequest.builder()
                .formType(request.getFormType())
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .limit(1)
                .build();

        List<String> companyCiks = remoteEdgarService.findMatchingCompanyCiks(searchRequest);
        updateProgress(jobId, 0, 0, companyCiks.size());

        long importedFilingRecords = 0;
        int processedCompanies = 0;

        for (String cik : companyCiks) {
            if (isCancelled(jobId)) {
                log.info("Stopping cancelled remote filing sync job {}", jobId);
                return importedFilingRecords;
            }

            importedFilingRecords += downloadSubmissionsService.downloadSubmissions(cik);
            processedCompanies++;

            int progress = companyCiks.isEmpty() ? 100 : (int) Math.round((processedCompanies * 100.0) / companyCiks.size());
            updateProgress(jobId, progress, importedFilingRecords, companyCiks.size());
        }

        return importedFilingRecords;
    }

    private void updateProgress(String jobId, int progress, long filesDownloaded, long totalFiles) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) {
            return;
        }
        job.setProgress(progress);
        job.setFilesDownloaded(filesDownloaded);
        job.setTotalFiles(totalFiles);
        downloadJobRepository.save(job);
    }

    private boolean isCancelled(String jobId) {
        return downloadJobRepository.findById(jobId)
                .map(job -> job.getStatus() == JobStatus.CANCELLED)
                .orElse(true);
    }

    private void markCompleted(String jobId, long filesDownloaded) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) {
            return;
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setFilesDownloaded(filesDownloaded);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }

    private void markFailed(String jobId, String error) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) {
            return;
        }
        job.setStatus(JobStatus.FAILED);
        job.setError(error);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }
}

