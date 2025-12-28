package org.jds.edgar4j.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.repository.DownloadJobRepository;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.DownloadTickersService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobExecutor {

    private final DownloadJobRepository downloadJobRepository;
    private final DownloadTickersService downloadTickersService;
    private final DownloadSubmissionsService downloadSubmissionsService;

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
            switch (request.getType()) {
                case TICKERS_ALL -> downloadTickersService.downloadTickers();
                case TICKERS_NYSE, TICKERS_NASDAQ -> downloadTickersService.downloadTickersExchanges();
                case TICKERS_MF -> downloadTickersService.downloadTickersMFs();
                case SUBMISSIONS -> {
                    if (request.getCik() != null) {
                        downloadSubmissionsService.downloadSubmissions(request.getCik());
                    }
                }
                default -> log.warn("Unsupported download type: {}", request.getType());
            }

            markCompleted(jobId);
        } catch (Exception e) {
            log.error("Download job failed: {}", jobId, e);
            markFailed(jobId, e.getMessage());
        }
    }

    private void markCompleted(String jobId) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }

    private void markFailed(String jobId, String error) {
        DownloadJob job = downloadJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus(JobStatus.FAILED);
        job.setError(error);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
    }
}

