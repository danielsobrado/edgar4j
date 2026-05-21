package org.jds.edgar4j.dto.request;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticalTradeScreenRequest {

    private String politician;
    private String ticker;
    private String issuer;
    private String party;
    private String chamber;
    private String state;
    private String assetType;
    private String transactionType;
    private String owner;
    private LocalDate tradedDateFrom;
    private LocalDate tradedDateTo;
    private LocalDate disclosureDateFrom;
    private LocalDate disclosureDateTo;
    private Double minAmount;
    private Double maxAmount;
    private String sortBy;
    private String sortDir;
    private int page;
    private int size;
}
