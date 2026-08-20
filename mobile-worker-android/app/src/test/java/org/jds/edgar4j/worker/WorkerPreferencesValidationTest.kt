package org.jds.edgar4j.worker

import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerPreferencesValidationTest {
    @Test
    fun acceptsHttpsServerWithContextPath() {
        WorkerPreferences.validate(validSettings(serverUrl = "https://edgar.example.com/app"))
    }

    @Test
    fun rejectsCredentialsInServerUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkerPreferences.validate(
                validSettings(serverUrl = "https://user:password@edgar.example.com"),
            )
        }
    }

    @Test
    fun rejectsQueryInServerUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkerPreferences.validate(
                validSettings(serverUrl = "https://edgar.example.com?token=value"),
            )
        }
    }

    @Test
    fun rejectsFragmentInServerUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkerPreferences.validate(
                validSettings(serverUrl = "https://edgar.example.com/#fragment"),
            )
        }
    }

    @Test
    fun rejectsLineBreaksInSecUserAgent() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkerPreferences.validate(
                validSettings(secUserAgent = "Edgar4j admin@example.com\r\nX-Test: injected"),
            )
        }
    }

    @Test
    fun rejectsArtifactLimitAboveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkerPreferences.validate(
                validSettings(maxArtifactMb = WorkerConstants.MAX_ARTIFACT_MB + 1),
            )
        }
    }

    private fun validSettings(
        serverUrl: String = "https://edgar.example.com",
        secUserAgent: String = "Edgar4j admin@example.com",
        maxArtifactMb: Int = WorkerConstants.DEFAULT_MAX_ARTIFACT_MB,
    ): WorkerSettings = WorkerSettings(
        serverUrl = serverUrl,
        secUserAgent = secUserAgent,
        maxArtifactMb = maxArtifactMb,
    )
}
