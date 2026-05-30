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

    /** Market capitalization in USD for the matched ticker, when a market-data provider can resolve it. */
    private Long marketCap;
}
