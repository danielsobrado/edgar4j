package org.jds.edgar4j.dto.response;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsiderPurchaseResponse {

    private String ticker;
    private String companyName;
    private String cik;
    private String insiderName;
    private String insiderTitle;
    private String ownerType;
    private LocalDate transactionDate;
    private Float purchasePrice;
    private Float transactionShares;
    private Float transactionValue;
    private Double currentPrice;
    private Double percentChange;
    private Double marketCap;
    private boolean sp500;
    private String accessionNumber;
    private String transactionCode;
}
