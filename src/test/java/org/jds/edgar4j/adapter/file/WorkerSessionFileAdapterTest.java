package org.jds.edgar4j.adapter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerPlatform;
import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class WorkerSessionFileAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void activeSessionSurvivesRestartAndExpiresAtConfiguredTime() {
        WorkerSessionFileAdapter first = newAdapter();
        WorkerSession saved = first.save(WorkerSession.builder()
                .id("session-1")
                .principalId("principal")
                .tokenHash("token-hash")
                .protocolVersion(1)
                .platform(WorkerPlatform.WEB)
                .capabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxConcurrentTasks(1)
                .createdAt(NOW)
                .lastSeenAt(NOW)
                .expiresAt(NOW.plusSeconds(60))
                .build());

        WorkerSessionFileAdapter restarted = newAdapter();
        WorkerSession recovered = restarted.findActive(
                saved.getId(),
                "token-hash",
                NOW.plusSeconds(30)).orElseThrow();

        assertEquals(saved.getId(), recovered.getId());
        assertTrue(restarted.findActive(
                saved.getId(),
                "token-hash",
                NOW.plusSeconds(60)).isEmpty());
        assertTrue(restarted.findActive(
                saved.getId(),
                "wrong-token",
                NOW.plusSeconds(30)).isEmpty());
    }

    private WorkerSessionFileAdapter newAdapter() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setFlushOnWrite(true);
        return new WorkerSessionFileAdapter(new FileStorageEngine(
                properties,
                new ObjectMapper().findAndRegisterModules()));
    }
}
