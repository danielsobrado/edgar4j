package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerTask;

public interface WorkerSourceFetcher {

    byte[] fetch(WorkerTask task);

    boolean supports(WorkerTask task);
}
