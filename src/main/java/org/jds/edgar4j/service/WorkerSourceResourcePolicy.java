package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerTask;

public interface WorkerSourceResourcePolicy {

    void validate(WorkerTask task);
}
