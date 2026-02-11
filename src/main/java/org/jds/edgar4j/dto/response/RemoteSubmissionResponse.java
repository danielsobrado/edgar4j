package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteSubmissionResponse {

    private String cik;
    private String companyName;
    private String sic;
    private String sicDescription;
    private List<String> tickers;
    private List<String> exchanges;
    private int recentFilingsCount;
    private List<RemoteSubmissionFilingResponse> recentFilings;
}

