package org.jds.edgar4j.service;

import org.jds.edgar4j.service.DistributedWorkPlanner.DownloadTaskSpec;

public interface DistributedResourceAcquisitionService {

    byte[] acquire(DownloadTaskSpec specification);
}
