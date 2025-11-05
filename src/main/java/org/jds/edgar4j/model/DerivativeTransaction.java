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
 * Represents a derivative security transaction from SEC Form 4
 * Table II - Derivative Securities Acquired, Disposed of, or Beneficially Owned
 * (e.g., stock options, warrants, convertible securities)
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
public class DerivativeTransaction {

    /** Title of Derivative Security (e.g., "Stock Option", "Restricted Stock Units") */
    private String securityTitle;

    /** Conversion or Exercise Price of Derivative Security */
    private BigDecimal conversionOrExercisePrice;

    /** Transaction Date (MM/DD/YYYY) */
    private LocalDate transactionDate;

    /** Deemed Execution Date, if any */
    private LocalDate deemedExecutionDate;

    /** Transaction Code (e.g., A=Award, M=Exercise, etc.) */
    private String transactionCode;

    /** Transaction Code V flag */
    private String transactionCodeV;

    /** Number of derivative securities acquired (A) */
    private BigDecimal securitiesAcquired;

    /** Number of derivative securities disposed of (D) */
    private BigDecimal securitiesDisposed;

    /** Date Exercisable */
    private LocalDate dateExercisable;

    /** Expiration Date */
    private LocalDate expirationDate;

    /** Title of underlying security (e.g., "Common Stock") */
    private String underlyingSecurityTitle;

    /** Amount or number of shares of underlying security */
    private BigDecimal underlyingSecurityShares;

    /** Price of Derivative Security */
    private BigDecimal derivativeSecurityPrice;

    /** Number of derivative securities owned following the transaction */
    private BigDecimal securitiesOwnedFollowingTransaction;

    /** Direct (D) or Indirect (I) ownership */
    private String directOrIndirectOwnership;

    /** Nature of indirect beneficial ownership (if indirect) */
    private String natureOfOwnership;

    /**
     * Check if this is an acquisition transaction
     * @return true if securities were acquired
     */
    public boolean isAcquisition() {
        return securitiesAcquired != null && securitiesAcquired.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if this is a disposal transaction
     * @return true if securities were disposed
     */
    public boolean isDisposal() {
        return securitiesDisposed != null && securitiesDisposed.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get the net change in derivative securities
     * @return positive for acquisition, negative for disposal
     */
    public BigDecimal getNetSecuritiesChange() {
        BigDecimal acquired = securitiesAcquired != null ? securitiesAcquired : BigDecimal.ZERO;
        BigDecimal disposed = securitiesDisposed != null ? securitiesDisposed : BigDecimal.ZERO;
        return acquired.subtract(disposed);
    }
}
