package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;

public interface DistributedWorkPlanner {

    WorkerTask planDownload(DownloadTaskSpec specification);

    int cancelParent(String parentDownloadJobId);

    record DownloadTaskSpec(
            String parentDownloadJobId,
            String resourceId,
            WorkerSource source,
            String sourceUrl,
            String contentType,
            Long expectedSizeBytes,
            String expectedSha256,
            long maxBytes,
            int priority) {
    }
}
