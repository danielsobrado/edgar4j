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
public class DividendEventsResponse {

    private DividendOverviewResponse.CompanySummary company;
    private List<DividendEvent> events;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DividendEvent {
        private String id;
        private EventType eventType;
        private String formType;
        private String accessionNumber;
        private LocalDate filedDate;
        private LocalDate declarationDate;
        private LocalDate recordDate;
        private LocalDate payableDate;
        private Double amountPerShare;
        private DividendType dividendType;
        private EventConfidence confidence;
        private String extractionMethod;
        private String sourceSection;
        private String textSnippet;
        private String policyLanguage;
        private String url;
    }

    public enum EventType {
        DECLARATION,
        SPECIAL,
        INCREASE,
        DECREASE,
        SUSPENSION,
        REINSTATEMENT,
        POLICY_CHANGE
    }

    public enum DividendType {
        REGULAR,
        SPECIAL,
        QUARTERLY,
        MONTHLY,
        ANNUAL,
        INTERIM,
        UNKNOWN
    }

    public enum EventConfidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
