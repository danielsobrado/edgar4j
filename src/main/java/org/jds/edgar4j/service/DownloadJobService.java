package org.jds.edgar4j.service;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.model.DownloadJob;

public interface DownloadJobService {

    DownloadJobResponse startDownload(DownloadRequest request);

    Optional<DownloadJobResponse> getJobById(String jobId);

    List<DownloadJobResponse> getRecentJobs(int limit);

    List<DownloadJobResponse> getActiveJobs();

    DownloadJob updateJobProgress(String jobId, int progress, long filesDownloaded);

    DownloadJob completeJob(String jobId);

    DownloadJob failJob(String jobId, String error);

    void cancelJob(String jobId);
}

