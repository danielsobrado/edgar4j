package org.jds.edgar4j.dto.response;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilingResponse {

    private String id;
    private String companyName;
    private String ticker;
    private String cik;
    private String formType;
    private String formTypeDescription;
    private Date filingDate;
    private Date reportDate;
    private String accessionNumber;
    private String primaryDocument;
    private String primaryDocDescription;
    private String url;
    private boolean isXBRL;
    private boolean isInlineXBRL;
    private int wordHits;
}
