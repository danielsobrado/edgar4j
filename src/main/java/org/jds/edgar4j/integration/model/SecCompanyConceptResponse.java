package org.jds.edgar4j.integration.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecCompanyConceptResponse {

    private String cik;
    private String taxonomy;
    private String tag;
    private String label;
    private String description;
    private String entityName;
    private Map<String, List<FactEntry>> units;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FactEntry {
        private String end;
        private String start;
        private BigDecimal val;
        private String accn;
        private Integer fy;
        private String fp;
        private String form;
        private String filed;
        private String frame;
    }
}
