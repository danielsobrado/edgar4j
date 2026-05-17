package org.jds.edgar4j.service.dividend;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.DividendAlertResolutionRequest;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.exception.ResourceNotFoundException;
import org.jds.edgar4j.model.DividendAlertResolution;
import org.jds.edgar4j.port.DividendAlertResolutionDataPort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendAlertResolutionService {

    private final DividendAlertResolutionDataPort resolutionDataPort;
    private final Clock clock = Clock.systemUTC();

    public List<DividendOverviewResponse.Alert> filterActiveAlerts(
            String cik,
            List<DividendOverviewResponse.Alert> alerts,
            List<DividendAlertsResponse.AlertEvent> activeEvents) {
        if (alerts == null || alerts.isEmpty()) {
            return List.of();
        }

        Map<String, DividendAlertsResponse.AlertEvent> eventByAlertId = activeEvents.stream()
                .collect(Collectors.toMap(
                        DividendAlertsResponse.AlertEvent::getId,
                        Function.identity(),
                        (left, right) -> left));
        Set<String> suppressedKeys = activeResolutionKeys(cik);

        return alerts.stream()
                .filter(alert -> {
                    DividendAlertsResponse.AlertEvent event = eventByAlertId.get(alert.getId());
                    return event == null || !suppressedKeys.contains(resolutionKey(cik, event));
                })
                .toList();
    }

    public List<DividendAlertsResponse.AlertEvent> applyResolutionState(
            String cik,
            List<DividendAlertsResponse.AlertEvent> events,
            boolean activeOnly) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        Map<String, DividendAlertResolution> resolutions = activeResolutions(cik);
        List<DividendAlertsResponse.AlertEvent> decorated = events.stream()
                .map(event -> decorate(event, resolutions.get(resolutionKey(cik, event))))
                .toList();

        return activeOnly
                ? decorated.stream().filter(DividendAlertsResponse.AlertEvent::isActive).toList()
                : decorated;
    }

    public DividendAlertResolution resolve(
            String cik,
            String ticker,
            DividendAlertsResponse.AlertEvent event,
            DividendAlertResolutionRequest request) {
        if (event == null) {
            throw new ResourceNotFoundException("Dividend alert event", "alertId", "null");
        }

        Instant now = Instant.now(clock);
        DividendAlertResolution.ResolutionStatus status = request != null && request.getStatus() != null
                ? request.getStatus()
                : DividendAlertResolution.ResolutionStatus.RESOLVED;
        String key = resolutionKey(cik, event);
        DividendAlertResolution resolution = resolutionDataPort.findByResolutionKey(key)
                .orElseGet(() -> DividendAlertResolution.builder()
                        .resolutionKey(key)
                        .cik(normalizeCik(cik))
                        .ticker(normalizeTicker(ticker))
                        .alertId(event.getId())
                        .periodEnd(event.getPeriodEnd())
                        .accessionNumber(event.getAccessionNumber())
                        .createdAt(now)
                        .build());

        resolution.setTicker(normalizeTicker(ticker));
        resolution.setStatus(status);
        resolution.setNote(trimToNull(request != null ? request.getNote() : null));
        resolution.setResolvedBy(trimToNull(request != null ? request.getResolvedBy() : null));
        resolution.setResolvedAt(now);
        resolution.setSnoozedUntil(request != null ? request.getSnoozedUntil() : null);
        resolution.setUpdatedAt(now);
        return resolutionDataPort.save(resolution);
    }

    public boolean reopen(String cik, DividendAlertsResponse.AlertEvent event) {
        if (event == null) {
            return false;
        }
        Optional<DividendAlertResolution> existing = resolutionDataPort.findByResolutionKey(resolutionKey(cik, event));
        existing.ifPresent(resolution -> resolutionDataPort.deleteById(resolution.getId()));
        return existing.isPresent();
    }

    public String resolutionKey(String cik, DividendAlertsResponse.AlertEvent event) {
        String period = event.getPeriodEnd() != null ? event.getPeriodEnd().toString() : "no-period";
        String accession = trimToNull(event.getAccessionNumber());
        return resolutionKey(cik, event.getId(), period, accession);
    }

    public String resolutionKey(String cik, String alertId, LocalDate periodEnd, String accessionNumber) {
        return resolutionKey(cik, alertId, periodEnd != null ? periodEnd.toString() : "no-period", accessionNumber);
    }

    private DividendAlertsResponse.AlertEvent decorate(
            DividendAlertsResponse.AlertEvent event,
            DividendAlertResolution resolution) {
        if (resolution == null || !isSuppressionActive(resolution)) {
            return event;
        }

        return DividendAlertsResponse.AlertEvent.builder()
                .id(event.getId())
                .severity(event.getSeverity())
                .title(event.getTitle())
                .description(event.getDescription())
                .periodEnd(event.getPeriodEnd())
                .filingDate(event.getFilingDate())
                .accessionNumber(event.getAccessionNumber())
                .active(false)
                .resolutionStatus(resolution.getStatus())
                .resolutionNote(resolution.getNote())
                .resolvedBy(resolution.getResolvedBy())
                .resolvedAt(resolution.getResolvedAt())
                .snoozedUntil(resolution.getSnoozedUntil())
                .build();
    }

    private Set<String> activeResolutionKeys(String cik) {
        return activeResolutions(cik).keySet();
    }

    private Map<String, DividendAlertResolution> activeResolutions(String cik) {
        String normalizedCik = normalizeCik(cik);
        if (normalizedCik == null) {
            return Map.of();
        }

        List<DividendAlertResolution> storedResolutions = resolutionDataPort.findByCik(normalizedCik);
        if (storedResolutions == null || storedResolutions.isEmpty()) {
            return Map.of();
        }

        Map<String, DividendAlertResolution> resolutions = new HashMap<>();
        for (DividendAlertResolution resolution : storedResolutions) {
            if (resolution.getResolutionKey() != null && isSuppressionActive(resolution)) {
                resolutions.put(resolution.getResolutionKey(), resolution);
            }
        }
        return resolutions;
    }

    private boolean isSuppressionActive(DividendAlertResolution resolution) {
        if (resolution.getStatus() == DividendAlertResolution.ResolutionStatus.RESOLVED) {
            return true;
        }
        return resolution.getStatus() == DividendAlertResolution.ResolutionStatus.SNOOZED
                && resolution.getSnoozedUntil() != null
                && resolution.getSnoozedUntil().isAfter(Instant.now(clock));
    }

    private String resolutionKey(String cik, String alertId, String period, String accessionNumber) {
        return String.join("|",
                normalizeCik(cik),
                normalizeAlertId(alertId),
                period != null ? period : "no-period",
                trimToNull(accessionNumber) != null ? trimToNull(accessionNumber) : "no-accession");
    }

    private String normalizeCik(String cik) {
        String trimmed = trimToNull(cik);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceFirst("^0+(?!$)", "");
        return digits.isBlank() ? trimmed : digits;
    }

    private String normalizeTicker(String ticker) {
        String trimmed = trimToNull(ticker);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeAlertId(String alertId) {
        return Objects.requireNonNull(trimToNull(alertId), "alertId must not be blank")
                .toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
