package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form4;
import org.springframework.stereotype.Service;

/**
 * Service for SEC Form 4 filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 * @deprecated Use {@link InsiderFormService} instead which supports Forms 3, 4, and 5
 */
@Service
@Deprecated
public interface Form4Service {

    CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument);
    Form4 parseForm4(String raw);

}
