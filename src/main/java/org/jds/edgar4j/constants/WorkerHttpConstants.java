package org.jds.edgar4j.constants;

public final class WorkerHttpConstants {

    public static final String BASE_PATH = "/api/workers";
    public static final String SESSION_ID_HEADER = "X-Worker-Session-Id";
    public static final String SESSION_TOKEN_HEADER = "X-Worker-Session-Token";
    public static final String LEASE_TOKEN_HEADER = "X-Worker-Lease-Token";
    public static final String SHA256_HEADER = "X-Artifact-Sha256";

    private WorkerHttpConstants() {
    }
}
