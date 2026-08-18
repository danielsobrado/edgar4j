package org.jds.edgar4j.constants;

public final class WorkerProtocolConstants {

    public static final int CURRENT_VERSION = 1;
    public static final int MAX_TASKS_PER_LEASE_REQUEST = 16;
    public static final int MAX_CLIENT_VERSION_LENGTH = 64;
    public static final int MAX_DIAGNOSTIC_MESSAGE_LENGTH = 512;
    public static final int MAX_RESOURCE_ID_LENGTH = 512;
    public static final int TOKEN_BYTES = 32;
    public static final int LEASE_CANDIDATE_SCAN_LIMIT = 64;
    public static final String SERVER_SESSION_PREFIX = "server-";

    private WorkerProtocolConstants() {
    }
}
