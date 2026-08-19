package org.jds.edgar4j.worker

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workerDataStore by preferencesDataStore(name = "worker_settings")

data class WorkerSettings(
    val serverUrl: String = "",
    val username: String = "",
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = true,
    val minimumBatteryPercent: Int = WorkerConstants.DEFAULT_MINIMUM_BATTERY,
    val maxArtifactMb: Int = WorkerConstants.DEFAULT_MAX_ARTIFACT_MB,
)

class WorkerPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val secretStore = SecretStore(appContext)

    val settings: Flow<WorkerSettings> = appContext.workerDataStore.data.map { preferences ->
        WorkerSettings(
            serverUrl = preferences[SERVER_URL] ?: "",
            username = preferences[USERNAME] ?: "",
            enabled = preferences[ENABLED] ?: false,
            wifiOnly = preferences[WIFI_ONLY] ?: true,
            chargingOnly = preferences[CHARGING_ONLY] ?: true,
            minimumBatteryPercent = preferences[MINIMUM_BATTERY]
                ?: WorkerConstants.DEFAULT_MINIMUM_BATTERY,
            maxArtifactMb = preferences[MAX_ARTIFACT_MB]
                ?: WorkerConstants.DEFAULT_MAX_ARTIFACT_MB,
        )
    }

    suspend fun current(): WorkerSettings = settings.first()

    suspend fun save(settings: WorkerSettings, password: String) {
        validate(settings)
        appContext.workerDataStore.edit { preferences ->
            preferences[SERVER_URL] = settings.serverUrl.trimEnd('/')
            preferences[USERNAME] = settings.username.trim()
            preferences[ENABLED] = settings.enabled
            preferences[WIFI_ONLY] = settings.wifiOnly
            preferences[CHARGING_ONLY] = settings.chargingOnly
            preferences[MINIMUM_BATTERY] = settings.minimumBatteryPercent
            preferences[MAX_ARTIFACT_MB] = settings.maxArtifactMb
        }
        secretStore.putPassword(password)
    }

    fun password(): String = secretStore.getPassword()

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val USERNAME = stringPreferencesKey("username")
        private val ENABLED = booleanPreferencesKey("enabled")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        private val MINIMUM_BATTERY = intPreferencesKey("minimum_battery")
        private val MAX_ARTIFACT_MB = intPreferencesKey("max_artifact_mb")

        fun validate(settings: WorkerSettings) {
            val url = runCatching { java.net.URI(settings.serverUrl.trim()) }
                .getOrElse { throw IllegalArgumentException("Server URL is invalid") }
            require(url.host?.isNotBlank() == true) { "Server URL must include a host" }
            require(url.scheme == "https" || (BuildConfig.DEBUG && url.scheme == "http")) {
                "Release builds require HTTPS"
            }
            require(settings.minimumBatteryPercent in 0..100) {
                "Minimum battery must be between 0 and 100"
            }
            require(settings.maxArtifactMb in WorkerConstants.MIN_ARTIFACT_MB..WorkerConstants.MAX_ARTIFACT_MB) {
                "Maximum artifact must be between ${WorkerConstants.MIN_ARTIFACT_MB} and ${WorkerConstants.MAX_ARTIFACT_MB} MB"
            }
        }
    }
}
