package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.Set;

import org.jds.edgar4j.model.MarketCapSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsiderActivityResponse {

    private String view;
    private String side;
    private String ticker;
    private String companyName;
    private String cik;
    private LocalDate latestTransactionDate;
    private LocalDate transactionDate;
    private String insiderName;
    private String insiderTitle;
    private String ownerType;
    private Integer insiderCount;
    private Integer transactionCount;
    private Float totalShares;
    private Float transactionShares;
    private Float averagePrice;
    private Float transactionPrice;
    private Float totalValue;
    private Float transactionValue;
    private Double currentPrice;
    private Double percentChange;
    private Double marketCap;
    private MarketCapSource marketCapSource;
    private boolean sp500;
    private String accessionNumber;
    private String transactionCode;
    private Set<String> transactionCodes;
}
