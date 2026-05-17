package org.jds.edgar4j.service.dividend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.DividendAlertResolutionRequest;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.model.DividendAlertResolution;
import org.jds.edgar4j.port.DividendAlertResolutionDataPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendAlertResolutionServiceTest {

    @Mock
    private DividendAlertResolutionDataPort resolutionDataPort;

    @Test
    @DisplayName("filterActiveAlerts should suppress resolved alerts for the same company and period")
    void filterActiveAlertsShouldSuppressResolvedAlertsForSamePeriod() {
        DividendAlertResolutionService service = new DividendAlertResolutionService(resolutionDataPort);
        DividendAlertsResponse.AlertEvent event = event("fcf-payout", LocalDate.of(2025, 12, 31));
        when(resolutionDataPort.findByCik("320193")).thenReturn(List.of(DividendAlertResolution.builder()
                .resolutionKey(service.resolutionKey("0000320193", event))
                .cik("320193")
                .alertId("fcf-payout")
                .status(DividendAlertResolution.ResolutionStatus.RESOLVED)
                .build()));

        List<DividendOverviewResponse.Alert> filtered = service.filterActiveAlerts(
                "0000320193",
                List.of(alert("fcf-payout"), alert("current-ratio")),
                List.of(event, event("current-ratio", LocalDate.of(2025, 12, 31))));

        assertEquals(1, filtered.size());
        assertEquals("current-ratio", filtered.get(0).getId());
    }

    @Test
    @DisplayName("applyResolutionState should decorate and deactivate resolved alert events")
    void applyResolutionStateShouldDecorateResolvedAlertEvents() {
        DividendAlertResolutionService service = new DividendAlertResolutionService(resolutionDataPort);
        DividendAlertsResponse.AlertEvent event = event("fcf-payout", LocalDate.of(2025, 12, 31));
        when(resolutionDataPort.findByCik("320193")).thenReturn(List.of(DividendAlertResolution.builder()
                .resolutionKey(service.resolutionKey("0000320193", event))
                .cik("320193")
                .alertId("fcf-payout")
                .status(DividendAlertResolution.ResolutionStatus.RESOLVED)
                .resolvedBy("analyst")
                .resolvedAt(Instant.parse("2026-01-15T00:00:00Z"))
                .build()));

        List<DividendAlertsResponse.AlertEvent> decorated = service.applyResolutionState(
                "0000320193",
                List.of(event),
                false);

        assertEquals(1, decorated.size());
        assertFalse(decorated.get(0).isActive());
        assertEquals(DividendAlertResolution.ResolutionStatus.RESOLVED, decorated.get(0).getResolutionStatus());
        assertEquals("analyst", decorated.get(0).getResolvedBy());
        assertTrue(service.applyResolutionState("0000320193", List.of(event), true).isEmpty());
    }

    @Test
    @DisplayName("resolve should upsert the alert resolution record")
    void resolveShouldUpsertAlertResolutionRecord() {
        DividendAlertResolutionService service = new DividendAlertResolutionService(resolutionDataPort);
        DividendAlertsResponse.AlertEvent event = event("fcf-payout", LocalDate.of(2025, 12, 31));
        when(resolutionDataPort.findByResolutionKey(service.resolutionKey("0000320193", event))).thenReturn(Optional.empty());
        when(resolutionDataPort.save(any(DividendAlertResolution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DividendAlertResolution saved = service.resolve(
                "0000320193",
                "AAPL",
                event,
                DividendAlertResolutionRequest.builder()
                        .note("Reviewed against latest 10-K")
                        .resolvedBy("analyst")
                        .build());

        assertEquals("320193", saved.getCik());
        assertEquals("AAPL", saved.getTicker());
        assertEquals("fcf-payout", saved.getAlertId());
        assertEquals(DividendAlertResolution.ResolutionStatus.RESOLVED, saved.getStatus());
        assertEquals("Reviewed against latest 10-K", saved.getNote());
    }

    private DividendOverviewResponse.Alert alert(String id) {
        return DividendOverviewResponse.Alert.builder()
                .id(id)
                .severity(DividendOverviewResponse.AlertSeverity.MEDIUM)
                .title(id)
                .description(id)
                .build();
    }

    private DividendAlertsResponse.AlertEvent event(String id, LocalDate periodEnd) {
        return DividendAlertsResponse.AlertEvent.builder()
                .id(id)
                .severity(DividendOverviewResponse.AlertSeverity.MEDIUM)
                .title(id)
                .description(id)
                .periodEnd(periodEnd)
                .filingDate(LocalDate.of(2026, 1, 30))
                .accessionNumber("0000320193-26-000001")
                .active(true)
                .build();
    }
}
