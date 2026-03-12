package org.jds.edgar4j.service;

import java.util.List;

import org.jds.edgar4j.dto.response.InsiderPurchaseResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseSummary;
import org.springframework.data.domain.Page;

public interface InsiderPurchaseService {

    Page<InsiderPurchaseResponse> getRecentInsiderPurchases(
            int lookbackDays,
            Double minMarketCap,
            boolean sp500Only,
            Double minTransactionValue,
            String sortBy,
            String sortDir,
            int page,
            int size);

    List<InsiderPurchaseResponse> getTopInsiderPurchases(int limit);

    InsiderPurchaseSummary getSummary(int lookbackDays);
}
