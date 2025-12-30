package org.jds.edgar4j.dto.request;

import java.util.Date;
import java.util.List;

import org.jds.edgar4j.config.AppConstants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class FilingSearchRequest {

    @Size(max = 200, message = "Company name must be at most 200 characters")
    private String companyName;

    @Size(max = 10, message = "Ticker must be at most 10 characters")
    private String ticker;

    @Pattern(regexp = "^[0-9]{1,10}$", message = "CIK must be 1-10 digits")
    private String cik;

    @Size(max = 20, message = "Maximum 20 form types allowed")
    private List<String> formTypes;

    private Date dateFrom;
    private Date dateTo;

    @Size(max = 10, message = "Maximum 10 keywords allowed")
    private List<String> keywords;

    @Min(value = 0, message = AppConstants.MSG_PAGE_MIN)
    @Builder.Default
    private int page = AppConstants.DEFAULT_PAGE;

    @Min(value = AppConstants.MIN_PAGE_SIZE, message = AppConstants.MSG_SIZE_MIN)
    @Max(value = AppConstants.MAX_PAGE_SIZE, message = AppConstants.MSG_SIZE_MAX)
    @Builder.Default
    private int size = AppConstants.DEFAULT_PAGE_SIZE;

    @Builder.Default
    private String sortBy = "fillingDate";

    @Pattern(regexp = "^(asc|desc)$", message = "Sort direction must be 'asc' or 'desc'")
    @Builder.Default
    private String sortDir = AppConstants.DEFAULT_SORT_DIRECTION;
}
