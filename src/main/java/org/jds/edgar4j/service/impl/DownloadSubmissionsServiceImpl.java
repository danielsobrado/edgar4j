package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;

import org.jds.edgar4j.service.DownloadSubmissionsService;
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
public class DownloadSubmissionsServiceImpl implements DownloadSubmissionsService {

    @Value("${edgar4j.urls.submissionsCIKUrl}")
    private String submissionsCIKUrl;

    @Override
    public void downloadSubmissions(String cik) {
        log.info("Download submissions for CIK: {}", cik);

        long cikLong;
        try {
                cikLong = Long.parseLong(cik);
        } catch (NumberFormatException e) {
                log.error("CIK is not a number: {}", cik);
                return;
        }

        DecimalFormat df = new DecimalFormat("0000000000");

        final HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(submissionsCIKUrl+df.format(cikLong)+".json"))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
        
    }

    
}
