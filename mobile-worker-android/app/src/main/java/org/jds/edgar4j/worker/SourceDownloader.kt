package org.jds.edgar4j.worker

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class SourceDownloader(private val stagingDirectory: File) {
    fun download(
        task: WorkerTask,
        configuredMaxBytes: Long,
        secUserAgent: String,
    ): DownloadedArtifact {
        validateSource(task.sourceUrl)
        require(secUserAgent.isNotBlank()) { "SEC User-Agent is required" }
        stagingDirectory.mkdirs()
        val target = File.createTempFile("task-${task.id}-", ".part", stagingDirectory)
        val maxBytes = minOf(task.maxBytes, configuredMaxBytes)

        try {
            val url = URL(task.sourceUrl)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = WorkerConstants.CONNECT_TIMEOUT_MS
                readTimeout = WorkerConstants.READ_TIMEOUT_MS
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", secUserAgent)
            }

            try {
                val status = connection.responseCode
                when {
                    status == HttpURLConnection.HTTP_NOT_FOUND -> throw WorkerTaskException(
                        "SOURCE_NOT_FOUND",
                        "Source returned HTTP 404",
                    )
                    status == 429 -> throw WorkerTaskException(
                        "SOURCE_RATE_LIMITED",
                        "Source returned HTTP 429",
                    )
                    status in 300..399 -> throw WorkerTaskException(
                        "SOURCE_REJECTED",
                        "Source redirects are not permitted",
                    )
                    status !in 200..299 -> throw WorkerTaskException(
                        "SOURCE_REJECTED",
                        "Source returned HTTP $status",
                    )
                }

                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) {
                    throw WorkerTaskException("CONTENT_INVALID", "Source exceeds the task size limit")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) {
                                throw WorkerTaskException("CONTENT_INVALID", "Source exceeded the task size limit")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }

                if (total == 0L) {
                    throw WorkerTaskException("CONTENT_INVALID", "Source returned an empty artifact")
                }

                val sha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                if (!task.expectedSha256.isNullOrBlank() &&
                    !task.expectedSha256.equals(sha256, ignoreCase = true)
                ) {
                    throw WorkerTaskException("CHECKSUM_MISMATCH", "Downloaded SHA-256 does not match the task")
                }

                return DownloadedArtifact(
                    file = target,
                    sha256 = sha256,
                    contentType = connection.contentType ?: task.contentType,
                    sizeBytes = total,
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: WorkerTaskException) {
            target.delete()
            throw e
        } catch (e: SocketTimeoutException) {
            target.delete()
            throw WorkerTaskException("SOURCE_TIMEOUT", "Source request timed out", e)
        } catch (e: Exception) {
            target.delete()
            throw WorkerTaskException("NETWORK_UNAVAILABLE", "Source download failed", e)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        fun validateSource(rawUrl: String) {
            val url = runCatching { URL(rawUrl) }
                .getOrElse { throw WorkerTaskException("SOURCE_REJECTED", "Invalid source URL") }
            if (url.protocol != "https") {
                throw WorkerTaskException("SOURCE_REJECTED", "Source URL must use HTTPS")
            }
            if (url.host.lowercase() !in WorkerConstants.ALLOWED_SOURCE_HOSTS) {
                throw WorkerTaskException("SOURCE_REJECTED", "Source host is not allowlisted")
            }
        }
    }
}
