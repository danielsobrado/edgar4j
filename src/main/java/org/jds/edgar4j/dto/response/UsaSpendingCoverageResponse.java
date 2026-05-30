package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Coverage of USAspending award downloads across a date window. Each range is the action-date span
 * of a completed download job; the union of ranges is the data we have, and any day in the window
 * not inside a range is a gap that can be downloaded.
 */
@Data
@Builder
public class UsaSpendingCoverageResponse {

    private LocalDate from;
    private LocalDate to;
    private List<CoverageRange> ranges;

    @Data
    @Builder
    public static class CoverageRange {
        private String jobId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private long rows;
        private LocalDateTime completedAt;
    }
}
