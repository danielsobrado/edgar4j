package org.jds.edgar4j.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkerScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun apply(settings: WorkerSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(WorkerConstants.UNIQUE_PERIODIC_WORK)
            workManager.cancelUniqueWork(WorkerConstants.UNIQUE_IMMEDIATE_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<EdgarDownloadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints(settings))
            .addTag(WorkerConstants.WORK_TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WorkerConstants.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runNow(settings: WorkerSettings) {
        val request = OneTimeWorkRequestBuilder<EdgarDownloadWorker>()
            .setConstraints(constraints(settings))
            .addTag(WorkerConstants.WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            WorkerConstants.UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun constraints(settings: WorkerSettings): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
        )
        .setRequiresCharging(settings.chargingOnly)
        .build()
}
