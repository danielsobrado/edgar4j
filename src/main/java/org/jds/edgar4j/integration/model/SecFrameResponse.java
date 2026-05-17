package org.jds.edgar4j.integration.model;

import java.math.BigDecimal;
import java.util.List;

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
public class SecFrameResponse {

    private String taxonomy;
    private String tag;
    private String ccp;
    private String uom;
    private String label;
    private String description;
    private List<FrameEntry> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrameEntry {
        private String accn;
        private Integer cik;
        private String entityName;
        private String loc;
        private String end;
        private BigDecimal val;
    }
}
