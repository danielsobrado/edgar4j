package org.jds.edgar4j.service;

import java.util.List;

import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;

public interface DividendSyncService {

    DividendSyncStatusResponse syncCompany(String tickerOrCik, boolean refreshMarketData);

    DividendSyncStatusResponse getSyncStatus(String tickerOrCik);

    DividendSyncStatusResponse trackCompany(String tickerOrCik, boolean syncNow, boolean refreshMarketData);

    DividendSyncStatusResponse untrackCompany(String tickerOrCik);

    List<DividendSyncStatusResponse> syncTrackedCompanies(int maxCompanies, boolean refreshMarketData);
}
