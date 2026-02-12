package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.service.insider.InsiderTransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of InsiderTransactionService for managing insider transaction data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InsiderTransactionServiceImpl implements InsiderTransactionService {

    private final InsiderTransactionRepository transactionRepository;

    @Override
    public InsiderTransaction saveTransaction(InsiderTransaction transaction) {
        log.debug("Saving insider transaction: {}", transaction.getAccessionNumber());
        
        // Validate transaction before saving
        if (!validateTransaction(transaction)) {
            log.warn("Transaction validation failed for: {}", transaction.getAccessionNumber());
            throw new IllegalArgumentException("Transaction validation failed");
        }
        
        // Calculate derived values
        calculateOwnershipPercentages(transaction);
        transaction.calculateTransactionValue();
        
        return transactionRepository.save(transaction);
    }

    @Override
    public InsiderTransaction save(InsiderTransaction transaction) {
        return saveTransaction(transaction);
    }

    @Override
    public List<InsiderTransaction> saveAll(List<InsiderTransaction> transactions) {
        log.info("Saving {} insider transactions", transactions.size());
        
        List<InsiderTransaction> savedTransactions = new ArrayList<>();
        
        for (InsiderTransaction transaction : transactions) {
            try {
                InsiderTransaction saved = saveTransaction(transaction);
                savedTransactions.add(saved);
                
            } catch (Exception e) {
                log.warn("Failed to save transaction {}: {}", 
                        transaction.getAccessionNumber(), e.getMessage());
                // Continue with other transactions
            }
        }
        
        log.info("Successfully saved {} out of {} transactions", 
                savedTransactions.size(), transactions.size());
        
        return savedTransactions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InsiderTransaction> findByAccessionNumber(String accessionNumber) {
        log.debug("Finding transaction by accession number: {}", accessionNumber);
        return transactionRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsiderTransaction> findTransactionsByCompany(String companyCik) {
        log.debug("Finding transactions for company CIK: {}", companyCik);
        return transactionRepository.findByCompanyCik(companyCik);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InsiderTransaction> findTransactionsByCompany(String companyCik, Pageable pageable) {
        log.debug("Finding transactions for company CIK: {} with pagination", companyCik);
        return transactionRepository.findByCompanyCik(companyCik, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsiderTransaction> findTransactionsByInsider(String insiderCik) {
        log.debug("Finding transactions for insider CIK: {}", insiderCik);
        return transactionRepository.findByInsiderCik(insiderCik);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsiderTransaction> findTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Finding transactions between {} and {}", startDate, endDate);
        return transactionRepository.findByTransactionDateBetween(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsiderTransaction> findSignificantTransactions(BigDecimal threshold) {
        log.debug("Finding significant transactions over: {}", threshold);
        return transactionRepository.findSignificantTransactions(threshold);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsiderTransaction> findRecentTransactions(LocalDate since) {
        log.debug("Finding recent transactions since: {}", since);
        return transactionRepository.findRecentTransactions(since);
    }

    @Override
    public List<InsiderTransaction> processForm4Data(String xmlContent, String accessionNumber) {
        log.info("Processing Form 4 data for accession number: {}", accessionNumber);
        
        // TODO: Implement Form 4 XML parsing
        // This would involve:
        // 1. Parse XML content to extract transaction data
        // 2. Create InsiderTransaction objects
        // 3. Validate and save transactions
        // 4. Return list of saved transactions
        
        log.warn("Form 4 processing not yet implemented");
        return List.of();
    }

    @Override
    public boolean validateTransaction(InsiderTransaction transaction) {
        log.debug("Validating transaction: {}", transaction.getAccessionNumber());
        
        if (transaction == null) {
            log.warn("Transaction is null");
            return false;
        }
        
        // Validate required fields
        if (transaction.getCompany() == null) {
            log.warn("Transaction missing company");
            return false;
        }
        
        if (transaction.getInsider() == null) {
            log.warn("Transaction missing insider");
            return false;
        }
        
        if (transaction.getAccessionNumber() == null || transaction.getAccessionNumber().isEmpty()) {
            log.warn("Transaction missing accession number");
            return false;
        }
        
        if (transaction.getTransactionDate() == null) {
            log.warn("Transaction missing transaction date");
            return false;
        }
        
        if (transaction.getFilingDate() == null) {
            log.warn("Transaction missing filing date");
            return false;
        }
        
        // Validate date logic
        if (transaction.getTransactionDate().isAfter(transaction.getFilingDate())) {
            log.warn("Transaction date {} is after filing date {}", 
                    transaction.getTransactionDate(), transaction.getFilingDate());
            return false;
        }
        
        // Validate numeric fields
        if (transaction.getSharesTransacted() != null && 
            transaction.getSharesTransacted().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Shares transacted cannot be negative: {}", transaction.getSharesTransacted());
            return false;
        }
        
        if (transaction.getPricePerShare() != null && 
            transaction.getPricePerShare().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Price per share cannot be negative: {}", transaction.getPricePerShare());
            return false;
        }
        
        // Validate shares owned calculation if all values are present
        if (!transaction.isDataConsistent()) {
            log.warn("Transaction data is inconsistent for: {}", transaction.getAccessionNumber());
            return false;
        }
        
        return true;
    }

    @Override
    public void calculateOwnershipPercentages(InsiderTransaction transaction) {
        log.debug("Calculating ownership percentages for transaction: {}", transaction.getAccessionNumber());
        
        // If company total shares is available, calculate ownership percentages
        if (transaction.getCompany() != null && 
            transaction.getCompany().getTotalSharesOutstanding() != null &&
            transaction.getCompany().getTotalSharesOutstanding().compareTo(BigDecimal.ZERO) > 0) {
            
            BigDecimal totalShares = transaction.getCompany().getTotalSharesOutstanding();
            
            if (transaction.getSharesOwnedBefore() != null) {
                BigDecimal percentageBefore = transaction.getSharesOwnedBefore()
                    .divide(totalShares, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                transaction.setOwnershipPercentageBefore(percentageBefore);
            }
            
            if (transaction.getSharesOwnedAfter() != null) {
                BigDecimal percentageAfter = transaction.getSharesOwnedAfter()
                    .divide(totalShares, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                transaction.setOwnershipPercentageAfter(percentageAfter);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean transactionExists(String accessionNumber) {
        log.debug("Checking if transaction exists: {}", accessionNumber);
        return transactionRepository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionStatistics getTransactionStatistics(String companyCik) {
        log.debug("Getting transaction statistics for company CIK: {}", companyCik);
        
        List<InsiderTransaction> transactions = transactionRepository.findByCompanyCik(companyCik);
        
        if (transactions.isEmpty()) {
            return new TransactionStatistics(0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        
        long totalTransactions = transactions.size();
        long purchaseTransactions = transactions.stream()
            .mapToLong(t -> t.isPurchase() ? 1 : 0)
            .sum();
        long saleTransactions = transactions.stream()
            .mapToLong(t -> t.isSale() ? 1 : 0)
            .sum();
        
        BigDecimal totalValue = transactions.stream()
            .filter(t -> t.getTransactionValue() != null)
            .map(InsiderTransaction::getTransactionValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageValue = totalTransactions > 0 
            ? totalValue.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        LocalDate lastTransactionDate = transactions.stream()
            .map(InsiderTransaction::getTransactionDate)
            .max(LocalDate::compareTo)
            .orElse(null);
        
        return new TransactionStatistics(
            totalTransactions, 
            purchaseTransactions, 
            saleTransactions, 
            totalValue, 
            averageValue, 
            lastTransactionDate
        );
    }
}
