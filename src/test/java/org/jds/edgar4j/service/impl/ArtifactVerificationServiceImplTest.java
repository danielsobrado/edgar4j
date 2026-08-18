package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.jds.edgar4j.adapter.file.FileSystemArtifactStore;
import org.jds.edgar4j.exception.WorkerArtifactException;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactVerificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void validArtifactIsIndependentlyHashedAndPromoted() throws Exception {
        byte[] bytes = "{\"cik\":\"0000320193\"}".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(bytes);
        FileSystemArtifactStore store = newStore();
        ArtifactVerificationServiceImpl verifier = new ArtifactVerificationServiceImpl(
                store,
                new DistributedWorkerProperties());
        StagedArtifact staged = store.stage("task-1", new ByteArrayInputStream(bytes), 1024);
        WorkerTask task = WorkerTask.builder()
                .expectedSha256(sha256)
                .expectedSizeBytes((long) bytes.length)
                .maxBytes(1024L)
                .contentType("application/json")
                .build();

        VerifiedArtifact verified = verifier.verifyAndPromote(
                task,
                staged,
                sha256,
                "application/json",
                NOW);

        assertEquals(sha256, verified.artifactId());
        assertEquals(bytes.length, verified.sizeBytes());
        assertTrue(store.findVerified(sha256).isPresent());
    }

    @Test
    void checksumMismatchIsRejectedAndStagingIsDeleted() throws Exception {
        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        FileSystemArtifactStore store = newStore();
        ArtifactVerificationServiceImpl verifier = new ArtifactVerificationServiceImpl(
                store,
                new DistributedWorkerProperties());
        StagedArtifact staged = store.stage("task-1", new ByteArrayInputStream(bytes), 1024);
        WorkerTask task = WorkerTask.builder()
                .expectedSha256("0".repeat(64))
                .maxBytes(1024L)
                .contentType("application/json")
                .build();

        assertThrows(
                WorkerArtifactException.class,
                () -> verifier.verifyAndPromote(task, staged, sha256(bytes), "application/json", NOW));

        assertThrows(Exception.class, () -> store.openStaged(staged.stagingId()));
        assertFalse(store.findVerified(sha256(bytes)).isPresent());
    }

    @Test
    void invalidJsonShapeIsRejected() throws Exception {
        byte[] bytes = "not-json".getBytes(StandardCharsets.UTF_8);
        FileSystemArtifactStore store = newStore();
        ArtifactVerificationServiceImpl verifier = new ArtifactVerificationServiceImpl(
                store,
                new DistributedWorkerProperties());
        StagedArtifact staged = store.stage("task-1", new ByteArrayInputStream(bytes), 1024);
        WorkerTask task = WorkerTask.builder()
                .maxBytes(1024L)
                .contentType("application/json")
                .build();

        assertThrows(
                WorkerArtifactException.class,
                () -> verifier.verifyAndPromote(task, staged, sha256(bytes), "application/json", NOW));
    }

    private FileSystemArtifactStore newStore() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        return new FileSystemArtifactStore(properties);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
