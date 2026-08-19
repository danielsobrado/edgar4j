package org.jds.edgar4j.worker

import java.io.File

data class WorkerRuntimeState(
    val networkType: String,
    val metered: Boolean,
    val charging: Boolean,
    val batteryPercent: Int?,
    val freeStorageBytes: Long,
)

data class WorkerSession(
    val sessionId: String,
    val sessionToken: String,
)

data class WorkerTask(
    val id: String,
    val sourceUrl: String,
    val leaseToken: String,
    val maxBytes: Long,
    val expectedSha256: String?,
    val contentType: String?,
)

data class WorkerLease(
    val tasks: List<WorkerTask>,
    val retryAfterSeconds: Int,
)

data class DownloadedArtifact(
    val file: File,
    val sha256: String,
    val contentType: String?,
    val sizeBytes: Long,
)

class WorkerTaskException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
