package org.jds.edgar4j.worker

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class WorkerApiClient(
    private val settings: WorkerSettings,
    private val password: String,
) {
    private val baseUrl = settings.serverUrl.trimEnd('/') + WorkerConstants.API_BASE_PATH

    fun openSession(): WorkerSession {
        val body = JSONObject()
            .put("protocolVersion", WorkerConstants.PROTOCOL_VERSION)
            .put("platform", "ANDROID")
            .put("clientVersion", "android-${BuildConfig.VERSION_NAME}")
            .put("capabilities", JSONArray().put("DOWNLOAD").put("SHA256"))
            .put("maxConcurrentTasks", 1)

        val data = requestJson("POST", "/session", body = body)
        return WorkerSession(
            sessionId = data.getString("sessionId"),
            sessionToken = data.getString("sessionToken"),
        )
    }

    fun lease(session: WorkerSession, state: WorkerRuntimeState): WorkerLease {
        val runtime = JSONObject()
            .put("networkType", state.networkType)
            .put("metered", state.metered)
            .put("charging", state.charging)
            .put("batteryPercent", state.batteryPercent ?: JSONObject.NULL)
            .put("freeStorageBytes", state.freeStorageBytes)
        val body = JSONObject()
            .put("protocolVersion", WorkerConstants.PROTOCOL_VERSION)
            .put("capabilities", JSONArray().put("DOWNLOAD").put("SHA256"))
            .put("maxTasks", 1)
            .put("runtime", runtime)

        val data = requestJson("POST", "/tasks/lease", session, body)
        val tasksJson = data.optJSONArray("tasks") ?: JSONArray()
        val tasks = buildList {
            for (index in 0 until tasksJson.length()) {
                val task = tasksJson.getJSONObject(index)
                add(
                    WorkerTask(
                        id = task.getString("id"),
                        sourceUrl = task.getString("sourceUrl"),
                        leaseToken = task.getString("leaseToken"),
                        maxBytes = task.getLong("maxBytes"),
                        expectedSha256 = task.optNullableString("expectedSha256"),
                        contentType = task.optNullableString("contentType"),
                    ),
                )
            }
        }
        return WorkerLease(tasks, data.optInt("retryAfterSeconds", 0))
    }

    fun heartbeat(session: WorkerSession, task: WorkerTask) {
        val body = JSONObject().put("leaseToken", task.leaseToken)
        requestJson("POST", "/tasks/${task.id}/heartbeat", session, body)
    }

    fun reportFailure(session: WorkerSession, task: WorkerTask, failure: WorkerTaskException) {
        val body = JSONObject()
            .put("leaseToken", task.leaseToken)
            .put("code", failure.code)
            .put("message", failure.message.orEmpty().take(WorkerConstants.MAX_DIAGNOSTIC_LENGTH))
        requestJson("POST", "/tasks/${task.id}/failure", session, body)
    }

    fun abandon(session: WorkerSession, task: WorkerTask) {
        val body = JSONObject().put("leaseToken", task.leaseToken)
        requestJson("POST", "/tasks/${task.id}/abandon", session, body)
    }

    fun upload(session: WorkerSession, task: WorkerTask, artifact: DownloadedArtifact) {
        val connection = openConnection("PUT", "/tasks/${task.id}/artifact", session).apply {
            setRequestProperty(WorkerConstants.LEASE_TOKEN_HEADER, task.leaseToken)
            setRequestProperty(WorkerConstants.SHA256_HEADER, artifact.sha256)
            setRequestProperty("Content-Type", artifact.contentType ?: "application/octet-stream")
            doOutput = true
            setFixedLengthStreamingMode(artifact.sizeBytes)
        }

        try {
            FileInputStream(artifact.file).use { input ->
                connection.outputStream.use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            readData(connection)
        } catch (e: WorkerApiException) {
            throw e
        } catch (e: Exception) {
            throw WorkerTaskException("UPLOAD_FAILED", "Artifact upload failed", e)
        } finally {
            connection.disconnect()
        }
    }

    fun revoke(session: WorkerSession) {
        runCatching { requestJson("DELETE", "/session", session) }
    }

    private fun requestJson(
        method: String,
        path: String,
        session: WorkerSession? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = openConnection(method, path, session)
        try {
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            return readData(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        method: String,
        path: String,
        session: WorkerSession?,
    ): HttpURLConnection {
        WorkerPreferences.validate(settings)
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = WorkerConstants.CONNECT_TIMEOUT_MS
        connection.readTimeout = WorkerConstants.READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Edgar4j-Mobile-Worker/${BuildConfig.VERSION_NAME}")
        basicAuthorization()?.let { connection.setRequestProperty("Authorization", it) }
        if (session != null) {
            connection.setRequestProperty(WorkerConstants.SESSION_ID_HEADER, session.sessionId)
            connection.setRequestProperty(WorkerConstants.SESSION_TOKEN_HEADER, session.sessionToken)
        }
        return connection
    }

    private fun readData(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Worker API returned HTTP $status"
            throw WorkerApiException(status, message)
        }
        if (text.isBlank()) return JSONObject()
        val root = JSONObject(text)
        val data = root.opt("data")
        return if (data is JSONObject) data else JSONObject()
    }

    private fun basicAuthorization(): String? {
        if (settings.username.isBlank() || password.isBlank()) return null
        val token = Base64.encodeToString(
            "${settings.username}:$password".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        return "Basic $token"
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}

class WorkerApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)
