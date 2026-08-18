package org.jds.edgar4j.service.impl;

import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort.WorkerTaskCounts;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.springframework.stereotype.Service;

@Service
public class WorkerParentJobServiceImpl implements WorkerParentJobService {

    private final DownloadJobDataPort downloadJobDataPort;
    private final WorkerTaskDataPort workerTaskDataPort;

    public WorkerParentJobServiceImpl(
            DownloadJobDataPort downloadJobDataPort,
            WorkerTaskDataPort workerTaskDataPort) {
        this.downloadJobDataPort = downloadJobDataPort;
        this.workerTaskDataPort = workerTaskDataPort;
    }

    @Override
    public boolean isCancelled(String parentDownloadJobId) {
        if (parentDownloadJobId == null || parentDownloadJobId.isBlank()) {
            return false;
        }
        return downloadJobDataPort.findById(parentDownloadJobId)
                .map(job -> job.getStatus() == JobStatus.CANCELLED)
                .orElse(false);
    }

    @Override
    public void refreshProgress(String parentDownloadJobId) {
        if (parentDownloadJobId == null || parentDownloadJobId.isBlank()) {
            return;
        }

        DownloadJob job = downloadJobDataPort.findById(parentDownloadJobId).orElse(null);
        if (job == null || isTerminal(job.getStatus())) {
            return;
        }

        WorkerTaskCounts counts = workerTaskDataPort.countByParentDownloadJobId(parentDownloadJobId);
        long total = counts.total();
        if (total == 0) {
            return;
        }

        job.setTotalFiles(total);
        job.setFilesDownloaded(counts.completed());
        job.setProgress((int) Math.min(100L, counts.completed() * 100L / total));
        downloadJobDataPort.save(job);
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.COMPLETED
                || status == JobStatus.FAILED
                || status == JobStatus.CANCELLED;
    }
}
