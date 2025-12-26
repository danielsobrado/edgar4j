package org.jds.edgar4j.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.entity.DownloadJob;
import org.jds.edgar4j.entity.DownloadJob.JobStatus;
import org.jds.edgar4j.entity.DownloadJob.JobType;
import org.jds.edgar4j.repository.DownloadJobRepository;
import org.jds.edgar4j.service.DownloadJobService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobServiceImpl implements DownloadJobService {

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadTickersService downloadTickersService;
    private final DownloadSubmissionsService downloadSubmissionsService;

    @Override
    public DownloadJobResponse startDownload(DownloadRequest request) {
        log.info("Starting download job: {}", request);

        JobType jobType = mapToJobType(request.getType());

        DownloadJob job = DownloadJob.builder()
                .type(jobType)
                .description(getJobDescription(jobType, request.getCik()))
                .status(JobStatus.PENDING)
                .progress(0)
                .startedAt(LocalDateTime.now())
                .cik(request.getCik())
                .userAgent(request.getUserAgent())
                .build();

        job = downloadJobRepository.save(job);

        executeDownloadAsync(job.getId(), request);

        return toDownloadJobResponse(job);
    }

    @Async
    protected void executeDownloadAsync(String jobId, DownloadRequest request) {
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
            switch (request.getType()) {
                case TICKERS_ALL:
                    downloadTickersService.downloadTickers();
                    break;
                case TICKERS_NYSE:
                case TICKERS_NASDAQ:
                    downloadTickersService.downloadTickersExchanges();
                    break;
                case TICKERS_MF:
                    downloadTickersService.downloadTickersMFs();
                    break;
                case SUBMISSIONS:
                    if (request.getCik() != null) {
                        downloadSubmissionsService.downloadSubmissions(request.getCik());
                    }
                    break;
                default:
                    log.warn("Unsupported download type: {}", request.getType());
            }

            completeJob(jobId);
        } catch (Exception e) {
            log.error("Download job failed: {}", jobId, e);
            failJob(jobId, e.getMessage());
        }
    }

    @Override
    public Optional<DownloadJobResponse> getJobById(String jobId) {
        return downloadJobRepository.findById(jobId).map(this::toDownloadJobResponse);
    }

    @Override
    public List<DownloadJobResponse> getRecentJobs(int limit) {
        return downloadJobRepository.findTop10ByOrderByStartedAtDesc().stream()
                .limit(limit)
                .map(this::toDownloadJobResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DownloadJobResponse> getActiveJobs() {
        return downloadJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.IN_PROGRESS))
                .stream()
                .map(this::toDownloadJobResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DownloadJob updateJobProgress(String jobId, int progress, long filesDownloaded) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setProgress(progress);
        job.setFilesDownloaded(filesDownloaded);
        return downloadJobRepository.save(job);
    }

    @Override
    public DownloadJob completeJob(String jobId) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setCompletedAt(LocalDateTime.now());
        return downloadJobRepository.save(job);
    }

    @Override
    public DownloadJob failJob(String jobId, String error) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.FAILED);
        job.setError(error);
        job.setCompletedAt(LocalDateTime.now());
        return downloadJobRepository.save(job);
    }

    @Override
    public void cancelJob(String jobId) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }

    private JobType mapToJobType(DownloadRequest.DownloadType type) {
        return switch (type) {
            case TICKERS_ALL -> JobType.TICKERS_ALL;
            case TICKERS_NYSE -> JobType.TICKERS_NYSE;
            case TICKERS_NASDAQ -> JobType.TICKERS_NASDAQ;
            case TICKERS_MF -> JobType.TICKERS_MF;
            case SUBMISSIONS -> JobType.SUBMISSIONS;
            case BULK_SUBMISSIONS -> JobType.BULK_SUBMISSIONS;
            case BULK_COMPANY_FACTS -> JobType.BULK_COMPANY_FACTS;
        };
    }

    private String getJobDescription(JobType type, String cik) {
        return switch (type) {
            case TICKERS_ALL -> "Download All Company Tickers";
            case TICKERS_NYSE -> "Download NYSE Tickers";
            case TICKERS_NASDAQ -> "Download NASDAQ Tickers";
            case TICKERS_MF -> "Download Mutual Fund Tickers";
            case SUBMISSIONS -> "Download Submissions for CIK " + cik;
            case BULK_SUBMISSIONS -> "Download Bulk Submissions Archive";
            case BULK_COMPANY_FACTS -> "Download Company Facts XBRL Archive";
        };
    }

    private DownloadJobResponse toDownloadJobResponse(DownloadJob job) {
        return DownloadJobResponse.builder()
                .id(job.getId())
                .type(job.getType().name())
                .description(job.getDescription())
                .status(DownloadJobResponse.JobStatus.valueOf(job.getStatus().name()))
                .progress(job.getProgress())
                .error(job.getError())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .filesDownloaded(job.getFilesDownloaded())
                .totalFiles(job.getTotalFiles())
                .build();
    }
}
