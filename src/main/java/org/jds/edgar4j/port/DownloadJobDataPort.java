package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;

public interface DownloadJobDataPort extends BaseDocumentDataPort<DownloadJob> {

    List<DownloadJob> findTop10ByOrderByStartedAtDesc();

    List<DownloadJob> findByStatusIn(List<JobStatus> statuses);

    Optional<DownloadJob> findFirstByTypeInAndStatusOrderByCompletedAtDesc(List<JobType> types, JobStatus status);
}
