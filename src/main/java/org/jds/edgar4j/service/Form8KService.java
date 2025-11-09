package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form8K;
import org.springframework.stereotype.Service;

/**
 * Service for downloading and parsing SEC Form 8-K filings
 * Form 8-K is filed when material events occur
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Service
public interface Form8KService {

    /**
     * Download a Form 8-K filing from SEC EDGAR
     *
     * @param cik The company's CIK (Central Index Key)
     * @param accessionNumber The SEC accession number
     * @param primaryDocument The primary document filename
     * @return CompletableFuture with HTTP response containing the form HTML/XML
     */
    CompletableFuture<HttpResponse<String>> downloadForm8K(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parse Form 8-K HTML/XML into Form8K object
     *
     * @param html The raw HTML/XML content from SEC
     * @return Parsed Form8K object with event items
     */
    Form8K parseForm8K(String html);
}
