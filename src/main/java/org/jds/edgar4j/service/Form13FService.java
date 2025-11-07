package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form13F;
import org.springframework.stereotype.Service;

/**
 * Service for downloading and parsing SEC Form 13F filings
 * Form 13F is filed quarterly by institutional investment managers
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Service
public interface Form13FService {

    /**
     * Download a Form 13F filing from SEC EDGAR
     *
     * @param cik The institution's CIK (Central Index Key)
     * @param accessionNumber The SEC accession number
     * @param primaryDocument The primary document filename
     * @return CompletableFuture with HTTP response containing the form XML
     */
    CompletableFuture<HttpResponse<String>> downloadForm13F(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parse Form 13F XML into Form13F object
     *
     * @param xml The raw XML content from SEC
     * @return Parsed Form13F object with holdings
     */
    Form13F parseForm13F(String xml);
}
