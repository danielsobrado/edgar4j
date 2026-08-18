package org.jds.edgar4j.constants;

public final class WorkerErrorCodes {

    public static final String DISABLED = "WORKERS_DISABLED";
    public static final String PROTOCOL_UNSUPPORTED = "WORKER_PROTOCOL_UNSUPPORTED";
    public static final String SESSION_INVALID = "WORKER_SESSION_INVALID";
    public static final String TASK_NOT_FOUND = "WORKER_TASK_NOT_FOUND";
    public static final String LEASE_INVALID = "WORKER_LEASE_INVALID";
    public static final String SOURCE_DISPATCH_UNAVAILABLE = "WORKER_SOURCE_DISPATCH_UNAVAILABLE";
    public static final String ARTIFACT_UPLOAD_FAILED = "WORKER_ARTIFACT_UPLOAD_FAILED";

    private WorkerErrorCodes() {
    }
}
