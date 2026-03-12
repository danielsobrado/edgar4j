package org.jds.edgar4j.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteFilingSearchRequest {

    private static final int DEFAULT_LIMIT = 100;

    @NotBlank(message = "formType is required")
    private String formType;

    @Size(max = 200, message = "companyName must be at most 200 characters")
    private String companyName;

    @Size(max = 10, message = "ticker must be at most 10 characters")
    private String ticker;

    @Pattern(regexp = "^[0-9]{1,10}$", message = "CIK must be 1-10 digits")
    private String cik;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 500, message = "limit must be at most 500")
    @Builder.Default
    private Integer limit = DEFAULT_LIMIT;
}
