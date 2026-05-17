package org.jds.edgar4j.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendReconciliationResponse {

    private DividendOverviewResponse.CompanySummary company;
    private DividendSyncStatusResponse syncStatus;
    private DividendOverviewResponse overview;
    private DividendHistoryResponse history;
    private DividendAlertsResponse alerts;
    private DividendEventsResponse events;
    private Instant reconciledAt;
    private List<String> warnings;
}
