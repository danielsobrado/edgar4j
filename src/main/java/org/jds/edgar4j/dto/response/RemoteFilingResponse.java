package org.jds.edgar4j.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteFilingResponse {

    private String cik;
    private String companyName;
    private String formType;
    private String filingDate;
    private String accessionNumber;
    private String archivePath;
    private String filingUrl;
}
