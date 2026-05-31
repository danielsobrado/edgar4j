package org.jds.edgar4j.dto.request;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticalTradeSyncRequest {

    private String assetType;
    private Integer maxPages;
    private Integer chunkPages;
    private Integer pauseSeconds;
    private LocalDate disclosureDateFrom;
    private LocalDate disclosureDateTo;
    private boolean force;
}
