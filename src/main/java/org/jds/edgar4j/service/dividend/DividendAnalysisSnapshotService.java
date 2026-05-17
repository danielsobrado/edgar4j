package org.jds.edgar4j.service.dividend;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.model.DividendAnalysisSnapshot;
import org.jds.edgar4j.port.DividendAnalysisSnapshotDataPort;
import org.jds.edgar4j.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendAnalysisSnapshotService {

    private final DividendAnalysisSnapshotDataPort snapshotDataPort;

    public DividendAnalysisSnapshot saveOverview(DividendOverviewResponse overview) {
        return upsert(overview.getCompany(), snapshot -> snapshot.setOverview(overview), null, false);
    }

    public DividendAnalysisSnapshot getSnapshot(String tickerOrCik) {
        String identifier = blankToNull(tickerOrCik);
        if (identifier == null) {
            throw new IllegalArgumentException("tickerOrCik must not be blank");
        }

        Optional<DividendAnalysisSnapshot> snapshot = identifier.chars().allMatch(Character::isDigit)
                ? findByCik(normalizeCik(identifier))
                : findByTicker(identifier);
        return snapshot.orElseThrow(() -> new ResourceNotFoundException(
                "Dividend analysis snapshot",
                "tickerOrCik",
                tickerOrCik));
    }

    public DividendAnalysisSnapshot saveHistory(DividendHistoryResponse history) {
        return upsert(history.getCompany(), snapshot -> snapshot.setHistory(history), null, false);
    }

    public DividendAnalysisSnapshot saveAlerts(DividendAlertsResponse alerts) {
        return upsert(alerts.getCompany(), snapshot -> snapshot.setAlerts(alerts), null, false);
    }

    public DividendAnalysisSnapshot saveEvents(DividendEventsResponse events) {
        return upsert(events.getCompany(), snapshot -> snapshot.setEvents(events), null, false);
    }

    public DividendAnalysisSnapshot markLiveReconciled(
            DividendOverviewResponse.CompanySummary company,
            Integer factsVersion,
            Instant reconciledAt) {
        return upsert(company, snapshot -> {
        }, factsVersion, true, reconciledAt != null ? reconciledAt : Instant.now());
    }

    private DividendAnalysisSnapshot upsert(
            DividendOverviewResponse.CompanySummary company,
            Consumer<DividendAnalysisSnapshot> mutator,
            Integer factsVersion,
            boolean liveReconciled) {
        return upsert(company, mutator, factsVersion, liveReconciled, Instant.now());
    }

    private DividendAnalysisSnapshot upsert(
            DividendOverviewResponse.CompanySummary company,
            Consumer<DividendAnalysisSnapshot> mutator,
            Integer factsVersion,
            boolean liveReconciled,
            Instant now) {
        if (company == null || company.getCik() == null || company.getCik().isBlank()) {
            throw new IllegalArgumentException("Snapshot company CIK is required");
        }

        String cik = normalizeCik(company.getCik());
        DividendAnalysisSnapshot snapshot = findByCik(cik)
                .or(() -> findByTicker(company.getTicker()))
                .orElseGet(() -> DividendAnalysisSnapshot.builder()
                        .id(cik)
                        .cik(cik)
                        .createdAt(now)
                        .build());

        if (snapshot.getId() == null) {
            snapshot.setId(cik);
        }
        snapshot.setCik(cik);
        snapshot.setTicker(blankToNull(company.getTicker()));
        snapshot.setCompanyName(blankToNull(company.getName()));
        snapshot.setLastComputedAt(now);
        snapshot.setUpdatedAt(now);
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(now);
        }
        if (factsVersion != null) {
            snapshot.setFactsVersion(factsVersion);
        }
        if (liveReconciled) {
            snapshot.setSource(DividendAnalysisSnapshot.SnapshotSource.LIVE_RECONCILED);
            snapshot.setLastReconciledAt(now);
        } else if (snapshot.getSource() == null) {
            snapshot.setSource(DividendAnalysisSnapshot.SnapshotSource.COMPUTED);
        }

        mutator.accept(snapshot);
        return snapshotDataPort.save(snapshot);
    }

    private Optional<DividendAnalysisSnapshot> findByCik(String cik) {
        Optional<DividendAnalysisSnapshot> snapshot = snapshotDataPort.findByCik(cik);
        return snapshot != null ? snapshot : Optional.empty();
    }

    private Optional<DividendAnalysisSnapshot> findByTicker(String ticker) {
        String normalizedTicker = blankToNull(ticker);
        if (normalizedTicker == null) {
            return Optional.empty();
        }
        Optional<DividendAnalysisSnapshot> snapshot = snapshotDataPort.findByTickerIgnoreCase(normalizedTicker);
        return snapshot != null ? snapshot : Optional.empty();
    }

    private String normalizeCik(String cik) {
        String digits = cik.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return cik.trim();
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
