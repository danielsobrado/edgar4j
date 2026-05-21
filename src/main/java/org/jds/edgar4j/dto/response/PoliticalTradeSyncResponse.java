package org.jds.edgar4j.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticalTradeSyncResponse {

    private String source;
    private String assetType;
    private int requestedPages;
    private int fetchedPages;
    private int fetchedRows;
    private int insertedRows;
    private int updatedRows;
    private int skippedRows;
    private long totalCachedRows;
    private boolean forced;
    private Instant syncedAt;
}
