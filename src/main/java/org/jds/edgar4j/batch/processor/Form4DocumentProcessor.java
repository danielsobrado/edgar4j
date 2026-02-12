package org.jds.edgar4j.batch.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.springframework.batch.item.ItemProcessor;
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

    private final EdgarApiService edgarApiService;
    private final Form4ParserService form4ParserService;

    @Override
    public List<InsiderTransaction> process(String accessionNumber) throws Exception {
        log.debug("Processing Form 4 document: {}", accessionNumber);
        
        try {
            // Fetch the XML content from EDGAR API
            String xmlContent = edgarApiService.getForm4Document(accessionNumber);
            
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
            log.error("Error processing Form 4 document {}: {}", accessionNumber, e.getMessage(), e);
            
            // Return empty list to continue processing other documents
            return List.of();
        }
    }
}