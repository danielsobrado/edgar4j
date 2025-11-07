package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.InsiderForm;
import org.springframework.stereotype.Service;

/**
 * Service for downloading and parsing SEC Insider Forms (Forms 3, 4, and 5)
 *
 * Forms covered:
 * - Form 3: Initial Statement of Beneficial Ownership
 * - Form 4: Statement of Changes in Beneficial Ownership
 * - Form 5: Annual Statement of Changes in Beneficial Ownership
 *
 * All three forms share identical XML structure and can be parsed using the same logic.
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Service
public interface InsiderFormService {

    /**
     * Download an insider form (3, 4, or 5) from SEC EDGAR
     *
     * @param cik The company's CIK (Central Index Key)
     * @param accessionNumber The SEC accession number
     * @param primaryDocument The primary document filename
     * @return CompletableFuture with HTTP response containing the form HTML/XML
     */
    CompletableFuture<HttpResponse<String>> downloadInsiderForm(String cik, String accessionNumber, String primaryDocument);

    /**
     * Parse insider form HTML/XML into InsiderForm object
     *
     * @param raw The raw HTML/XML content from SEC
     * @param formType The form type ("3", "4", or "5")
     * @return Parsed InsiderForm object
     */
    InsiderForm parseInsiderForm(String raw, String formType);

}
