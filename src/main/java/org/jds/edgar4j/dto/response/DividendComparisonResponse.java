package org.jds.edgar4j.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendComparisonResponse {

    private List<DividendMetricDefinitionResponse> metrics;
    private List<ComparisonRow> companies;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonRow {
        private DividendOverviewResponse.CompanySummary company;
        private DividendOverviewResponse.ViabilitySummary viability;
        private Map<String, Double> values;
        private List<String> warnings;
    }
}
