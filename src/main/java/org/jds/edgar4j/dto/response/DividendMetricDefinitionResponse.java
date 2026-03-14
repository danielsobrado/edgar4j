package org.jds.edgar4j.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendMetricDefinitionResponse {

    private String id;
    private String label;
    private String unit;
    private String formatHint;
    private String group;
    private String description;
}
