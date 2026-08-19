package org.jds.edgar4j.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceDownloaderTest {
    @Test
    fun acceptsAllowlistedHttpsSource() {
        SourceDownloader.validateSource("https://data.sec.gov/submissions/CIK0000320193.json")
        SourceDownloader.validateSource("https://www.sec.gov:443/files/company_tickers.json")
    }

    @Test
    fun rejectsCleartextSource() {
        assertRejected("http://data.sec.gov/submissions/test.json")
    }

    @Test
    fun rejectsUnknownHost() {
        assertRejected("https://example.com/file.json")
    }

    @Test
    fun rejectsNonStandardHttpsPort() {
        assertRejected("https://data.sec.gov:8443/submissions/test.json")
    }

    @Test
    fun rejectsUserInformation() {
        assertRejected("https://user@example.com@data.sec.gov/submissions/test.json")
    }

    @Test
    fun rejectsFragment() {
        assertRejected("https://data.sec.gov/submissions/test.json#fragment")
    }

    private fun assertRejected(url: String) {
        val error = assertThrows(WorkerTaskException::class.java) {
            SourceDownloader.validateSource(url)
        }
        assertEquals("SOURCE_REJECTED", error.code)
    }
}
