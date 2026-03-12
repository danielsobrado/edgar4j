package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteFilingSearchResponse {

    private String formType;
    private String dateFrom;
    private String dateTo;
    private int totalMatches;
    private int returnedMatches;
    private int uniqueCompanyCount;
    private boolean truncated;
    private int searchedDateCount;
    private int availableDateCount;
    private int unavailableDateCount;
    private List<RemoteFilingResponse> filings;
}
