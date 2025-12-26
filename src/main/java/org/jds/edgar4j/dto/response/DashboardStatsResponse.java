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
public class DashboardStatsResponse {

    private long totalFilings;
    private long companiesTracked;
    private LocalDateTime lastSync;
    private long filingsTodayCount;
    private long form4Count;
    private long form10KCount;
    private long form10QCount;
}
