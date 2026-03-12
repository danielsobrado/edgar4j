package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for insider transaction operations
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface InsiderTransactionService {

    /**
     * Save or update an insider transaction
     */
    InsiderTransaction saveTransaction(InsiderTransaction transaction);

    /**
     * Save a single insider transaction
     */
    InsiderTransaction save(InsiderTransaction transaction);

    /**
     * Save multiple insider transactions
     */
    List<InsiderTransaction> saveAll(List<InsiderTransaction> transactions);

    /**
     * Find transaction by accession number
     */
    Optional<InsiderTransaction> findByAccessionNumber(String accessionNumber);

    /**
     * Find transactions by company CIK
     */
    List<InsiderTransaction> findTransactionsByCompany(String companyCik);

    /**
     * Find transactions by company CIK with pagination
     */
    Page<InsiderTransaction> findTransactionsByCompany(String companyCik, Pageable pageable);

    /**
     * Find transactions by insider CIK
     */
    List<InsiderTransaction> findTransactionsByInsider(String insiderCik);

    /**
     * Find transactions by date range
     */
    List<InsiderTransaction> findTransactionsByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Find significant transactions (over specified threshold)
     */
    List<InsiderTransaction> findSignificantTransactions(BigDecimal threshold);

    /**
     * Find recent transactions
     */
    List<InsiderTransaction> findRecentTransactions(LocalDate since);

    /**
     * Process and save Form 4 data
     */
    List<InsiderTransaction> processForm4Data(String xmlContent, String accessionNumber);

    /**
     * Validate transaction data consistency
     */
    boolean validateTransaction(InsiderTransaction transaction);

    /**
     * Calculate ownership percentages
     */
    void calculateOwnershipPercentages(InsiderTransaction transaction);

    /**
     * Check if transaction already exists
     */
    boolean transactionExists(String accessionNumber);

    /**
     * Get transaction statistics for company
     */
    TransactionStatistics getTransactionStatistics(String companyCik);

    /**
     * Inner class for transaction statistics
     */
    class TransactionStatistics {
        private Long totalTransactions;
        private Long purchaseTransactions;
        private Long saleTransactions;
        private BigDecimal totalValue;
        private BigDecimal averageValue;
        private LocalDate lastTransactionDate;

        // Constructors, getters, and setters
        public TransactionStatistics() {}

        public TransactionStatistics(Long totalTransactions, Long purchaseTransactions, 
                                   Long saleTransactions, BigDecimal totalValue, 
                                   BigDecimal averageValue, LocalDate lastTransactionDate) {
            this.totalTransactions = totalTransactions;
            this.purchaseTransactions = purchaseTransactions;
            this.saleTransactions = saleTransactions;
            this.totalValue = totalValue;
            this.averageValue = averageValue;
            this.lastTransactionDate = lastTransactionDate;
        }

        // Getters and setters
        public Long getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }

        public Long getPurchaseTransactions() { return purchaseTransactions; }
        public void setPurchaseTransactions(Long purchaseTransactions) { this.purchaseTransactions = purchaseTransactions; }

        public Long getSaleTransactions() { return saleTransactions; }
        public void setSaleTransactions(Long saleTransactions) { this.saleTransactions = saleTransactions; }

        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

        public BigDecimal getAverageValue() { return averageValue; }
        public void setAverageValue(BigDecimal averageValue) { this.averageValue = averageValue; }

        public LocalDate getLastTransactionDate() { return lastTransactionDate; }
        public void setLastTransactionDate(LocalDate lastTransactionDate) { this.lastTransactionDate = lastTransactionDate; }
    }
}
