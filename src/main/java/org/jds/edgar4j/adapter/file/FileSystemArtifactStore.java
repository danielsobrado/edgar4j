package org.jds.edgar4j.adapter.file;

import static org.jds.edgar4j.constants.WorkerStorageConstants.ARTIFACT_ROOT_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.SHA256_PATTERN;
import static org.jds.edgar4j.constants.WorkerStorageConstants.STAGING_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.STAGING_SUFFIX;
import static org.jds.edgar4j.constants.WorkerStorageConstants.VERIFIED_DIRECTORY;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.springframework.stereotype.Component;

@Component
public class FileSystemArtifactStore implements ArtifactStorePort {

    private static final int COPY_BUFFER_BYTES = 16 * 1024;

    private final Path stagingDirectory;
    private final Path verifiedDirectory;

    public FileSystemArtifactStore(FileStorageProperties storageProperties) {
        Path artifactRoot = storageProperties.resolveBaseDirectory().resolve(ARTIFACT_ROOT_DIRECTORY).normalize();
        this.stagingDirectory = artifactRoot.resolve(STAGING_DIRECTORY).normalize();
        this.verifiedDirectory = artifactRoot.resolve(VERIFIED_DIRECTORY).normalize();
    }

    @Override
    public StagedArtifact stage(String taskId, InputStream input, long maxBytes) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Artifact input is required");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Artifact maxBytes must be positive");
        }

        Files.createDirectories(stagingDirectory);
        String stagingId = UUID.randomUUID().toString();
        Path target = stagingPath(stagingId);
        long total = 0;

        try (OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Artifact exceeds configured maximum size");
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }

        return new StagedArtifact(stagingId, total);
    }

    @Override
    public InputStream openStaged(String stagingId) throws IOException {
        return Files.newInputStream(stagingPath(stagingId));
    }

    @Override
    public VerifiedArtifact promote(
            String stagingId,
            String sha256,
            long sizeBytes,
            String contentType,
            Instant verifiedAt) throws IOException {
        validateSha256(sha256);
        Path staged = stagingPath(stagingId);
        long actualSize = Files.size(staged);
        if (actualSize != sizeBytes) {
            throw new IOException("Staged artifact size changed during verification");
        }

        Path target = verifiedPath(sha256);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            Files.deleteIfExists(staged);
        } else {
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staged, target);
            }
        }

        return new VerifiedArtifact(sha256, sha256, sizeBytes, contentType, verifiedAt);
    }

    @Override
    public Optional<VerifiedArtifact> findVerified(String artifactId) {
        if (artifactId == null || !SHA256_PATTERN.matcher(artifactId).matches()) {
            return Optional.empty();
        }

        Path target = verifiedPath(artifactId);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new VerifiedArtifact(
                    artifactId,
                    artifactId,
                    Files.size(target),
                    Files.probeContentType(target),
                    Files.getLastModifiedTime(target).toInstant()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read verified artifact metadata", e);
        }
    }

    @Override
    public void deleteStaged(String stagingId) throws IOException {
        Files.deleteIfExists(stagingPath(stagingId));
    }

    @Override
    public int deleteStagedOlderThan(Instant cutoff) throws IOException {
        if (!Files.isDirectory(stagingDirectory)) {
            return 0;
        }

        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingDirectory, "*" + STAGING_SUFFIX)) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry)
                        && !Files.getLastModifiedTime(entry).toInstant().isAfter(cutoff)
                        && Files.deleteIfExists(entry)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private Path stagingPath(String stagingId) {
        if (stagingId == null || stagingId.isBlank() || stagingId.contains("/") || stagingId.contains("\\")) {
            throw new IllegalArgumentException("Invalid staging artifact id");
        }
        Path path = stagingDirectory.resolve(stagingId + STAGING_SUFFIX).normalize();
        if (!path.startsWith(stagingDirectory)) {
            throw new IllegalArgumentException("Invalid staging artifact path");
        }
        return path;
    }

    private Path verifiedPath(String sha256) {
        validateSha256(sha256);
        Path directory = verifiedDirectory.resolve(sha256.substring(0, 2)).normalize();
        Path path = directory.resolve(sha256).normalize();
        if (!path.startsWith(verifiedDirectory)) {
            throw new IllegalArgumentException("Invalid verified artifact path");
        }
        return path;
    }

    private static void validateSha256(String sha256) {
        if (sha256 == null || !SHA256_PATTERN.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 artifact id");
        }
    }
}
