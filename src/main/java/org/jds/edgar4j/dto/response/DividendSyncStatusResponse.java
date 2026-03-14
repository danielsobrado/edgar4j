package org.jds.edgar4j.dto.response;

import java.time.Instant;
import java.util.List;

import org.jds.edgar4j.model.DividendSyncState;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendSyncStatusResponse {

    private DividendOverviewResponse.CompanySummary company;
    private DividendSyncState.SyncStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastFactsSync;
    private Instant lastEventsSync;
    private Instant lastSuccessfulSync;
    private Instant lastMarketDataSync;
    private Instant lastAnalysisWarmup;
    private String lastAccession;
    private int retryCount;
    private String errorMessage;
    private Instant nextRetryAt;
    private int factsVersion;
    private int newFilingsDetected;
    private boolean refreshedSubmissions;
    private boolean refreshedMarketData;
    private boolean analysisWarmupSucceeded;
    private List<String> warnings;
}
