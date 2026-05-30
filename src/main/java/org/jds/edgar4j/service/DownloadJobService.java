package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.dto.response.DownloadSummaryResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCoverageResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCsvPageResponse;
import org.jds.edgar4j.model.DownloadJob;

public interface DownloadJobService {

    DownloadJobResponse startDownload(DownloadRequest request);

    Optional<DownloadJobResponse> getJobById(String jobId);

    Optional<UsaSpendingCsvPageResponse> getUsaSpendingCsvPage(String jobId, int page, int size);

    UsaSpendingCoverageResponse getUsaSpendingCoverage(LocalDate from, LocalDate to);

    List<DownloadJobResponse> getRecentJobs(int limit);

    List<DownloadJobResponse> getActiveJobs();

    DownloadSummaryResponse getSummary();

    DownloadJob updateJobProgress(String jobId, int progress, long filesDownloaded);

    DownloadJob completeJob(String jobId);

    DownloadJob failJob(String jobId, String error);

    boolean cancelJob(String jobId);
}

