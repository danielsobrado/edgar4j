package org.jds.edgar4j.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class EdgarDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val preferences = WorkerPreferences(appContext)
    private val stateReader = DeviceStateReader(appContext)
    private val downloader = SourceDownloader(appContext.cacheDir.resolve("worker-staging"))

    override suspend fun doWork(): Result {
        if (!ACTIVE.compareAndSet(false, true)) {
            Log.i(TAG, "Skipping overlapping mobile worker execution")
            return Result.success()
        }

        return try {
            executeWork()
        } finally {
            ACTIVE.set(false)
        }
    }

    private suspend fun executeWork(): Result = withContext(Dispatchers.IO) {
        val settings = preferences.current()
        if (!settings.enabled) return@withContext Result.success()

        try {
            WorkerPreferences.validate(settings)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Worker settings are invalid: ${e.message}")
            return@withContext Result.failure()
        }

        val password = preferences.password()
        if (settings.username.isNotBlank() && password.isBlank()) {
            Log.e(TAG, "HTTP Basic username is configured without a stored password")
            return@withContext Result.failure()
        }

        val initialState = stateReader.read()
        if (!stateReader.isEligible(settings, initialState)) {
            return@withContext Result.success()
        }

        val api = WorkerApiClient(settings, password)
        val session = try {
            api.openSession()
        } catch (e: WorkerApiException) {
            return@withContext apiFailureResult("open session", e)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open worker session", e)
            return@withContext Result.retry()
        }

        val deadline = System.currentTimeMillis() + WorkerConstants.MAX_RUN_MS
        try {
            while (!isStopped && System.currentTimeMillis() < deadline) {
                val runtime = stateReader.read()
                if (!stateReader.isEligible(settings, runtime)) break

                val lease = try {
                    api.lease(session, runtime)
                } catch (e: WorkerApiException) {
                    return@withContext apiFailureResult("lease task", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to lease worker task", e)
                    return@withContext Result.retry()
                }

                val task = lease.tasks.firstOrNull()
                if (task == null) {
                    val retrySeconds = lease.retryAfterSeconds.coerceIn(
                        WorkerConstants.MIN_IDLE_RETRY_SECONDS,
                        WorkerConstants.MAX_IDLE_RETRY_SECONDS,
                    )
                    val retryMs = retrySeconds * 1_000L
                    if (System.currentTimeMillis() + retryMs >= deadline) break
                    delay(retryMs)
                    continue
                }

                try {
                    processTask(api, session, task, settings)
                } catch (e: WorkerApiException) {
                    return@withContext apiFailureResult("process task", e)
                }
            }
            Result.success()
        } finally {
            api.revoke(session)
            cleanupStaging()
        }
    }

    private suspend fun processTask(
        api: WorkerApiClient,
        session: WorkerSession,
        task: WorkerTask,
        settings: WorkerSettings,
    ) = coroutineScope {
        var artifact: DownloadedArtifact? = null
        val heartbeat = launch(Dispatchers.IO) {
            while (isActive) {
                delay(WorkerConstants.HEARTBEAT_INTERVAL_MS)
                runCatching { api.heartbeat(session, task) }
                    .onFailure { Log.w(TAG, "Heartbeat failed for task ${task.id}") }
            }
        }

        try {
            val maxBytes = settings.maxArtifactMb.toLong() * WorkerConstants.MEBIBYTE_BYTES
            val downloaded = withContext(Dispatchers.IO) {
                downloader.download(task, maxBytes, settings.secUserAgent)
            }
            artifact = downloaded
            withContext(Dispatchers.IO) { api.upload(session, task, downloaded) }
            Log.i(TAG, "Completed worker task ${task.id} (${downloaded.sizeBytes} bytes)")
        } catch (e: CancellationException) {
            abandonOnCancellation(api, session, task)
            throw e
        } catch (e: WorkerTaskException) {
            Log.w(TAG, "Worker task ${task.id} failed with ${e.code}: ${e.message}")
            reportFailureSafely(api, session, task, e)
        } catch (e: WorkerApiException) {
            when (e.statusCode) {
                409 -> Log.i(TAG, "Worker lease became stale for task ${task.id}")
                413, 422 -> {
                    Log.w(TAG, "Worker API rejected artifact for task ${task.id}: ${e.message}")
                    reportFailureSafely(
                        api,
                        session,
                        task,
                        WorkerTaskException("UPLOAD_FAILED", "Worker API rejected artifact upload", e),
                    )
                }
                else -> throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected worker task failure ${task.id}", e)
            reportFailureSafely(
                api,
                session,
                task,
                WorkerTaskException("INTERNAL_ERROR", "Unexpected mobile worker failure", e),
            )
        } finally {
            heartbeat.cancel()
            artifact?.file?.delete()
        }
    }

    private suspend fun abandonOnCancellation(
        api: WorkerApiClient,
        session: WorkerSession,
        task: WorkerTask,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { api.abandon(session, task) }
                .onFailure { Log.w(TAG, "Unable to abandon cancelled task ${task.id}") }
        }
    }

    private suspend fun reportFailureSafely(
        api: WorkerApiClient,
        session: WorkerSession,
        task: WorkerTask,
        failure: WorkerTaskException,
    ) {
        withContext(Dispatchers.IO) {
            runCatching { api.reportFailure(session, task, failure) }
                .onFailure { Log.w(TAG, "Unable to report failure for task ${task.id}") }
        }
    }

    private fun apiFailureResult(operation: String, failure: WorkerApiException): Result {
        Log.w(TAG, "Worker API failed to $operation with HTTP ${failure.statusCode}: ${failure.message}")
        return if (isRetryableApiStatus(failure.statusCode)) Result.retry() else Result.failure()
    }

    private fun cleanupStaging() {
        applicationContext.cacheDir.resolve("worker-staging")
            .listFiles()
            ?.filter { it.isFile }
            ?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "Edgar4jWorker"
        private val ACTIVE = AtomicBoolean(false)

        private fun isRetryableApiStatus(statusCode: Int): Boolean =
            statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
    }
}
