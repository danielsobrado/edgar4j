package org.jds.edgar4j.model;

import java.time.Instant;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "dividend_analysis_snapshots")
public class DividendAnalysisSnapshot {

    @Id
    private String id;

    @Indexed(unique = true)
    private String cik;

    @Indexed
    private String ticker;

    private String companyName;
    private DividendOverviewResponse overview;
    private DividendHistoryResponse history;
    private DividendAlertsResponse alerts;
    private DividendEventsResponse events;

    @Builder.Default
    private SnapshotSource source = SnapshotSource.COMPUTED;

    private Integer factsVersion;
    private Instant lastComputedAt;
    private Instant lastReconciledAt;
    private Instant createdAt;
    private Instant updatedAt;

    public enum SnapshotSource {
        COMPUTED,
        LIVE_RECONCILED
    }
}
