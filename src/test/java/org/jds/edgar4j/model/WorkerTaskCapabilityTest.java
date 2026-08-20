package org.jds.edgar4j.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class WorkerTaskCapabilityTest {

    @Test
    void checksumlessLegacySecDownloadRequiresTrustedSource() {
        WorkerTask task = WorkerTask.builder()
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .build();

        assertTrue(task.getRequiredCapabilities().contains(WorkerCapability.TRUSTED_SOURCE));
    }

    @Test
    void expectedChecksumKeepsRemoteDownloadEligible() {
        WorkerTask task = WorkerTask.builder()
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .expectedSha256("a".repeat(64))
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .build();

        assertFalse(task.getRequiredCapabilities().contains(WorkerCapability.TRUSTED_SOURCE));
    }
}
