package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Per-day count of cached insider activity filings for a form over a date window.
 */
@Data
@Builder
public class InsiderActivityCoverageResponse {

    private String form;
    private LocalDate from;
    private LocalDate to;
    private long totalFilings;
    private List<DayCount> days;

    @Data
    @Builder
    public static class DayCount {
        private LocalDate date;
        private long count;
    }
}

