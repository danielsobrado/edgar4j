package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerSource;

public interface WorkerSourceDispatchPolicy {

    void reserveRemoteDispatch(WorkerSource source);

    void reserveSourceRequest(WorkerSource source);
}
