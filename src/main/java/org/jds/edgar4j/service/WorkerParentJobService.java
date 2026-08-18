package org.jds.edgar4j.service;

public interface WorkerParentJobService {

    boolean isCancelled(String parentDownloadJobId);

    void refreshProgress(String parentDownloadJobId);
}
