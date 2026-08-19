package org.jds.edgar4j.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceDownloaderTest {
    @Test
    fun acceptsAllowlistedHttpsSource() {
        SourceDownloader.validateSource("https://data.sec.gov/submissions/CIK0000320193.json")
    }

    @Test
    fun rejectsCleartextSource() {
        val error = assertThrows(WorkerTaskException::class.java) {
            SourceDownloader.validateSource("http://data.sec.gov/submissions/test.json")
        }
        assertEquals("SOURCE_REJECTED", error.code)
    }

    @Test
    fun rejectsUnknownHost() {
        val error = assertThrows(WorkerTaskException::class.java) {
            SourceDownloader.validateSource("https://example.com/file.json")
        }
        assertEquals("SOURCE_REJECTED", error.code)
    }
}
