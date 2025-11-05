package org.jds.edgar4j.model.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an individual insider buy transaction for reporting purposes
 * Flattened view derived from Form 4 data for "Latest Insider Buys" report
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
public class InsiderBuy {

    /** SEC Form 4 accession number (unique filing identifier) */
    private String accessionNumber;

    /** Date and time the Form 4 was filed with SEC */
    private LocalDateTime filingDate;

    /** Transaction date (actual buy date) */
    private LocalDate tradeDate;

    /** Stock ticker symbol */
    private String ticker;

    /** Company name */
    private String companyName;

    /** Company CIK */
    private String companyCik;

    /** Insider's full name */
    private String insiderName;

    /** Insider's CIK */
    private String insiderCik;

    /** Insider's title/role (e.g., "CEO", "Director", "10%") */
    private String insiderTitle;

    /** Transaction type code (e.g., "P - Purchase", "A - Award") */
    private String tradeType;

    /** Price per share */
    private BigDecimal pricePerShare;

    /** Number of shares purchased */
    private BigDecimal quantity;

    /** Total shares owned after this transaction */
    private BigDecimal sharesOwnedAfter;

    /** Shares owned before this transaction (calculated) */
    private BigDecimal sharesOwnedBefore;

    /** Ownership change percentage */
    private BigDecimal ownershipChangePercent;

    /** Total transaction value (quantity * price per share) */
    private BigDecimal transactionValue;

    /** Direct (D) or Indirect (I) ownership */
    private String ownershipType;

    /** Security title (usually "Common Stock") */
    private String securityTitle;

    // Performance metrics (to be populated by stock price service)
    /** 1-day price change percentage */
    private BigDecimal oneDayChange;

    /** 1-week price change percentage */
    private BigDecimal oneWeekChange;

    /** 1-month price change percentage */
    private BigDecimal oneMonthChange;

    /** 6-month price change percentage */
    private BigDecimal sixMonthChange;

    /**
     * Calculate ownership change percentage
     * Formula: ((sharesOwnedAfter - sharesOwnedBefore) / sharesOwnedBefore) * 100
     */
    public void calculateOwnershipChange() {
        if (sharesOwnedBefore != null && sharesOwnedBefore.compareTo(BigDecimal.ZERO) > 0 &&
            sharesOwnedAfter != null) {

            BigDecimal change = sharesOwnedAfter.subtract(sharesOwnedBefore);
            this.ownershipChangePercent = change
                .divide(sharesOwnedBefore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        } else if (sharesOwnedBefore == null || sharesOwnedBefore.compareTo(BigDecimal.ZERO) == 0) {
            // New position
            this.ownershipChangePercent = BigDecimal.valueOf(100);
        }
    }

    /**
     * Calculate shares owned before the transaction
     */
    public void calculateSharesOwnedBefore() {
        if (sharesOwnedAfter != null && quantity != null) {
            this.sharesOwnedBefore = sharesOwnedAfter.subtract(quantity);
            if (sharesOwnedBefore.compareTo(BigDecimal.ZERO) < 0) {
                sharesOwnedBefore = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Calculate transaction value
     */
    public void calculateTransactionValue() {
        if (quantity != null && pricePerShare != null) {
            this.transactionValue = quantity.multiply(pricePerShare).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Get formatted trade type for display
     * @return formatted trade type (e.g., "P - Purchase")
     */
    public String getFormattedTradeType() {
        if (tradeType == null || tradeType.isEmpty()) {
            return "P - Purchase";
        }

        // If already formatted, return as-is
        if (tradeType.contains("-")) {
            return tradeType;
        }

        // Format single letter codes
        switch (tradeType.toUpperCase()) {
            case "P":
                return "P - Purchase";
            case "S":
                return "S - Sale";
            case "A":
                return "A - Award";
            case "M":
                return "M - Exercise";
            case "G":
                return "G - Gift";
            case "D":
                return "D - Disposition";
            default:
                return tradeType;
        }
    }

    /**
     * Get formatted ownership change with sign
     * @return formatted ownership change (e.g., "+25.5%", "+100%")
     */
    public String getFormattedOwnershipChange() {
        if (ownershipChangePercent == null) {
            return "";
        }

        String sign = ownershipChangePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + ownershipChangePercent.toString() + "%";
    }

    /**
     * Check if this is a direct ownership transaction
     * @return true if ownership type is Direct (D)
     */
    public boolean isDirectOwnership() {
        return "D".equalsIgnoreCase(ownershipType);
    }

    /**
     * Check if this is an indirect ownership transaction
     * @return true if ownership type is Indirect (I)
     */
    public boolean isIndirectOwnership() {
        return "I".equalsIgnoreCase(ownershipType);
    }
}
