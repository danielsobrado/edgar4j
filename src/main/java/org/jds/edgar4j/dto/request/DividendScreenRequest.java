package org.jds.edgar4j.dto.request;

import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendScreenRequest {

    private List<String> tickersOrCiks;
    private String searchTerm;
    private DividendScreenFilters filters;
    private List<String> metrics;

    @Builder.Default
    private String sort = "score";

    @Builder.Default
    private String direction = "DESC";

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;

    @Builder.Default
    private int candidateLimit = 50;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DividendScreenFilters {
        private Map<String, MetricRange> metrics;
        private List<DividendOverviewResponse.DividendRating> viabilityRatings;
        private List<String> sectors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricRange {
        private Double min;
        private Double max;
    }
}
