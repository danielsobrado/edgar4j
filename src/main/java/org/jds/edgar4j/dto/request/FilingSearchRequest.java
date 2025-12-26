package org.jds.edgar4j.dto.request;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilingSearchRequest {

    private String companyName;
    private String ticker;
    private String cik;
    private List<String> formTypes;
    private Date dateFrom;
    private Date dateTo;
    private List<String> keywords;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private String sortBy = "fillingDate";

    @Builder.Default
    private String sortDir = "desc";
}
