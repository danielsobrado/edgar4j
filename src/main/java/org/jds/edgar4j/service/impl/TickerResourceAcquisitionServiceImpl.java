package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerResourceIds.COMPANY_TICKERS;
import static org.jds.edgar4j.constants.WorkerResourceIds.COMPANY_TICKERS_EXCHANGES;
import static org.jds.edgar4j.constants.WorkerResourceIds.COMPANY_TICKERS_MUTUAL_FUNDS;
import static org.jds.edgar4j.constants.WorkerTaskPriorities.BACKGROUND;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.DistributedResourceAcquisitionService;
import org.jds.edgar4j.service.DistributedWorkPlanner.DownloadTaskSpec;
import org.jds.edgar4j.service.TickerResourceAcquisitionService;
import org.springframework.stereotype.Service;

@Service
public class TickerResourceAcquisitionServiceImpl implements TickerResourceAcquisitionService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final DistributedResourceAcquisitionService acquisitionService;
    private final DistributedWorkerProperties properties;
    private final SecApiConfig secApiConfig;
    private final Clock clock;

    public TickerResourceAcquisitionServiceImpl(
            DistributedResourceAcquisitionService acquisitionService,
            DistributedWorkerProperties properties,
            SecApiConfig secApiConfig,
            Clock clock) {
        this.acquisitionService = acquisitionService;
        this.properties = properties;
        this.secApiConfig = secApiConfig;
        this.clock = clock;
    }

    @Override
    public boolean isDistributedAcquisitionEnabled() {
        return properties.isEnabled() && properties.getServerWorker().isEnabled();
    }

    @Override
    public String acquireCompanyTickers(String parentDownloadJobId) {
        return acquire(parentDownloadJobId, COMPANY_TICKERS, secApiConfig.getCompanyTickersUrl());
    }

    @Override
    public String acquireCompanyTickersExchanges(String parentDownloadJobId) {
        return acquire(parentDownloadJobId, COMPANY_TICKERS_EXCHANGES, secApiConfig.getCompanyTickersExchangesUrl());
    }

    @Override
    public String acquireCompanyTickersMutualFunds(String parentDownloadJobId) {
        return acquire(parentDownloadJobId, COMPANY_TICKERS_MUTUAL_FUNDS, secApiConfig.getCompanyTickersMFsUrl());
    }

    private String acquire(String parentDownloadJobId, String resourcePrefix, String sourceUrl) {
        String freshnessKey = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        long maxBytes = properties.getArtifact().getMaxMobileBytes().toBytes();
        DownloadTaskSpec task = new DownloadTaskSpec(
                parentDownloadJobId,
                resourcePrefix + freshnessKey,
                WorkerSource.SEC_EDGAR,
                sourceUrl,
                JSON_CONTENT_TYPE,
                null,
                null,
                maxBytes,
                BACKGROUND);
        return new String(acquisitionService.acquire(task), StandardCharsets.UTF_8);
    }
}
