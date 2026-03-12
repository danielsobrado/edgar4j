package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Company;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for company operations
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface CompanyService {

    /**
     * Save or update a company
     */
    Company saveCompany(Company company);

    /**
     * Find company by CIK
     */
    Optional<Company> findByCik(String cik);

    /**
     * Find company by ticker symbol
     */
    Optional<Company> findByTickerSymbol(String tickerSymbol);

    /**
     * Find companies by name
     */
    List<Company> findByCompanyName(String name);

    /**
     * Find all active companies
     */
    List<Company> findActiveCompanies();

    /**
     * Create company from SEC data
     */
    Company createFromSecData(String cik, String companyName, String tickerSymbol);

    /**
     * Update company information
     */
    Company updateCompanyInfo(String cik, String companyName, String tickerSymbol, String exchange);

    /**
     * Check if company exists
     */
    boolean companyExists(String cik);

    /**
     * Get or create company
     */
    Company getOrCreateCompany(String cik, String companyName, String tickerSymbol);

    /**
     * Enrich company data with market information
     */
    Company enrichCompanyData(Company company);

    /**
     * Calculate and update market cap
     */
    void updateMarketCap(String cik);

    /**
     * Get company statistics
     */
    CompanyStatistics getCompanyStatistics(String cik);

    /**
     * Inner class for company statistics
     */
    class CompanyStatistics {
        private Long totalInsiders;
        private Long totalTransactions;
        private Long activeRelationships;
        private String lastFilingDate;

        public CompanyStatistics() {}

        public CompanyStatistics(Long totalInsiders, Long totalTransactions, 
                               Long activeRelationships, String lastFilingDate) {
            this.totalInsiders = totalInsiders;
            this.totalTransactions = totalTransactions;
            this.activeRelationships = activeRelationships;
            this.lastFilingDate = lastFilingDate;
        }

        // Getters and setters
        public Long getTotalInsiders() { return totalInsiders; }
        public void setTotalInsiders(Long totalInsiders) { this.totalInsiders = totalInsiders; }

        public Long getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }

        public Long getActiveRelationships() { return activeRelationships; }
        public void setActiveRelationships(Long activeRelationships) { this.activeRelationships = activeRelationships; }

        public String getLastFilingDate() { return lastFilingDate; }
        public void setLastFilingDate(String lastFilingDate) { this.lastFilingDate = lastFilingDate; }
    }
}
