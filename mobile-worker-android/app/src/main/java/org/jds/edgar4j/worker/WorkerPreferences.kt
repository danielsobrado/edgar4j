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
import java.net.URI

private val Context.workerDataStore by preferencesDataStore(name = "worker_settings")

data class WorkerSettings(
    val serverUrl: String = "",
    val secUserAgent: String = "",
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
            secUserAgent = preferences[SEC_USER_AGENT] ?: "",
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

    suspend fun save(settings: WorkerSettings, newPassword: String?) {
        validate(settings)
        validateCredentials(settings, newPassword)

        appContext.workerDataStore.edit { preferences ->
            preferences[SERVER_URL] = settings.serverUrl.trim().trimEnd('/')
            preferences[SEC_USER_AGENT] = settings.secUserAgent.trim()
            preferences[USERNAME] = settings.username.trim()
            preferences[ENABLED] = settings.enabled
            preferences[WIFI_ONLY] = settings.wifiOnly
            preferences[CHARGING_ONLY] = settings.chargingOnly
            preferences[MINIMUM_BATTERY] = settings.minimumBatteryPercent
            preferences[MAX_ARTIFACT_MB] = settings.maxArtifactMb
        }
        if (settings.username.isBlank()) {
            secretStore.putPassword("")
        } else {
            newPassword?.let(secretStore::putPassword)
        }
    }

    fun password(): String = secretStore.getPassword()

    private suspend fun validateCredentials(settings: WorkerSettings, newPassword: String?) {
        val username = settings.username.trim()
        if (username.isBlank()) return

        val previous = current()
        if (newPassword != null) {
            require(newPassword.isNotBlank()) { "HTTP Basic password cannot be blank" }
            return
        }

        val samePrincipal = previous.username.trim() == username
        val sameOrigin = runCatching { serverOrigin(previous.serverUrl) }
            .getOrNull() == serverOrigin(settings.serverUrl)
        require(samePrincipal && sameOrigin && secretStore.getPassword().isNotBlank()) {
            "Enter the HTTP Basic password when setting credentials or changing the server"
        }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SEC_USER_AGENT = stringPreferencesKey("sec_user_agent")
        private val USERNAME = stringPreferencesKey("username")
        private val ENABLED = booleanPreferencesKey("enabled")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        private val MINIMUM_BATTERY = intPreferencesKey("minimum_battery")
        private val MAX_ARTIFACT_MB = intPreferencesKey("max_artifact_mb")

        fun validate(settings: WorkerSettings) {
            val url = parseServerUri(settings.serverUrl)
            val scheme = url.scheme?.lowercase()
            require(scheme == "https" || (BuildConfig.DEBUG && scheme == "http")) {
                "Release builds require HTTPS"
            }
            require(url.host?.isNotBlank() == true) { "Server URL must include a host" }
            require(url.userInfo == null) { "Server URL cannot contain credentials" }
            require(url.rawQuery == null) { "Server URL cannot contain a query" }
            require(url.rawFragment == null) { "Server URL cannot contain a fragment" }
            require(url.port == -1 || url.port in 1..65535) { "Server URL port is invalid" }

            val secUserAgent = settings.secUserAgent.trim()
            require(secUserAgent.isNotBlank()) {
                "SEC User-Agent with a contact identity is required"
            }
            require(secUserAgent.length <= 256) { "SEC User-Agent is too long" }
            require(secUserAgent.none { it == '\r' || it == '\n' }) {
                "SEC User-Agent cannot contain line breaks"
            }

            require(settings.minimumBatteryPercent in 0..100) {
                "Minimum battery must be between 0 and 100"
            }
            require(settings.maxArtifactMb in WorkerConstants.MIN_ARTIFACT_MB..WorkerConstants.MAX_ARTIFACT_MB) {
                "Maximum artifact must be between ${WorkerConstants.MIN_ARTIFACT_MB} and ${WorkerConstants.MAX_ARTIFACT_MB} MB"
            }
        }

        private fun parseServerUri(rawUrl: String): URI = runCatching { URI(rawUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("Server URL is invalid") }

        private fun serverOrigin(rawUrl: String): String {
            val uri = parseServerUri(rawUrl)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            val port = if (uri.port == -1) defaultPort(scheme) else uri.port
            return "$scheme://$host:$port"
        }

        private fun defaultPort(scheme: String): Int = when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }
}
