package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.jds.edgar4j.model.WorkerTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StrictWorkerSourceResourcePolicyTest {

    @Mock
    private WorkerSourceResourcePolicyImpl delegate;

    @Test
    void alternateHttpsPortIsRejectedAfterBasePolicyValidation() {
        StrictWorkerSourceResourcePolicy policy = new StrictWorkerSourceResourcePolicy(delegate);
        WorkerTask task = WorkerTask.builder()
                .sourceUrl("https://data.sec.gov:8443/submissions/file.json")
                .build();

        assertThrows(IllegalArgumentException.class, () -> policy.validate(task));
        verify(delegate).validate(task);
    }
}
