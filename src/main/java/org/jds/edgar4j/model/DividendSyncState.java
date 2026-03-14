package org.jds.edgar4j.model;

import java.time.Instant;

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
@Document(collection = "dividend_sync_states")
public class DividendSyncState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String cik;

    @Indexed
    private String ticker;

    private String companyName;

    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.IDLE;

    private Instant lastFactsSync;
    private Instant lastEventsSync;
    private Instant lastSuccessfulSync;
    private Instant lastMarketDataSync;
    private Instant lastAnalysisWarmup;
    private String lastAccession;
    private String errorMessage;

    @Builder.Default
    private int retryCount = 0;

    private Instant nextRetryAt;

    @Builder.Default
    private int factsVersion = 0;

    @Builder.Default
    private int lastNewFilingsDetected = 0;

    private Instant createdAt;
    private Instant updatedAt;

    public enum SyncStatus {
        IDLE,
        IN_PROGRESS,
        ERROR
    }
}
