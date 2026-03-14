package org.jds.edgar4j.adapter.file;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class DownloadJobFileAdapter extends AbstractFileDataPort<DownloadJob> implements DownloadJobDataPort {

    public DownloadJobFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "download_jobs",
                DownloadJob.class,
                FileFormat.JSON,
                DownloadJob::getId,
                DownloadJob::setId));
    }

    @Override
    public List<DownloadJob> findTop10ByOrderByStartedAtDesc() {
        return findAll().stream()
                .sorted(Comparator.comparing(DownloadJob::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }

    @Override
    public List<DownloadJob> findByStatusIn(List<JobStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return findMatching(value -> value.getStatus() != null && statuses.contains(value.getStatus()));
    }

    @Override
    public Optional<DownloadJob> findFirstByTypeInAndStatusOrderByCompletedAtDesc(List<JobType> types, JobStatus status) {
        if (types == null || types.isEmpty() || status == null) {
            return Optional.empty();
        }
        return findMatching(value -> value.getStatus() == status && value.getType() != null && types.contains(value.getType()))
                .stream()
                .sorted(Comparator.comparing(DownloadJob::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }
}
