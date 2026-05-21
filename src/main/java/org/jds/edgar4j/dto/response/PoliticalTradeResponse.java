package org.jds.edgar4j.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticalTradeResponse {

    private String id;
    private String sourceTradeId;
    private String politicianName;
    private String party;
    private String chamber;
    private String state;
    private String issuerName;
    private String ticker;
    private LocalDate disclosureDate;
    private LocalDate tradedDate;
    private Integer filedAfterDays;
    private String owner;
    private String transactionType;
    private String amountLabel;
    private Double amountMin;
    private Double amountMax;
    private Double price;
    private String assetType;
    private String sourceTradeUrl;
    private String source;
    private Instant firstSeenAt;
    private Instant updatedAt;
}
