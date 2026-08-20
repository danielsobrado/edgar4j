package org.jds.edgar4j.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

class DeviceStateReader(private val context: Context) {
    fun read(): WorkerRuntimeState {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        return WorkerRuntimeState(
            networkType = networkType(capabilities),
            metered = connectivity.isActiveNetworkMetered,
            charging = isCharging(battery),
            batteryPercent = batteryPercent(battery),
            freeStorageBytes = context.filesDir.usableSpace,
        )
    }

    fun isEligible(settings: WorkerSettings, state: WorkerRuntimeState): Boolean {
        if (state.networkType == "OTHER") return false
        if (settings.wifiOnly && state.metered) return false
        if (settings.chargingOnly && !state.charging) return false
        if (settings.minimumBatteryPercent > 0) {
            val batteryPercent = state.batteryPercent ?: return false
            if (batteryPercent < settings.minimumBatteryPercent) return false
        }

        val requiredBytes = settings.maxArtifactMb.toLong() * WorkerConstants.MEBIBYTE_BYTES +
            WorkerConstants.STORAGE_RESERVE_BYTES
        return state.freeStorageBytes >= requiredBytes
    }

    private fun networkType(capabilities: NetworkCapabilities?): String {
        if (capabilities == null ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            return "OTHER"
        }

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun isCharging(intent: Intent?): Boolean {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun batteryPercent(intent: Intent?): Int? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return null
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }
}
