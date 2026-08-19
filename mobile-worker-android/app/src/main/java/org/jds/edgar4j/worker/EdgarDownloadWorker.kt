package org.jds.edgar4j.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EdgarDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val preferences = WorkerPreferences(appContext)
    private val stateReader = DeviceStateReader(appContext)
    private val downloader = SourceDownloader(appContext.cacheDir.resolve("worker-staging"))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = preferences.current()
        if (!settings.enabled) return@withContext Result.success()

        try {
            WorkerPreferences.validate(settings)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Worker settings are invalid: ${e.message}")
            return@withContext Result.failure()
        }

        val initialState = stateReader.read()
        if (!stateReader.isEligible(settings, initialState)) {
            return@withContext Result.retry()
        }

        val api = WorkerApiClient(settings, preferences.password())
        val session = try {
            api.openSession()
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

                processTask(api, session, task, settings)
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
            val maxBytes = settings.maxArtifactMb * 1024L * 1024L
            artifact = withContext(Dispatchers.IO) {
                downloader.download(task, maxBytes, settings.secUserAgent)
            }
            withContext(Dispatchers.IO) { api.upload(session, task, artifact) }
            Log.i(TAG, "Completed worker task ${task.id} (${artifact.sizeBytes} bytes)")
        } catch (e: WorkerTaskException) {
            Log.w(TAG, "Worker task ${task.id} failed with ${e.code}: ${e.message}")
            reportFailureSafely(api, session, task, e)
        } catch (e: WorkerApiException) {
            if (e.statusCode != 409) {
                Log.w(TAG, "Worker API rejected task ${task.id}: ${e.message}")
                reportFailureSafely(
                    api,
                    session,
                    task,
                    WorkerTaskException("UPLOAD_FAILED", "Worker API rejected artifact upload", e),
                )
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

    private fun cleanupStaging() {
        applicationContext.cacheDir.resolve("worker-staging")
            .listFiles()
            ?.filter { it.isFile }
            ?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "Edgar4jWorker"
    }
}
