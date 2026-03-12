package org.jds.edgar4j.model;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual transaction from a Form 4 filing.
 * Can be either a non-derivative (Table I) or derivative (Table II) transaction.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Form4Transaction {

    private String accessionNumber;

    /**
     * Transaction type: NON_DERIVATIVE or DERIVATIVE
     */
    private String transactionType;

    private String securityTitle;

    private LocalDate transactionDate;

    /**
     * Transaction code per SEC spec.
     * Common codes:
     * P - Open market purchase
     * S - Open market sale
     * A - Grant/award (Rule 16b-3)
     * M - Exercise of derivative
     * F - Payment of exercise price via withholding
     * G - Gift
     * J - Other (with description)
     */
    private String transactionCode;

    private String transactionFormType;

    private Boolean equitySwapInvolved;

    private Float transactionShares;

    private Float transactionPricePerShare;

    private Float transactionValue;

    /**
     * A = Acquired, D = Disposed
     */
    private String acquiredDisposedCode;

    private Float sharesOwnedFollowingTransaction;

    /**
     * D = Direct, I = Indirect
     */
    private String directOrIndirectOwnership;

    private String natureOfOwnership;

    // Derivative-specific fields
    private Float exercisePrice;

    private LocalDate expirationDate;

    private String underlyingSecurityTitle;

    private Float underlyingSecurityShares;

    /**
     * Determines if this is a non-derivative (Table I) transaction.
     */
    public boolean isNonDerivative() {
        return "NON_DERIVATIVE".equals(transactionType);
    }

    /**
     * Determines if this is a derivative (Table II) transaction.
     */
    public boolean isDerivative() {
        return "DERIVATIVE".equals(transactionType);
    }

    /**
     * Determines if transaction was a purchase.
     */
    public boolean isBuy() {
        return "A".equalsIgnoreCase(acquiredDisposedCode);
    }

    /**
     * Determines if transaction was a sale/disposal.
     */
    public boolean isSell() {
        return "D".equalsIgnoreCase(acquiredDisposedCode);
    }

    /**
     * Gets human-readable description of transaction code.
     */
    public String getTransactionCodeDescription() {
        if (transactionCode == null) return "Unknown";

        String code = transactionCode.toUpperCase();
        if (code.equals("P")) return "Open Market Purchase";
        if (code.equals("S")) return "Open Market Sale";
        if (code.equals("A")) return "Grant/Award (Rule 16b-3)";
        if (code.equals("D")) return "Disposition to Issuer";
        if (code.equals("F")) return "Payment via Withholding";
        if (code.equals("I")) return "Discretionary Transaction";
        if (code.equals("M")) return "Exercise of Derivative";
        if (code.equals("C")) return "Conversion of Derivative";
        if (code.equals("E")) return "Expiration (short position)";
        if (code.equals("H")) return "Expiration (long position)";
        if (code.equals("O")) return "Exercise (out-of-money)";
        if (code.equals("X")) return "Exercise (in-the-money)";
        if (code.equals("G")) return "Gift";
        if (code.equals("L")) return "Small Acquisition (Rule 16a-6)";
        if (code.equals("W")) return "Acquisition/Disposition by Will/Laws of Descent";
        if (code.equals("Z")) return "Deposit/Withdrawal from Voting Trust";
        if (code.equals("J")) return "Other";
        if (code.equals("K")) return "Equity Swap Transaction";
        if (code.equals("U")) return "Disposition due to Tender of Shares";
        return "Code: " + transactionCode;
    }
}
