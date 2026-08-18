package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.junit.jupiter.api.Test;

class WorkerSourceResourcePolicyImplTest {

    private final WorkerSourceResourcePolicyImpl policy = new WorkerSourceResourcePolicyImpl(
            new DistributedWorkerProperties());

    @Test
    void exactConfiguredHttpsHostIsAccepted() {
        assertDoesNotThrow(() -> policy.validate(task(
                "https://data.sec.gov/submissions/CIK0000320193.json")));
    }

    @Test
    void lookalikeHostIsRejected() {
        assertThrows(
                WorkerCoordinatorException.class,
                () -> policy.validate(task("https://data.sec.gov.evil.example/submissions/file.json")));
    }

    @Test
    void plainHttpIsRejected() {
        assertThrows(
                WorkerCoordinatorException.class,
                () -> policy.validate(task("http://data.sec.gov/submissions/file.json")));
    }

    private static WorkerTask task(String sourceUrl) {
        return WorkerTask.builder()
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl(sourceUrl)
                .build();
    }
}
