package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.response.DividendReconciliationResponse;

public interface DividendReconciliationService {

    DividendReconciliationResponse reconcile(String tickerOrCik, boolean refreshMarketData);
}
