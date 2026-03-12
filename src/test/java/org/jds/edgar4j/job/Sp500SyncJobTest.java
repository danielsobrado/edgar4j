package org.jds.edgar4j.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Sp500SyncJobTest {

    @Mock
    private Sp500Service sp500Service;

    @Test
    @DisplayName("syncSp500 should skip execution when the job is disabled")
    void syncSp500ShouldSkipWhenDisabled() {
        Sp500SyncJob job = new Sp500SyncJob(sp500Service);
        ReflectionTestUtils.setField(job, "enabled", false);

        job.syncSp500();

        verifyNoInteractions(sp500Service);
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncSp500 should invoke the service when enabled")
    void syncSp500ShouldInvokeServiceWhenEnabled() {
        Sp500SyncJob job = new Sp500SyncJob(sp500Service);
        ReflectionTestUtils.setField(job, "enabled", true);
        when(sp500Service.syncFromWikipedia()).thenReturn(List.of());

        job.syncSp500();

        verify(sp500Service).syncFromWikipedia();
        assertFalse(job.isRunning());
    }
}
