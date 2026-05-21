package org.jds.edgar4j.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsaSpendingCompanyMatchResponse {

    private String cik;
    private String ticker;
    private String companyName;
    private int confidence;
    private String sourceField;
    private String sourceValue;
    private String matchMethod;
}
