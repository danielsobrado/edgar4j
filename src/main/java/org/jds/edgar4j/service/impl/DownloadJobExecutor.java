package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.jds.edgar4j.service.DownloadBulkDataService.BulkDownloadResult;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.jds.edgar4j.service.UsaSpendingDownloadService;
import org.jds.edgar4j.service.UsaSpendingDownloadService.UsaSpendingDownloadResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobExecutor {

    private static final int MAX_CHUNK_DAYS = 366;
    private static final int MAX_PAUSE_SECONDS = 3_600;
    private static final int MIN_PAUSE_SECONDS = 0;

    private final DownloadJobDataPort downloadJobRepository;
    private final DownloadTickersService downloadTickersService;
    private final DownloadSubmissionsService downloadSubmissionsService;
    private final DownloadBulkDataService downloadBulkDataService;
    private final Edgar4JProperties edgar4JProperties;
    private final RemoteEdgarService remoteEdgarService;
    private final UsaSpendingDownloadService usaSpendingDownloadService;

    @Async("downloadExecutor")
    public void executeDownloadAsync(String jobId, DownloadRequest request) {
        log.info("Executing download job asynchronously: {}", jobId);

        Optional<DownloadJob> jobOpt = downloadJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("Job not found: {}", jobId);
            return;
        }

        DownloadJob job = jobOpt.get();
        if (job.getStatus() == JobStatus.CANCELLED) {
            log.info("Skipping cancelled download job {}", jobId);
            return;
        }
        job.setStatus(JobStatus.IN_PROGRESS);
        downloadJobRepository.save(job);

        try {
            long filesDownloaded = 0;

            switch (request.getType()) {
                case TICKERS_ALL:
                    filesDownloaded = downloadTickersService.downloadTickers(jobId);
                    break;
                case TICKERS_NYSE:
                case TICKERS_NASDAQ:
                    filesDownloaded = downloadTickersService.downloadTickersExchanges(jobId);
                    break;
                case TICKERS_MF:
                    filesDownloaded = downloadTickersService.downloadTickersMFs(jobId);
                    break;
                case SUBMISSIONS:
                    if (request.getCik() == null || request.getCik().isBlank()) {
                        throw new IllegalArgumentException("CIK is required for submissions download jobs");
                    }
                    filesDownloaded = downloadSubmissionsService.downloadSubmissions(request.getCik());
                    break;
                case REMOTE_FILINGS_SYNC:
                    filesDownloaded = executeRemoteFilingSync(jobId, request);
                    if (isCancelled(jobId)) {
                        return;
                    }
                    break;
                case USA_SPENDING_AWARDS:
                    UsaSpendingDownloadResult result = usaSpendingDownloadService.downloadAwardCsvZip(
                            request.getDateFrom(),
                            request.getDateTo());
                    markCompleted(jobId, 1, result);
                    return;
                case BULK_SUBMISSIONS:
                    BulkDownloadResult submissionsResult = downloadBulkDataService.downloadBulkSubmissionsArchive();
                    markCompleted(jobId, submissionsResult);
                    return;
                case BULK_COMPANY_FACTS:
                    BulkDownloadResult companyFactsResult = downloadBulkDataService.downloadBulkCompanyFactsArchive();
                    markCompleted(jobId, companyFactsResult);
                    return;
                default:
                    log.warn("Unsupported download type: {}", request.getType());
            }

            if (!isCancelled(jobId)) {
                markCompleted(jobId, filesDownloaded);
            }
        } catch (Throwable e) {
            // A fatal asynchronous failure must not leave the persisted job stuck in progress.
            log.error("Download job failed: {}", jobId, e);
            markFailed(jobId, e.getMessage());
        }
    }

    private long executeRemoteFilingSync(String jobId, DownloadRequest request) {
        DownloadRequest.RemoteFilingSyncMode syncMode = Objects.requireNonNullElse(
                request.getRemoteFilingSyncMode(),
                DownloadRequest.RemoteFilingSyncMode.COMPANY);

        int chunkDays = resolveChunkDays(request.getChunkDays());
        long pauseMs = resolvePauseSeconds(request.getPauseSeconds()) * 1_000L;
        List<DateRange> chunks = splitIntoChunks(request.getDateFrom(), request.getDateTo(), chunkDays);

        long importedFilingRecords = 0;
        Set<String> matchedCompanyCiks = new LinkedHashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (isCancelled(jobId)) {
                return importedFilingRecords;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Remote filing sync was interrupted");
            }

            SyncRangeResult syncResult = syncRange(jobId, request, syncMode, chunks.get(i));
            importedFilingRecords += syncResult.filesDownloaded();
            matchedCompanyCiks.addAll(syncResult.companyCiks());
            updateProgress(
                    jobId,
                    Math.round((i + 1) * 100.0f / chunks.size()),
                    importedFilingRecords,
                    matchedCompanyCiks.size());

            if (isCancelled(jobId)) {
                return importedFilingRecords;
            }

            if (i < chunks.size() - 1 && !sleepInterruptibly(pauseMs, jobId)) {
                throw new IllegalStateException("Remote filing sync was interrupted during pause");
            }
        }

        if (!chunks.isEmpty()) {
            updateProgress(jobId, 100, importedFilingRecords, matchedCompanyCiks.size());
        }

        return importedFilingRecords;
    }

    private SyncRangeResult syncRange(
            String jobId,
            DownloadRequest request,
            DownloadRequest.RemoteFilingSyncMode syncMode,
            DateRange range) {
        RemoteFilingSearchRequest searchRequest = RemoteFilingSearchRequest.builder()
                .formType(request.getFormType())
                .dateFrom(range.start())
                .dateTo(range.end())
                .limit(1)
                .build();

        List<String> companyCiks = remoteEdgarService.findMatchingCompanyCiks(searchRequest);
        long importedFilingRecords = 0;
        for (String cik : companyCiks) {
            if (isCancelled(jobId)) {
                log.info(
                        "Stopping cancelled remote filing sync job {} during chunk {} to {}",
                        jobId,
                        range.start(),
                        range.end());
                return new SyncRangeResult(importedFilingRecords, companyCiks);
            }
            importedFilingRecords += switch (syncMode) {
                case FILING_DATE -> downloadSubmissionsService.downloadSubmissions(
                        cik,
                        request.getFormType(),
                        range.start(),
                        range.end());
                default -> downloadSubmissionsService.downloadSubmissions(cik);
            };
        }

        return new SyncRangeResult(importedFilingRecords, companyCiks);
    }

    private List<DateRange> splitIntoChunks(LocalDate from, LocalDate to, int chunkDays) {
        if (from == null || to == null || from.isAfter(to) || chunkDays <= 0) {
            return List.of(new DateRange(from, to));
        }

        List<DateRange> ranges = new ArrayList<>();
        for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(chunkDays)) {
            LocalDate end = start.plusDays(chunkDays - 1);
            ranges.add(new DateRange(start, end.isAfter(to) ? to : end));
        }
        return ranges;
    }

    private boolean sleepInterruptibly(long pauseMs, String jobId) {
        if (pauseMs <= 0) {
            return !isCancelled(jobId);
        }

        final long sleepStepMs = 500L;
        long remainingMs = pauseMs;
        while (remainingMs > 0) {
            if (isCancelled(jobId)) {
                return false;
            }

            long sleep = Math.min(sleepStepMs, remainingMs);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remainingMs -= sleep;
        }
        return true;
    }

    private int resolveChunkDays(Integer requestedChunkDays) {
        Integer resolved = requestedChunkDays;
        if (resolved == null) {
            resolved = getRemoteSyncDefaults().getChunkDays();
        }
        if (resolved <= 0) {
            return 0;
        }
        return Math.min(Math.max(1, resolved), MAX_CHUNK_DAYS);
    }

    private int resolvePauseSeconds(Integer requestedPauseSeconds) {
        Integer resolved = requestedPauseSeconds;
        if (resolved == null) {
            resolved = getRemoteSyncDefaults().getPauseSeconds();
        }
        if (resolved <= MIN_PAUSE_SECONDS) {
            return MIN_PAUSE_SECONDS;
        }
        return Math.min(resolved, MAX_PAUSE_SECONDS);
    }

    private Edgar4JProperties.RemoteSync getRemoteSyncDefaults() {
        Edgar4JProperties.RemoteSync remoteSync = edgar4JProperties != null ? edgar4JProperties.getRemoteSync() : null;
        if (remoteSync == null) {
            return new Edgar4JProperties.RemoteSync();
        }
        return remoteSync;
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

    private void markCompleted(String jobId, long filesDownloaded, UsaSpendingDownloadResult result) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) {
            return;
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setFilesDownloaded(filesDownloaded);
        job.setTotalFiles(result.totalRows());
        job.setSourceUrl(result.sourceUrl());
        job.setOutputPath(result.outputPath().toString());
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }

    private void markCompleted(String jobId, BulkDownloadResult result) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) {
            return;
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setFilesDownloaded(result.filesDownloaded());
        job.setTotalFiles(result.filesDownloaded());
        job.setSourceUrl(result.sourceUrl());
        job.setOutputPath(result.outputPath().toString());
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

    private record SyncRangeResult(long filesDownloaded, List<String> companyCiks) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
