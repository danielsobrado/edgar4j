package org.jds.edgar4j.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MarketCapBackfillResponse {

    private int trackedTickers;

    private int candidateTickers;

    private int processedTickers;

    private int updatedTickers;

    private int unresolvedTickersCount;

    private int upToDateTickers;

    private int deferredTickers;

    private int batchSize;

    private int maxTickers;

    private long durationMs;

    private List<String> sampleUnresolvedTickers;
}
