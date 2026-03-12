package org.jds.edgar4j.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsiderPurchaseSummary {

    private int totalPurchases;
    private int uniqueCompanies;
    private double totalPurchaseValue;
    private double averagePercentChange;
    private int positiveChangeCount;
    private int negativeChangeCount;
}
