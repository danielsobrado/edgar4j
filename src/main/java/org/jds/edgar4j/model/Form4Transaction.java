package org.jds.edgar4j.model;

import java.util.Date;

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

    private Date transactionDate;

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

    private boolean equitySwapInvolved;

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

    private Date expirationDate;

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

        return switch (transactionCode.toUpperCase()) {
            case "P" -> "Open Market Purchase";
            case "S" -> "Open Market Sale";
            case "A" -> "Grant/Award (Rule 16b-3)";
            case "D" -> "Disposition to Issuer";
            case "F" -> "Payment via Withholding";
            case "I" -> "Discretionary Transaction";
            case "M" -> "Exercise of Derivative";
            case "C" -> "Conversion of Derivative";
            case "E" -> "Expiration (short position)";
            case "H" -> "Expiration (long position)";
            case "O" -> "Exercise (out-of-money)";
            case "X" -> "Exercise (in-the-money)";
            case "G" -> "Gift";
            case "L" -> "Small Acquisition (Rule 16a-6)";
            case "W" -> "Acquisition/Disposition by Will/Laws of Descent";
            case "Z" -> "Deposit/Withdrawal from Voting Trust";
            case "J" -> "Other";
            case "K" -> "Equity Swap Transaction";
            case "U" -> "Disposition due to Tender of Shares";
            default -> "Code: " + transactionCode;
        };
    }
}
