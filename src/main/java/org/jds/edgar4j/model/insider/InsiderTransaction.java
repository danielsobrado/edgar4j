package org.jds.edgar4j.model.insider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an insider transaction record
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Entity
@Table(name = "insider_transactions", indexes = {
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_filing_date", columnList = "filing_date"),
    @Index(name = "idx_company_id", columnList = "company_id"),
    @Index(name = "idx_insider_id", columnList = "insider_id"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type_id"),
    @Index(name = "idx_accession_number", columnList = "accession_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsiderTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insider_id", nullable = false)
    private Insider insider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_type_id", nullable = false)
    private TransactionType transactionType;

    @Column(name = "accession_number", nullable = false, length = 25)
    private String accessionNumber;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "security_title", nullable = false, length = 200)
    private String securityTitle;

    @Column(name = "transaction_code", nullable = false, length = 2)
    private String transactionCode;

    @Column(name = "shares_transacted", precision = 19, scale = 4)
    private BigDecimal sharesTransacted;

    @Column(name = "price_per_share", precision = 19, scale = 4)
    private BigDecimal pricePerShare;

    @Column(name = "transaction_value", precision = 19, scale = 4)
    private BigDecimal transactionValue;

    @Column(name = "shares_owned_before", precision = 19, scale = 4)
    private BigDecimal sharesOwnedBefore;

    @Column(name = "shares_owned_after", precision = 19, scale = 4)
    private BigDecimal sharesOwnedAfter;

    @Column(name = "ownership_percentage_before", precision = 8, scale = 6)
    private BigDecimal ownershipPercentageBefore;

    @Column(name = "ownership_percentage_after", precision = 8, scale = 6)
    private BigDecimal ownershipPercentageAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquired_disposed", nullable = false)
    private AcquiredDisposed acquiredDisposed;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_nature", nullable = false)
    private OwnershipNature ownershipNature;

    @Column(name = "is_derivative", nullable = false)
    @Builder.Default
    private Boolean isDerivative = false;

    @Column(name = "exercise_date")
    private LocalDate exerciseDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "exercise_price", precision = 19, scale = 4)
    private BigDecimal exercisePrice;

    @Column(name = "underlying_security_title", length = 200)
    private String underlyingSecurityTitle;

    @Column(name = "underlying_shares", precision = 19, scale = 4)
    private BigDecimal underlyingShares;

    @Column(name = "footnotes", length = 1000)
    private String footnotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AcquiredDisposed {
        ACQUIRED("A", "Acquired"),
        DISPOSED("D", "Disposed");

        private final String code;
        private final String description;

        AcquiredDisposed(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static AcquiredDisposed fromCode(String code) {
            for (AcquiredDisposed type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown acquired/disposed code: " + code);
        }
    }

    public enum OwnershipNature {
        DIRECT("D", "Direct"),
        INDIRECT("I", "Indirect");

        private final String code;
        private final String description;

        OwnershipNature(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static OwnershipNature fromCode(String code) {
            for (OwnershipNature type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown ownership nature code: " + code);
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateTransactionValue();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateTransactionValue();
    }

    /**
     * Calculates transaction value from shares and price
     */
    public void calculateTransactionValue() {
        if (sharesTransacted != null && pricePerShare != null) {
            this.transactionValue = sharesTransacted.multiply(pricePerShare)
                .setScale(4, RoundingMode.HALF_UP);
        }
    }

    /**
     * Calculates ownership percentage change
     */
    public BigDecimal getOwnershipPercentageChange() {
        if (ownershipPercentageBefore != null && ownershipPercentageAfter != null) {
            return ownershipPercentageAfter.subtract(ownershipPercentageBefore)
                .setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculates shares change
     */
    public BigDecimal getSharesChange() {
        if (sharesOwnedBefore != null && sharesOwnedAfter != null) {
            return sharesOwnedAfter.subtract(sharesOwnedBefore)
                .setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Determines if this is a significant transaction
     */
    public boolean isSignificantTransaction() {
        if (transactionValue != null && transactionValue.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            return true;
        }
        
        BigDecimal ownershipChange = getOwnershipPercentageChange();
        return ownershipChange.abs().compareTo(BigDecimal.valueOf(1.0)) > 0;
    }

    /**
     * Determines if this is a purchase transaction
     */
    public boolean isPurchase() {
        return AcquiredDisposed.ACQUIRED.equals(acquiredDisposed) && 
               sharesTransacted != null && 
               sharesTransacted.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Determines if this is a sale transaction
     */
    public boolean isSale() {
        return AcquiredDisposed.DISPOSED.equals(acquiredDisposed) && 
               sharesTransacted != null && 
               sharesTransacted.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Validates transaction data consistency
     */
    public boolean isDataConsistent() {
        // Check if shares owned calculation is consistent
        if (sharesOwnedBefore != null && sharesOwnedAfter != null && sharesTransacted != null) {
            BigDecimal expectedShares = sharesOwnedBefore;
            
            if (AcquiredDisposed.ACQUIRED.equals(acquiredDisposed)) {
                expectedShares = expectedShares.add(sharesTransacted);
            } else if (AcquiredDisposed.DISPOSED.equals(acquiredDisposed)) {
                expectedShares = expectedShares.subtract(sharesTransacted);
            }
            
            return expectedShares.compareTo(sharesOwnedAfter) == 0;
        }
        
        return true; // If we don't have enough data, assume it's consistent
    }
}
