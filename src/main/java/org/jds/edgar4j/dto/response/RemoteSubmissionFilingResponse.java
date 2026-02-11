package org.jds.edgar4j.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteSubmissionFilingResponse {

    private String accessionNumber;
    private String formType;
    private String filingDate;
    private String reportDate;
    private String primaryDocument;
    private String primaryDocDescription;
}

