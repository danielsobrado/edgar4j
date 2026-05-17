package org.jds.edgar4j.service.dividend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.model.DividendAnalysisSnapshot;
import org.jds.edgar4j.port.DividendAnalysisSnapshotDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendAnalysisSnapshotServiceTest {

    @Mock
    private DividendAnalysisSnapshotDataPort snapshotDataPort;

    private DividendAnalysisSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new DividendAnalysisSnapshotService(snapshotDataPort);
        lenient().when(snapshotDataPort.save(any(DividendAnalysisSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("saveOverview should create a durable computed snapshot by normalized CIK")
    void saveOverviewShouldCreateComputedSnapshot() {
        when(snapshotDataPort.findByCik("0000000123")).thenReturn(Optional.empty());
        when(snapshotDataPort.findByTickerIgnoreCase("ACME")).thenReturn(Optional.empty());

        DividendOverviewResponse overview = DividendOverviewResponse.builder()
                .company(company("123", "ACME"))
                .build();

        DividendAnalysisSnapshot snapshot = snapshotService.saveOverview(overview);

        assertThat(snapshot.getId()).isEqualTo("0000000123");
        assertThat(snapshot.getCik()).isEqualTo("0000000123");
        assertThat(snapshot.getTicker()).isEqualTo("ACME");
        assertThat(snapshot.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(snapshot.getOverview()).isSameAs(overview);
        assertThat(snapshot.getSource()).isEqualTo(DividendAnalysisSnapshot.SnapshotSource.COMPUTED);
        assertThat(snapshot.getLastComputedAt()).isNotNull();
        assertThat(snapshot.getCreatedAt()).isNotNull();
        assertThat(snapshot.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getSnapshot should resolve persisted snapshots by ticker")
    void getSnapshotShouldResolveByTicker() {
        DividendAnalysisSnapshot existing = DividendAnalysisSnapshot.builder()
                .id("0000000123")
                .cik("0000000123")
                .ticker("ACME")
                .build();
        when(snapshotDataPort.findByTickerIgnoreCase("ACME")).thenReturn(Optional.of(existing));

        DividendAnalysisSnapshot snapshot = snapshotService.getSnapshot("ACME");

        assertThat(snapshot).isSameAs(existing);
    }

    @Test
    @DisplayName("markLiveReconciled should preserve existing payloads and stamp live reconciliation metadata")
    void markLiveReconciledShouldPreserveExistingPayloads() {
        DividendOverviewResponse overview = DividendOverviewResponse.builder()
                .company(company("0000000123", "ACME"))
                .build();
        DividendAnalysisSnapshot existing = DividendAnalysisSnapshot.builder()
                .id("0000000123")
                .cik("0000000123")
                .ticker("ACME")
                .overview(overview)
                .source(DividendAnalysisSnapshot.SnapshotSource.COMPUTED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        Instant reconciledAt = Instant.parse("2026-05-17T10:00:00Z");

        when(snapshotDataPort.findByCik("0000000123")).thenReturn(Optional.of(existing));

        DividendAnalysisSnapshot snapshot = snapshotService.markLiveReconciled(
                company("0000000123", "ACME"),
                7,
                reconciledAt);

        assertThat(snapshot.getOverview()).isSameAs(overview);
        assertThat(snapshot.getFactsVersion()).isEqualTo(7);
        assertThat(snapshot.getSource()).isEqualTo(DividendAnalysisSnapshot.SnapshotSource.LIVE_RECONCILED);
        assertThat(snapshot.getLastReconciledAt()).isEqualTo(reconciledAt);
        assertThat(snapshot.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    private DividendOverviewResponse.CompanySummary company(String cik, String ticker) {
        return DividendOverviewResponse.CompanySummary.builder()
                .cik(cik)
                .ticker(ticker)
                .name("Acme Corp")
                .build();
    }
}
