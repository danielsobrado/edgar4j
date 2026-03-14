package org.jds.edgar4j.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.jds.edgar4j.service.DividendSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendSyncJobTest {

    @Mock
    private DividendSyncService dividendSyncService;

    @Test
    @DisplayName("syncTrackedCompanies should skip execution when the job is disabled")
    void syncTrackedCompaniesShouldSkipWhenDisabled() {
        DividendSyncJob job = new DividendSyncJob(dividendSyncService, false, 25, false);

        job.syncTrackedCompanies();

        verifyNoInteractions(dividendSyncService);
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncTrackedCompanies should invoke the sync service when enabled")
    void syncTrackedCompaniesShouldInvokeSyncServiceWhenEnabled() {
        DividendSyncJob job = new DividendSyncJob(dividendSyncService, true, 12, true);

        job.syncTrackedCompanies();

        verify(dividendSyncService).syncTrackedCompanies(12, true);
        assertFalse(job.isRunning());
    }
}
