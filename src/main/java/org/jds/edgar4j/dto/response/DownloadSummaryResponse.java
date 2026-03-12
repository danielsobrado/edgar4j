package org.jds.edgar4j.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadSummaryResponse {
    private long tickerRecordsImported;
    private LocalDateTime lastTickerUpdate;
}
