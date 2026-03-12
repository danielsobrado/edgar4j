package org.jds.edgar4j.integration.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecTickerExchangeResponse {

    private List<String> fields;
    private List<List<Object>> data;
}
