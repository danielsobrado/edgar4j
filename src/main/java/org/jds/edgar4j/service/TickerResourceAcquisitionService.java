package org.jds.edgar4j.service;

public interface TickerResourceAcquisitionService {

    boolean isDistributedAcquisitionEnabled();

    String acquireCompanyTickers();

    String acquireCompanyTickersExchanges();

    String acquireCompanyTickersMutualFunds();
}
