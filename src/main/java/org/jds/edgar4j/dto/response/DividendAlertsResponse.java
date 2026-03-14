package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendAlertsResponse {

    private DividendOverviewResponse.CompanySummary company;
    private boolean activeOnly;
    private List<DividendOverviewResponse.Alert> activeAlerts;
    private List<AlertEvent> historicalAlerts;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertEvent {
        private String id;
        private DividendOverviewResponse.AlertSeverity severity;
        private String title;
        private String description;
        private LocalDate periodEnd;
        private LocalDate filingDate;
        private String accessionNumber;
        private boolean active;
    }
}
