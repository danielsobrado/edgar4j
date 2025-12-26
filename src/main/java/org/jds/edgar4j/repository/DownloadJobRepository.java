package org.jds.edgar4j.repository;

import java.util.List;

import org.jds.edgar4j.entity.DownloadJob;
import org.jds.edgar4j.entity.DownloadJob.JobStatus;
import org.jds.edgar4j.entity.DownloadJob.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadJobRepository extends MongoRepository<DownloadJob, String> {

    List<DownloadJob> findByStatus(JobStatus status);

    List<DownloadJob> findByType(JobType type);

    List<DownloadJob> findByTypeAndStatus(JobType type, JobStatus status);

    Page<DownloadJob> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<DownloadJob> findTop10ByOrderByStartedAtDesc();

    List<DownloadJob> findByStatusIn(List<JobStatus> statuses);

    long countByStatus(JobStatus status);
}
