package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Per-day count of cached political trades (by disclosure date) across a date window. Days with no
 * entry are gaps; the counts let the UI shade a coverage heatmap by trade volume.
 */
@Data
@Builder
public class PoliticalTradeCoverageResponse {

    private LocalDate from;
    private LocalDate to;
    private long totalTrades;
    private List<DayCount> days;

    @Data
    @Builder
    public static class DayCount {
        private LocalDate date;
        private long count;
    }
}
