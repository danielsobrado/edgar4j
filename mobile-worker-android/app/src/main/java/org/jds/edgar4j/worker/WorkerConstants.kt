package org.jds.edgar4j.worker

object WorkerConstants {
    const val PROTOCOL_VERSION = 1
    const val API_BASE_PATH = "/api/workers"
    const val SESSION_ID_HEADER = "X-Worker-Session-Id"
    const val SESSION_TOKEN_HEADER = "X-Worker-Session-Token"
    const val LEASE_TOKEN_HEADER = "X-Worker-Lease-Token"
    const val SHA256_HEADER = "X-Artifact-Sha256"

    const val UNIQUE_PERIODIC_WORK = "edgar4j-mobile-worker-periodic"
    const val UNIQUE_IMMEDIATE_WORK = "edgar4j-mobile-worker-immediate"
    const val WORK_TAG = "edgar4j-mobile-worker"

    const val DEFAULT_MINIMUM_BATTERY = 40
    const val DEFAULT_MAX_ARTIFACT_MB = 10
    const val MAX_ARTIFACT_MB = 50
    const val MIN_ARTIFACT_MB = 1
    const val STORAGE_RESERVE_BYTES = 64L * 1024L * 1024L
    const val HEARTBEAT_INTERVAL_MS = 60_000L
    const val MAX_RUN_MS = 8L * 60L * 1000L
    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 60_000
    const val MAX_DIAGNOSTIC_LENGTH = 512

    val ALLOWED_SOURCE_HOSTS = setOf(
        "www.sec.gov",
        "data.sec.gov",
        "efts.sec.gov",
    )
}
