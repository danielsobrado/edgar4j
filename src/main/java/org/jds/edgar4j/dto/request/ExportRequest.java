package org.jds.edgar4j.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    public enum ExportFormat {
        CSV,
        JSON
    }

    @Size(max = 1000, message = "Maximum 1000 filing IDs allowed per export")
    private List<String> filingIds;

    @Valid
    private FilingSearchRequest searchCriteria;

    @NotNull(message = "Export format is required")
    private ExportFormat format;
}
