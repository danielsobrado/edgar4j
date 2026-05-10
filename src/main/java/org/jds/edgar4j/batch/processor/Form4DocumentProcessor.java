package org.jds.edgar4j.batch.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.exception.Form4DocumentProcessingException;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecForm4DocumentSupport;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Batch ItemProcessor for Form 4 documents
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Form4DocumentProcessor implements ItemProcessor<String, List<InsiderTransaction>> {
    private static final String ERROR_MSG = "Failed to process Form 4 document {}";

    private final SecApiClient secApiClient;
    private final Form4ParserService form4ParserService;
    @Value("${edgar4j.batch.fail-on-item-error:false}")
    private boolean failOnProcessingError;

    @Override
    public List<InsiderTransaction> process(String accessionNumber) throws Exception {
        if (accessionNumber == null || accessionNumber.isBlank()) {
            log.warn("Skipping blank accession number");
            return List.of();
        }

        log.debug("Processing Form 4 document: {}", accessionNumber);

        try {
            String xmlContent = fetchForm4Document(accessionNumber);

            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                log.warn("No XML content found for accession number: {}", accessionNumber);
                return List.of();
            }

            // Parse the XML and extract transactions
            List<InsiderTransaction> transactions = form4ParserService.parseForm4Xml(xmlContent, accessionNumber);

            if (transactions.isEmpty()) {
                log.warn("No transactions extracted from Form 4: {}", accessionNumber);
            } else {
                log.info("Successfully processed {} transactions from Form 4: {}",
                        transactions.size(), accessionNumber);
            }

            return transactions;

        } catch (Exception e) {
            Form4DocumentProcessingException processingException =
                    new Form4DocumentProcessingException(accessionNumber, e);
            if (failOnProcessingError) {
                log.error(ERROR_MSG, accessionNumber, processingException);
                throw processingException;
            }

            log.warn("Failed to process Form 4 document {}: {}",
                    accessionNumber,
                    processingException.getMessage());
            log.debug("Processing details", processingException);

            // Return empty list to continue processing other documents
            return List.of();
        }
    }

    private String fetchForm4Document(String accessionNumber) {
        String cik = SecForm4DocumentSupport.extractCikFromAccessionNumber(accessionNumber);
        if (cik == null) {
            log.warn("Could not determine CIK for accession number: {}", accessionNumber);
            return null;
        }

        String filingIndexJson = secApiClient.fetchFiling(cik, accessionNumber, "index.json");
        String primaryDocument = SecForm4DocumentSupport.selectPrimaryXmlDocument(filingIndexJson);
        if (primaryDocument == null || primaryDocument.isBlank()) {
            log.warn("Could not resolve primary Form 4 XML document for accession number: {}", accessionNumber);
            return null;
        }

        return secApiClient.fetchForm4(cik, accessionNumber, primaryDocument);
    }
}
