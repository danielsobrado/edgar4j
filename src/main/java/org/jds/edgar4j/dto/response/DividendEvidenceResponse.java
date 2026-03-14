package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendEvidenceResponse {

    private DividendOverviewResponse.CompanySummary company;
    private DividendOverviewResponse.SourceFiling filing;
    private List<EvidenceHighlight> highlights;
    private String cleanedText;
    private boolean truncated;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceHighlight {
        private String id;
        private DividendEventsResponse.EventType eventType;
        private DividendEventsResponse.EventConfidence confidence;
        private String sourceSection;
        private String snippet;
        private String policyLanguage;
    }
}
