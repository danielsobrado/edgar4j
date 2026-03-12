package org.jds.edgar4j.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchRequest {

    private String searchTerm;
    private String ticker;
    private String cik;
    private String sic;
    private String stateOfIncorporation;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private String sortBy = "name";

    @Builder.Default
    private String sortDir = "asc";
}
