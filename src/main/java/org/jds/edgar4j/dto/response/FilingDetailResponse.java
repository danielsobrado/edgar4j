package org.jds.edgar4j.dto.response;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilingDetailResponse {

    private String id;
    private String companyName;
    private String ticker;
    private String cik;
    private String formType;
    private String formTypeDescription;
    private Date filingDate;
    private Date reportDate;
    private String accessionNumber;
    private String fileNumber;
    private String filmNumber;
    private String primaryDocument;
    private String primaryDocDescription;
    private String url;
    private String items;
    @JsonProperty("isXBRL")
    private boolean isXBRL;
    @JsonProperty("isInlineXBRL")
    private boolean isInlineXBRL;
    private String contentPreview;
    private List<String> documentTags;
}
