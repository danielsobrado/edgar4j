package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.repository.insider.TransactionTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Service to initialize standard SEC transaction types
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionTypeInitService implements CommandLineRunner {

    private final TransactionTypeRepository transactionTypeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing standard SEC transaction types");
        
        if (transactionTypeRepository.count() == 0) {
            initializeStandardTransactionTypes();
            log.info("Standard transaction types initialized successfully");
        } else {
            log.info("Transaction types already exist, skipping initialization");
        }
    }

    /**
     * Initialize all standard SEC transaction types
     */
    private void initializeStandardTransactionTypes() {
        List<TransactionType> standardTypes = Arrays.asList(
            createTransactionType("P", "Purchase", 
                "Open market or private purchase", 
                TransactionType.TransactionCategory.PURCHASE, 1),
            
            createTransactionType("S", "Sale", 
                "Open market or private sale", 
                TransactionType.TransactionCategory.SALE, 2),
            
            createTransactionType("A", "Award", 
                "Grant, award or other acquisition", 
                TransactionType.TransactionCategory.GRANT, 3),
            
            createTransactionType("D", "Disposition", 
                "Disposition to the issuer", 
                TransactionType.TransactionCategory.SALE, 4),
            
            createTransactionType("M", "Exercise", 
                "Exercise or conversion of derivative security", 
                TransactionType.TransactionCategory.EXERCISE, 5),
            
            createTransactionType("F", "Payment", 
                "Payment of exercise price or tax liability", 
                TransactionType.TransactionCategory.PURCHASE, 6),
            
            createTransactionType("G", "Gift", 
                "Bona fide gift", 
                TransactionType.TransactionCategory.TRANSFER, 7),
            
            createTransactionType("V", "Inheritance", 
                "Transaction in equity swap", 
                TransactionType.TransactionCategory.TRANSFER, 8),
            
            createTransactionType("X", "Option Exercise", 
                "Exercise of in-the-money option", 
                TransactionType.TransactionCategory.EXERCISE, 9),
            
            createTransactionType("E", "Option Expiration", 
                "Expiration of short derivative position", 
                TransactionType.TransactionCategory.OTHER, 10),
            
            createTransactionType("T", "Tender", 
                "Tender of shares in a tender offer", 
                TransactionType.TransactionCategory.SALE, 11),
            
            createTransactionType("W", "Merger", 
                "Acquisition or disposition by will or laws of descent", 
                TransactionType.TransactionCategory.OTHER, 12),
            
            createTransactionType("J", "Other", 
                "Other acquisition or disposition", 
                TransactionType.TransactionCategory.OTHER, 13),
            
            createTransactionType("H", "Expiration", 
                "Expiration of long derivative position", 
                TransactionType.TransactionCategory.OTHER, 14),
            
            createTransactionType("I", "Discretionary", 
                "Discretionary transaction", 
                TransactionType.TransactionCategory.OTHER, 15),
            
            createTransactionType("K", "Equity Swap", 
                "Equity swap transaction", 
                TransactionType.TransactionCategory.OTHER, 16),
            
            createTransactionType("L", "Small Acquisition", 
                "Small acquisition under Rule 16a-6", 
                TransactionType.TransactionCategory.PURCHASE, 17),
            
            createTransactionType("O", "Exercise", 
                "Exercise of out-of-the-money option", 
                TransactionType.TransactionCategory.EXERCISE, 18),
            
            createTransactionType("U", "Disposition", 
                "Disposition pursuant to tender offer", 
                TransactionType.TransactionCategory.SALE, 19),
            
            createTransactionType("Z", "Deposit", 
                "Deposit into or withdrawal from voting trust", 
                TransactionType.TransactionCategory.TRANSFER, 20)
        );

        transactionTypeRepository.saveAll(standardTypes);
        log.info("Saved {} standard transaction types", standardTypes.size());
    }

    /**
     * Create a transaction type with specified parameters
     */
    private TransactionType createTransactionType(String code, String name, String description, 
                                                TransactionType.TransactionCategory category, 
                                                Integer sortOrder) {
        return TransactionType.builder()
            .transactionCode(code)
            .transactionName(name)
            .transactionDescription(description)
            .transactionCategory(category)
            .sortOrder(sortOrder)
            .isActive(true)
            .build();
    }
}
