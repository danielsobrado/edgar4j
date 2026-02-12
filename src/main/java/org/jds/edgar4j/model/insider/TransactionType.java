package org.jds.edgar4j.model.insider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity representing transaction types for insider trading
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Entity
@Table(name = "transaction_types", indexes = {
    @Index(name = "idx_transaction_code", columnList = "transaction_code"),
    @Index(name = "idx_transaction_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TransactionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_code", nullable = false, unique = true, length = 2)
    private String transactionCode;

    @Column(name = "transaction_name", nullable = false, length = 100)
    private String transactionName;

    @Column(name = "transaction_description", length = 500)
    private String transactionDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_category", nullable = false)
    private TransactionCategory transactionCategory;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "transactionType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InsiderTransaction> insiderTransactions;

    public enum TransactionCategory {
        PURCHASE("Purchase/Acquisition"),
        SALE("Sale/Disposition"),
        GRANT("Grant/Award"),
        EXERCISE("Exercise/Conversion"),
        TRANSFER("Transfer/Gift"),
        OTHER("Other");

        private final String description;

        TransactionCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Creates standard SEC transaction types
     */
    public static TransactionType createStandardType(String code, String name, String description, TransactionCategory category) {
        return TransactionType.builder()
            .transactionCode(code)
            .transactionName(name)
            .transactionDescription(description)
            .transactionCategory(category)
            .isActive(true)
            .build();
    }

    /**
     * Standard SEC transaction codes and their mappings
     */
    public static class StandardTypes {
        public static final TransactionType PURCHASE = createStandardType(
            "P", "Purchase", "Open market or private purchase", TransactionCategory.PURCHASE);
        
        public static final TransactionType SALE = createStandardType(
            "S", "Sale", "Open market or private sale", TransactionCategory.SALE);
        
        public static final TransactionType AWARD = createStandardType(
            "A", "Award", "Grant, award or other acquisition", TransactionCategory.GRANT);
        
        public static final TransactionType DISPOSITION = createStandardType(
            "D", "Disposition", "Disposition to the issuer", TransactionCategory.SALE);
        
        public static final TransactionType EXERCISE = createStandardType(
            "M", "Exercise", "Exercise or conversion of derivative security", TransactionCategory.EXERCISE);
        
        public static final TransactionType PAYMENT = createStandardType(
            "F", "Payment", "Payment of exercise price or tax liability", TransactionCategory.PURCHASE);
        
        public static final TransactionType GIFT = createStandardType(
            "G", "Gift", "Bona fide gift", TransactionCategory.TRANSFER);
        
        public static final TransactionType INHERITANCE = createStandardType(
            "V", "Inheritance", "Transaction in equity swap", TransactionCategory.TRANSFER);
        
        public static final TransactionType OPTION_EXERCISE = createStandardType(
            "X", "Option Exercise", "Exercise of in-the-money option", TransactionCategory.EXERCISE);
        
        public static final TransactionType OPTION_EXPIRATION = createStandardType(
            "E", "Option Expiration", "Expiration of short derivative position", TransactionCategory.OTHER);
        
        public static final TransactionType TENDER = createStandardType(
            "T", "Tender", "Tender of shares in a tender offer", TransactionCategory.SALE);
        
        public static final TransactionType MERGER = createStandardType(
            "W", "Merger", "Acquisition or disposition by will or laws of descent", TransactionCategory.OTHER);
        
        public static final TransactionType OTHER = createStandardType(
            "J", "Other", "Other acquisition or disposition", TransactionCategory.OTHER);
    }

    /**
     * Determines if this transaction type represents a purchase
     */
    public boolean isPurchaseType() {
        return TransactionCategory.PURCHASE.equals(transactionCategory) || 
               TransactionCategory.GRANT.equals(transactionCategory);
    }

    /**
     * Determines if this transaction type represents a sale
     */
    public boolean isSaleType() {
        return TransactionCategory.SALE.equals(transactionCategory);
    }

    /**
     * Determines if this transaction type involves derivative securities
     */
    public boolean isDerivativeType() {
        return TransactionCategory.EXERCISE.equals(transactionCategory) ||
               "M".equals(transactionCode) || 
               "X".equals(transactionCode) || 
               "E".equals(transactionCode);
    }
}
