package org.jds.edgar4j.integration.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecTickerResponse {

    private Map<String, TickerEntry> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TickerEntry {
        private int cik_str;
        private String ticker;
        private String title;
    }
}
