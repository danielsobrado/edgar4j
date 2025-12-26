package org.jds.edgar4j.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyListResponse {

    private String id;
    private String name;
    private String ticker;
    private String cik;
    private String sic;
    private String sicDescription;
    private String stateOfIncorporation;
    private Long filingCount;
}
