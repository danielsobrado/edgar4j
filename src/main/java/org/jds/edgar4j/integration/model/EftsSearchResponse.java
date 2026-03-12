package org.jds.edgar4j.integration.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EftsSearchResponse {

    private Hits hits;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hits {
        private Total total;
        private List<Hit> hits;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Total {
        private int value;
        private String relation;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hit {
        @JsonProperty("_id")
        private String id;

        @JsonProperty("_source")
        private Source source;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        @JsonProperty("entity_name")
        private String entityName;

        @JsonProperty("file_date")
        private String fileDate;

        @JsonProperty("period_of_report")
        private String periodOfReport;

        @JsonProperty("form_type")
        private String formType;

        @JsonProperty("file_num")
        private String fileNum;

        @JsonProperty("entity_id")
        private List<String> entityId;

        @JsonProperty("display_names")
        private List<String> displayNames;

        @JsonProperty("accession_number")
        private String accessionNumber;

        @JsonProperty("adsh")
        private String adsh;

        @JsonProperty("cik")
        private String cik;

        @JsonProperty("ciks")
        private List<String> ciks;
    }
}
