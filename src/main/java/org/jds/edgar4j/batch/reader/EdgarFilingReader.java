package org.jds.edgar4j.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spring Batch ItemReader for Edgar Form 4 filings
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class EdgarFilingReader implements ItemReader<String> {

    private final EdgarApiService edgarApiService;
    
    @Value("#{jobParameters['startDate']}")
    private String startDate;
    
    @Value("#{jobParameters['endDate']}")
    private String endDate;
    
    @Value("#{jobParameters['formType'] ?: 'FORM4'}")
    private String formType;
    
    private Iterator<String> accessionNumberIterator;
    private boolean initialized = false;

    @Override
    public String read() throws Exception {
        if (!initialized) {
            initialize();
            initialized = true;
        }
        
        if (accessionNumberIterator != null && accessionNumberIterator.hasNext()) {
            String accessionNumber = accessionNumberIterator.next();
            log.debug("Reading accession number: {}", accessionNumber);
            return accessionNumber;
        }
        
        return null; // Indicates end of data
    }

    private void initialize() {
        try {
            log.info("Initializing EdgarFilingReader with parameters: startDate={}, endDate={}, formType={}", 
                    startDate, endDate, formType);
            
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            
            List<String> accessionNumbers = fetchAccessionNumbers(start, end);
            this.accessionNumberIterator = accessionNumbers.iterator();
            
            log.info("Initialized reader with {} accession numbers", accessionNumbers.size());
            
        } catch (Exception e) {
            log.error("Failed to initialize EdgarFilingReader: {}", e.getMessage(), e);
            this.accessionNumberIterator = new ArrayList<String>().iterator();
        }
    }

    private List<String> fetchAccessionNumbers(LocalDate startDate, LocalDate endDate) {
        List<String> accessionNumbers = new ArrayList<>();
        
        try {
            log.info("Fetching Form 4 accession numbers from {} to {}", startDate, endDate);
            
            // Use the EdgarApiService to get filings by date range
            accessionNumbers = edgarApiService.getForm4FilingsByDateRange(startDate, endDate);
            
            log.info("Successfully fetched {} accession numbers for date range", accessionNumbers.size());
            
        } catch (Exception e) {
            log.error("Error fetching accession numbers for date range {} to {}: {}", 
                    startDate, endDate, e.getMessage(), e);
        }
        
        return accessionNumbers;
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return LocalDate.now();
        }
        
        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            log.warn("Failed to parse date: {}, using current date", dateString);
            return LocalDate.now();
        }
    }
}