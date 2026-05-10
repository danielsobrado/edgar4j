package org.jds.edgar4j.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.dto.response.DownloadSummaryResponse;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.service.DownloadJobService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobServiceImpl implements DownloadJobService {

    private final DownloadJobDataPort downloadJobRepository;
    private final TickerDataPort tickerRepository;
    private final DownloadJobExecutor downloadJobExecutor;

    @Override
    public DownloadJobResponse startDownload(DownloadRequest request) {
        log.info("Starting download job: {}", request);

        JobType jobType = mapToJobType(request.getType());

        DownloadJob job = DownloadJob.builder()
                .type(jobType)
                .description(getJobDescription(jobType, request))
                .status(JobStatus.PENDING)
                .progress(0)
                .startedAt(LocalDateTime.now())
                .cik(request.getCik())
                .userAgent(request.getUserAgent())
                .build();

        job = downloadJobRepository.save(job);

        downloadJobExecutor.executeDownloadAsync(job.getId(), request);

        return toDownloadJobResponse(job);
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
    public DownloadSummaryResponse getSummary() {
        List<JobType> tickerJobTypes = Arrays.asList(
                JobType.TICKERS_ALL,
                JobType.TICKERS_NYSE,
                JobType.TICKERS_NASDAQ,
                JobType.TICKERS_MF
        );

        Optional<DownloadJob> latestTickerJob = downloadJobRepository
                .findFirstByTypeInAndStatusOrderByCompletedAtDesc(tickerJobTypes, JobStatus.COMPLETED);

        return DownloadSummaryResponse.builder()
                .tickerRecordsImported(tickerRepository.count())
                .lastTickerUpdate(latestTickerJob.map(DownloadJob::getCompletedAt).orElse(null))
                .build();
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
    public boolean cancelJob(String jobId) {
        return downloadJobRepository.findById(jobId)
                .map(this::attemptCancelJob)
                .orElse(false);
    }

    private boolean attemptCancelJob(DownloadJob job) {
        if (job.getStatus() == JobStatus.CANCELLED) {
            return false;
        }

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            return false;
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
        return true;
    }

    private JobType mapToJobType(DownloadRequest.DownloadType type) {
        switch (type) {
            case TICKERS_ALL:
                return JobType.TICKERS_ALL;
            case TICKERS_NYSE:
                return JobType.TICKERS_NYSE;
            case TICKERS_NASDAQ:
                return JobType.TICKERS_NASDAQ;
            case TICKERS_MF:
                return JobType.TICKERS_MF;
            case SUBMISSIONS:
                return JobType.SUBMISSIONS;
            case REMOTE_FILINGS_SYNC:
                return JobType.REMOTE_FILINGS_SYNC;
            case BULK_SUBMISSIONS:
                return JobType.BULK_SUBMISSIONS;
            case BULK_COMPANY_FACTS:
                return JobType.BULK_COMPANY_FACTS;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private String getJobDescription(JobType type, DownloadRequest request) {
        switch (type) {
            case TICKERS_ALL:
                return "Download All Company Tickers";
            case TICKERS_NYSE:
                return "Download NYSE Tickers";
            case TICKERS_NASDAQ:
                return "Download NASDAQ Tickers";
            case TICKERS_MF:
                return "Download Mutual Fund Tickers";
            case SUBMISSIONS:
                return "Download Submissions for CIK " + request.getCik();
            case REMOTE_FILINGS_SYNC:
                return String.format(
                        "Sync Remote %s Filings from %s to %s",
                        request.getFormType(),
                        request.getDateFrom(),
                        request.getDateTo()
                );
            case BULK_SUBMISSIONS:
                return "Download Bulk Submissions Archive";
            case BULK_COMPANY_FACTS:
                return "Download Company Facts XBRL Archive";
            default:
                return "Unknown Job Type";
        }
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
                .cik(job.getCik())
                .build();
    }
}

