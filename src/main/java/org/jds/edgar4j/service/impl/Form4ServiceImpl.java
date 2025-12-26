package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.service.Form4Service;
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
public class Form4ServiceImpl implements Form4Service {

        @Value("${edgar4j.urls.edgarDataArchivesUrl}")
        private String edgarDataArchivesUrl;

        // if isDirector == 1:
        //     owner = 'Director'
        // elif isOfficer == 1:
        //     owner = 'Officer'
        // elif isTenOwner == 1:
        //     owner = '10% Owner'
        // elif isOther == 1:
        //     owner = 'Other'
        // else:
        //     owner = 'Unknown'

        public CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument) {
                log.info("Download form 4");

                final String formURL = edgarDataArchivesUrl + "/" + cik + "/" + accessionNumber.replace("-", "") + "/" + primaryDocument;

                log.debug("Form URL: {}", formURL);

                final HttpClient httpClient = HttpClient.newHttpClient();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(formURL))
                        .build();
                HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
                return httpClient.sendAsync(httpRequest, bodyHandler);
        }
        
        public Form4 parseForm4(String raw) {
                log.info("Parse form 4");

                Form4 form4 = new Form4();
                return form4;
        }
    
}

