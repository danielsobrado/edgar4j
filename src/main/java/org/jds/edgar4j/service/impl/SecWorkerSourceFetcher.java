package org.jds.edgar4j.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.service.WorkerSourceFetcher;
import org.springframework.stereotype.Service;

@Service
public class SecWorkerSourceFetcher implements WorkerSourceFetcher {

    private final SecApiClient secApiClient;
    private final SecApiConfig secApiConfig;

    public SecWorkerSourceFetcher(SecApiClient secApiClient, SecApiConfig secApiConfig) {
        this.secApiClient = secApiClient;
        this.secApiConfig = secApiConfig;
    }

    @Override
    public byte[] fetch(WorkerTask task) {
        Objects.requireNonNull(task, "task");
        String url = task.getSourceUrl();
        String body;
        if (secApiConfig.getCompanyTickersUrl().equals(url)) {
            body = secApiClient.fetchCompanyTickers();
        } else if (secApiConfig.getCompanyTickersExchangesUrl().equals(url)) {
            body = secApiClient.fetchCompanyTickersExchanges();
        } else if (secApiConfig.getCompanyTickersMFsUrl().equals(url)) {
            body = secApiClient.fetchCompanyTickersMutualFunds();
        } else {
            throw new IllegalArgumentException("Unsupported SEC server-worker resource");
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(WorkerTask task) {
        if (task == null || task.getSource() != WorkerSource.SEC_EDGAR || task.getSourceUrl() == null) {
            return false;
        }
        String url = task.getSourceUrl();
        return secApiConfig.getCompanyTickersUrl().equals(url)
                || secApiConfig.getCompanyTickersExchangesUrl().equals(url)
                || secApiConfig.getCompanyTickersMFsUrl().equals(url);
    }
}
