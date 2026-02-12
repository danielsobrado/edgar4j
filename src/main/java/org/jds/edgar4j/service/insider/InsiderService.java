package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Insider;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for insider operations
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface InsiderService {

    /**
     * Save or update an insider
     */
    Insider saveInsider(Insider insider);

    /**
     * Find insider by CIK
     */
    Optional<Insider> findByCik(String cik);

    /**
     * Find insiders by name
     */
    List<Insider> findByName(String name);

    /**
     * Find all active insiders
     */
    List<Insider> findActiveInsiders();

    /**
     * Create insider from SEC data
     */
    Insider createFromSecData(String cik, String fullName, String address);

    /**
     * Update insider information
     */
    Insider updateInsiderInfo(String cik, String fullName, String address);

    /**
     * Check if insider exists
     */
    boolean insiderExists(String cik);

    /**
     * Get or create insider
     */
    Insider getOrCreateInsider(String cik, String fullName, String address);

    /**
     * Parse name components
     */
    void parseNameComponents(Insider insider);

    /**
     * Parse address components
     */
    void parseAddressComponents(Insider insider, String address);

    /**
     * Find insiders with transactions for company
     */
    List<Insider> findInsidersForCompany(String companyCik);

    /**
     * Get insider statistics
     */
    InsiderStatistics getInsiderStatistics(String cik);

    /**
     * Inner class for insider statistics
     */
    class InsiderStatistics {
        private Long totalTransactions;
        private Long totalCompanies;
        private String lastTransactionDate;
        private Long activeRelationships;

        public InsiderStatistics() {}

        public InsiderStatistics(Long totalTransactions, Long totalCompanies, 
                               String lastTransactionDate, Long activeRelationships) {
            this.totalTransactions = totalTransactions;
            this.totalCompanies = totalCompanies;
            this.lastTransactionDate = lastTransactionDate;
            this.activeRelationships = activeRelationships;
        }

        // Getters and setters
        public Long getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }

        public Long getTotalCompanies() { return totalCompanies; }
        public void setTotalCompanies(Long totalCompanies) { this.totalCompanies = totalCompanies; }

        public String getLastTransactionDate() { return lastTransactionDate; }
        public void setLastTransactionDate(String lastTransactionDate) { this.lastTransactionDate = lastTransactionDate; }

        public Long getActiveRelationships() { return activeRelationships; }
        public void setActiveRelationships(Long activeRelationships) { this.activeRelationships = activeRelationships; }
    }
}
