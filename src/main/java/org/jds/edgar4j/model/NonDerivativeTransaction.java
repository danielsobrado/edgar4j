package org.jds.edgar4j.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a non-derivative security transaction from SEC Form 4
 * Table I - Non-Derivative Securities Acquired, Disposed of, or Beneficially Owned
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NonDerivativeTransaction {

    /** Title of Security (e.g., "Common Stock", "Preferred Stock") */
    private String securityTitle;

    /** Transaction Date (MM/DD/YYYY) */
    private LocalDate transactionDate;

    /** Deemed Execution Date, if any */
    private LocalDate deemedExecutionDate;

    /** Transaction Code (e.g., P=Purchase, S=Sale, A=Award, etc.) */
    private String transactionCode;

    /** Transaction Code V flag */
    private String transactionCodeV;

    /** Number of shares acquired (A) or disposed of (D) */
    private BigDecimal transactionShares;

    /** Acquired (A) or Disposed (D) indicator */
    private String acquiredDisposedCode;

    /** Price per share of the transaction */
    private BigDecimal transactionPricePerShare;

    /** Total shares owned after this transaction */
    private BigDecimal sharesOwnedFollowingTransaction;

    /** Direct (D) or Indirect (I) ownership */
    private String directOrIndirectOwnership;

    /** Nature of indirect beneficial ownership (if indirect) */
    private String natureOfOwnership;

    /**
     * Calculate the total transaction value
     * @return transaction value (shares * price per share)
     */
    public BigDecimal getTransactionValue() {
        if (transactionShares != null && transactionPricePerShare != null) {
            return transactionShares.multiply(transactionPricePerShare);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if this is a purchase transaction
     * @return true if transaction code indicates a purchase
     */
    public boolean isPurchase() {
        return "P".equalsIgnoreCase(transactionCode) ||
               "A".equalsIgnoreCase(acquiredDisposedCode);
    }

    /**
     * Check if this is a sale/disposal transaction
     * @return true if transaction code indicates a sale
     */
    public boolean isSale() {
        return "S".equalsIgnoreCase(transactionCode) ||
               "D".equalsIgnoreCase(acquiredDisposedCode);
    }
}
