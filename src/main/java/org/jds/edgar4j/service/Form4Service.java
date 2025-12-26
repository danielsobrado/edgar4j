package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form4;
import org.springframework.stereotype.Service;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Service
public interface Form4Service {

    CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument);
    Form4 parseForm4(String raw);

}

