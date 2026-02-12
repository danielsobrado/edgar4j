package org.jds.edgar4j.service.insider;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for SEC EDGAR API integration
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface EdgarApiService {

    /**
     * Download and process submissions for a company
     */
    CompletableFuture<Void> processCompanySubmissions(String cik);

    /**
     * Download Form 4 document
     */
    CompletableFuture<String> downloadForm4Document(String cik, String accessionNumber, String primaryDocument);

    /**
     * Get recent Form 4 filings
     */
    CompletableFuture<List<FilingInfo>> getRecentForm4Filings(String cik);

    /**
     * Process bulk submissions file
     */
    CompletableFuture<Void> processBulkSubmissions();

    /**
     * Get company tickers
     */
    CompletableFuture<List<CompanyTicker>> getCompanyTickers();

    /**
     * Get Form 4 document content by accession number
     */
    String getForm4Document(String accessionNumber);

    /**
     * Get Form 4 filings by date range for batch processing
     */
    List<String> getForm4FilingsByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Get Form 4 filings from daily master index
     */
    List<String> getForm4FilingsFromDailyIndex(LocalDate date);

    /**
     * Inner class for filing information
     */
    class FilingInfo {
        private String accessionNumber;
        private String filingDate;
        private String primaryDocument;
        private String documentUrl;
        private String formType;

        public FilingInfo() {}

        public FilingInfo(String accessionNumber, String filingDate, String primaryDocument, 
                         String documentUrl, String formType) {
            this.accessionNumber = accessionNumber;
            this.filingDate = filingDate;
            this.primaryDocument = primaryDocument;
            this.documentUrl = documentUrl;
            this.formType = formType;
        }

        // Getters and setters
        public String getAccessionNumber() { return accessionNumber; }
        public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

        public String getFilingDate() { return filingDate; }
        public void setFilingDate(String filingDate) { this.filingDate = filingDate; }

        public String getPrimaryDocument() { return primaryDocument; }
        public void setPrimaryDocument(String primaryDocument) { this.primaryDocument = primaryDocument; }

        public String getDocumentUrl() { return documentUrl; }
        public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

        public String getFormType() { return formType; }
        public void setFormType(String formType) { this.formType = formType; }
    }

    /**
     * Inner class for company ticker information
     */
    class CompanyTicker {
        private String cik;
        private String ticker;
        private String title;
        private String exchange;

        public CompanyTicker() {}

        public CompanyTicker(String cik, String ticker, String title, String exchange) {
            this.cik = cik;
            this.ticker = ticker;
            this.title = title;
            this.exchange = exchange;
        }

        // Getters and setters
        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
    }
}
