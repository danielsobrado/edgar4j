package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.jds.edgar4j.service.DownloadTickersService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Slf4j
@Service
public class DownloadTickersServiceImpl implements DownloadTickersService {

    @Value("${edgar4j.urls.companyTickersUrl}")
    private String companyTickersUrl;

    @Value("${edgar4j.urls.companyTickersExchangesUrl}")
    private String companyTickersExchangesUrl;

    @Value("${edgar4j.urls.companyTickersMFsUrl}")
    private String companyTickersMFsUrl;

    @Override
    public void downloadTickers() {
        log.info("Download tickers");
        final HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(companyTickersUrl))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
        
    }

    @Override
    public void downloadTickersExchanges() {
        log.info("Download tickers exchanges");
        final HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(companyTickersExchangesUrl))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
    }

    @Override
    public void downloadTickersMFs() {
        log.info("Download tickers MFs");
        final HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(companyTickersMFsUrl))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
    }

    
}
