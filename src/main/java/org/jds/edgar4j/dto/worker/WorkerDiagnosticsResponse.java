package org.jds.edgar4j.dto.worker;

public record WorkerDiagnosticsResponse(
        boolean enabled,
        boolean serverWorkerEnabled,
        int serverWorkerMaxConcurrency,
        long maxMobileArtifactBytes,
        long pending,
        long leased,
        long verifying,
        long completed,
        long failed,
        long cancelled) {
}
