package org.jds.edgar4j.service;

public interface TickerResourceAcquisitionService {

    boolean isDistributedAcquisitionEnabled();

    default String acquireCompanyTickers() {
        return acquireCompanyTickers(null);
    }

    String acquireCompanyTickers(String parentDownloadJobId);

    default String acquireCompanyTickersExchanges() {
        return acquireCompanyTickersExchanges(null);
    }

    String acquireCompanyTickersExchanges(String parentDownloadJobId);

    default String acquireCompanyTickersMutualFunds() {
        return acquireCompanyTickersMutualFunds(null);
    }

    String acquireCompanyTickersMutualFunds(String parentDownloadJobId);
}
