package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.worker.WorkerDiagnosticsResponse;

public interface WorkerDiagnosticsService {

    WorkerDiagnosticsResponse getDiagnostics();
}
