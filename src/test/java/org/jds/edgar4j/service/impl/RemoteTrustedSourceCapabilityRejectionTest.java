package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.EnumSet;

import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerPlatform;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteTrustedSourceCapabilityRejectionTest {

    @Mock
    private WorkerCoordinatorServiceImpl delegate;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerParentJobService parentJobService;

    @Test
    void remoteSessionCannotClaimTrustedSourceCapability() {
        ParentAwareWorkerCoordinatorService service = new ParentAwareWorkerCoordinatorService(
                delegate,
                taskDataPort,
                parentJobService);
        WorkerSessionRequest request = new WorkerSessionRequest(
                1,
                WorkerPlatform.WEB,
                "malicious-client",
                EnumSet.of(
                        WorkerCapability.DOWNLOAD,
                        WorkerCapability.SHA256,
                        WorkerCapability.TRUSTED_SOURCE),
                1);

        assertThrows(IllegalArgumentException.class, () -> service.openSession("principal", request));
        verify(delegate, never()).openSession("principal", request);
    }
}
