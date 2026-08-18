package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.properties.WorkerPilotProperties;
import org.jds.edgar4j.service.DistributedResourceAcquisitionService;
import org.jds.edgar4j.service.DistributedWorkPlanner.DownloadTaskSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TickerResourceAcquisitionServiceImplTest {

    @Mock
    private DistributedResourceAcquisitionService acquisitionService;
    @Mock
    private SecApiConfig secApiConfig;

    @Test
    void tickerResourceIdentityChangesAtConfiguredFreshnessBoundary() {
        when(secApiConfig.getCompanyTickersUrl()).thenReturn("https://www.sec.gov/files/company_tickers.json");
        when(acquisitionService.acquire(any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        DistributedWorkerProperties workerProperties = new DistributedWorkerProperties();
        WorkerPilotProperties pilotProperties = new WorkerPilotProperties();
        ArgumentCaptor<DownloadTaskSpec> taskCaptor = ArgumentCaptor.forClass(DownloadTaskSpec.class);

        service(workerProperties, pilotProperties, "2026-08-18T10:00:00Z").acquireCompanyTickers("job-1");
        org.mockito.Mockito.verify(acquisitionService).acquire(taskCaptor.capture());
        String firstResourceId = taskCaptor.getValue().resourceId();

        org.mockito.Mockito.clearInvocations(acquisitionService);
        service(workerProperties, pilotProperties, "2026-08-18T10:14:00Z").acquireCompanyTickers("job-1");
        org.mockito.Mockito.verify(acquisitionService).acquire(taskCaptor.capture());
        assertEquals(firstResourceId, taskCaptor.getValue().resourceId());

        org.mockito.Mockito.clearInvocations(acquisitionService);
        service(workerProperties, pilotProperties, "2026-08-18T10:15:00Z").acquireCompanyTickers("job-1");
        org.mockito.Mockito.verify(acquisitionService).acquire(taskCaptor.capture());
        assertNotEquals(firstResourceId, taskCaptor.getValue().resourceId());
    }

    private TickerResourceAcquisitionServiceImpl service(
            DistributedWorkerProperties workerProperties,
            WorkerPilotProperties pilotProperties,
            String instant) {
        return new TickerResourceAcquisitionServiceImpl(
                acquisitionService,
                workerProperties,
                secApiConfig,
                pilotProperties,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
