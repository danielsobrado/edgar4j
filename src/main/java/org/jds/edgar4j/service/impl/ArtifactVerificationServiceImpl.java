package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerStorageConstants.SHA256_ALGORITHM;
import static org.jds.edgar4j.constants.WorkerStorageConstants.SHA256_PATTERN;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import org.jds.edgar4j.exception.WorkerArtifactException;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.ArtifactVerificationService;
import org.springframework.stereotype.Service;

@Service
public class ArtifactVerificationServiceImpl implements ArtifactVerificationService {

    private static final int READ_BUFFER_BYTES = 16 * 1024;
    private static final int SIGNATURE_BYTES = 4;

    private final ArtifactStorePort artifactStore;
    private final DistributedWorkerProperties properties;

    public ArtifactVerificationServiceImpl(
            ArtifactStorePort artifactStore,
            DistributedWorkerProperties properties) {
        this.artifactStore = artifactStore;
        this.properties = properties;
    }

    @Override
    public VerifiedArtifact verifyAndPromote(
            WorkerTask task,
            StagedArtifact stagedArtifact,
            String claimedSha256,
            String contentType,
            Instant verifiedAt) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(stagedArtifact, "stagedArtifact");
        Objects.requireNonNull(verifiedAt, "verifiedAt");

        try {
            validateSize(task, stagedArtifact);
            DigestResult digestResult = digest(stagedArtifact.stagingId());
            validateHash(task, claimedSha256, digestResult.sha256());
            validateContentShape(task.getContentType(), digestResult);
            return artifactStore.promote(
                    stagedArtifact.stagingId(),
                    digestResult.sha256(),
                    stagedArtifact.sizeBytes(),
                    contentType,
                    verifiedAt);
        } catch (WorkerArtifactException e) {
            deleteRejectedStaging(stagedArtifact.stagingId(), e);
            throw e;
        } catch (IOException e) {
            throw new WorkerArtifactException(
                    "Failed to verify staged worker artifact",
                    WorkerFailureCode.INTERNAL_ERROR,
                    e);
        }
    }

    private void validateSize(WorkerTask task, StagedArtifact stagedArtifact) {
        long configuredMax = properties.getArtifact().getMaxMobileBytes().toBytes();
        long taskMax = task.getMaxBytes() == null ? configuredMax : Math.min(task.getMaxBytes(), configuredMax);
        if (stagedArtifact.sizeBytes() < 0 || stagedArtifact.sizeBytes() > taskMax) {
            throw new WorkerArtifactException(
                    "Worker artifact exceeds the allowed size",
                    WorkerFailureCode.CONTENT_INVALID);
        }
        if (task.getExpectedSizeBytes() != null
                && task.getExpectedSizeBytes().longValue() != stagedArtifact.sizeBytes()) {
            throw new WorkerArtifactException(
                    "Worker artifact size does not match the expected resource size",
                    WorkerFailureCode.CONTENT_INVALID);
        }
    }

    private DigestResult digest(String stagingId) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM does not provide SHA-256", e);
        }

        byte[] signature = new byte[SIGNATURE_BYTES];
        int signatureLength = 0;
        int firstNonWhitespace = -1;
        byte[] buffer = new byte[READ_BUFFER_BYTES];

        try (InputStream input = artifactStore.openStaged(stagingId)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
                for (int i = 0; i < read; i++) {
                    int value = buffer[i] & 0xff;
                    if (signatureLength < signature.length) {
                        signature[signatureLength++] = buffer[i];
                    }
                    if (firstNonWhitespace < 0 && !isAsciiWhitespace(value)) {
                        firstNonWhitespace = value;
                    }
                }
            }
        }

        return new DigestResult(
                HexFormat.of().formatHex(digest.digest()),
                firstNonWhitespace,
                signature,
                signatureLength);
    }

    private static void validateHash(WorkerTask task, String claimedSha256, String actualSha256) {
        String claimed = normalizeHash(claimedSha256);
        if (claimed != null && !claimed.equals(actualSha256)) {
            throw new WorkerArtifactException(
                    "Worker artifact checksum does not match the uploaded bytes",
                    WorkerFailureCode.CHECKSUM_MISMATCH);
        }

        String expected = normalizeHash(task.getExpectedSha256());
        if (expected != null && !expected.equals(actualSha256)) {
            throw new WorkerArtifactException(
                    "Worker artifact checksum does not match the expected source artifact",
                    WorkerFailureCode.CHECKSUM_MISMATCH);
        }
    }

    private static void validateContentShape(String expectedContentType, DigestResult result) {
        if (expectedContentType == null || expectedContentType.isBlank()) {
            return;
        }

        String mediaType = expectedContentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if ((mediaType.equals("application/json") || mediaType.endsWith("+json"))
                && result.firstNonWhitespace() != '{'
                && result.firstNonWhitespace() != '[') {
            invalidShape();
        }
        if ((mediaType.equals("application/xml") || mediaType.equals("text/xml") || mediaType.endsWith("+xml"))
                && result.firstNonWhitespace() != '<') {
            invalidShape();
        }
        if (mediaType.equals("application/zip") && !isZipSignature(result.signature(), result.signatureLength())) {
            invalidShape();
        }
    }

    private static boolean isZipSignature(byte[] signature, int length) {
        if (length < 4 || signature[0] != 'P' || signature[1] != 'K') {
            return false;
        }
        int third = signature[2] & 0xff;
        int fourth = signature[3] & 0xff;
        return (third == 3 && fourth == 4)
                || (third == 5 && fourth == 6)
                || (third == 7 && fourth == 8);
    }

    private static void invalidShape() {
        throw new WorkerArtifactException(
                "Worker artifact content does not match the expected media type",
                WorkerFailureCode.CONTENT_INVALID);
    }

    private static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        String normalized = hash.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new WorkerArtifactException(
                    "Invalid SHA-256 checksum format",
                    WorkerFailureCode.CHECKSUM_MISMATCH);
        }
        return normalized;
    }

    private void deleteRejectedStaging(String stagingId, WorkerArtifactException failure) {
        try {
            artifactStore.deleteStaged(stagingId);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static boolean isAsciiWhitespace(int value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private record DigestResult(
            String sha256,
            int firstNonWhitespace,
            byte[] signature,
            int signatureLength) {
    }
}
