package org.jds.edgar4j.model.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a cluster of insider buy transactions
 * Groups multiple insiders buying the same stock on the same day
 * Used for "Latest Cluster Buys" report
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
public class ClusterBuy {

    /** Most recent filing date in the cluster */
    private LocalDateTime filingDate;

    /** Transaction date (all buys in cluster happened on this date) */
    private LocalDate tradeDate;

    /** Stock ticker symbol */
    private String ticker;

    /** Company name */
    private String companyName;

    /** Company CIK */
    private String companyCik;

    /** Industry classification (e.g., "State Commercial Banks") */
    private String industry;

    /** Number of distinct insiders who bought on this date */
    private Integer insiderCount;

    /** Transaction type (usually "P - Purchase") */
    private String tradeType;

    /** Average price per share across all transactions */
    private BigDecimal averagePrice;

    /** Total quantity of shares purchased by all insiders */
    private BigDecimal totalQuantity;

    /** Total shares owned by all insiders after transactions */
    private BigDecimal totalSharesOwned;

    /** Average ownership change percentage across all insiders */
    private BigDecimal averageOwnershipChange;

    /** Total transaction value (sum of all insider buys) */
    private BigDecimal totalValue;

    /** List of individual insider buys in this cluster */
    @Builder.Default
    private List<InsiderBuy> insiderBuys = new ArrayList<>();

    /** Insider titles/roles in this cluster (for display) */
    private String insiderRoles;

    /** Indicates if this cluster includes director buys */
    private boolean hasDirectorBuys;

    /** Indicates if this cluster includes officer buys */
    private boolean hasOfficerBuys;

    /** Indicates if this cluster includes 10% owner buys */
    private boolean hasTenPercentOwnerBuys;

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
     * Aggregate data from individual insider buys
     * Call this after adding all InsiderBuy objects to the list
     */
    public void aggregateFromInsiderBuys() {
        if (insiderBuys == null || insiderBuys.isEmpty()) {
            return;
        }

        // Count distinct insiders
        Set<String> uniqueInsiders = insiderBuys.stream()
            .map(InsiderBuy::getInsiderCik)
            .collect(Collectors.toSet());
        this.insiderCount = uniqueInsiders.size();

        // Calculate total quantity
        this.totalQuantity = insiderBuys.stream()
            .map(InsiderBuy::getQuantity)
            .filter(q -> q != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total value
        this.totalValue = insiderBuys.stream()
            .map(InsiderBuy::getTransactionValue)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        // Calculate total shares owned
        this.totalSharesOwned = insiderBuys.stream()
            .map(InsiderBuy::getSharesOwnedAfter)
            .filter(s -> s != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate average price
        List<BigDecimal> prices = insiderBuys.stream()
            .map(InsiderBuy::getPricePerShare)
            .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
            .collect(Collectors.toList());

        if (!prices.isEmpty()) {
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            this.averagePrice = sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        }

        // Calculate average ownership change
        List<BigDecimal> ownershipChanges = insiderBuys.stream()
            .map(InsiderBuy::getOwnershipChangePercent)
            .filter(c -> c != null)
            .collect(Collectors.toList());

        if (!ownershipChanges.isEmpty()) {
            BigDecimal sum = ownershipChanges.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            this.averageOwnershipChange = sum.divide(BigDecimal.valueOf(ownershipChanges.size()), 2, RoundingMode.HALF_UP);
        }

        // Determine insider roles
        Set<String> roles = new HashSet<>();
        for (InsiderBuy buy : insiderBuys) {
            String title = buy.getInsiderTitle();
            if (title != null && !title.isEmpty()) {
                if (title.toLowerCase().contains("director") || title.equals("Dir")) {
                    roles.add("D");
                    this.hasDirectorBuys = true;
                }
                if (title.toLowerCase().contains("officer") ||
                    title.toLowerCase().contains("ceo") ||
                    title.toLowerCase().contains("cfo") ||
                    title.toLowerCase().contains("president")) {
                    roles.add("O");
                    this.hasOfficerBuys = true;
                }
                if (title.contains("10%")) {
                    roles.add("10%");
                    this.hasTenPercentOwnerBuys = true;
                }
            }
        }

        // Format insider roles for display (e.g., "D,O" or "10%" or "D,O,10%")
        this.insiderRoles = roles.isEmpty() ? "" : String.join(",", roles);

        // Set most recent filing date
        this.filingDate = insiderBuys.stream()
            .map(InsiderBuy::getFilingDate)
            .filter(d -> d != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        // Set trade date (should be same for all in cluster)
        this.tradeDate = insiderBuys.get(0).getTradeDate();

        // Set ticker and company info (should be same for all in cluster)
        InsiderBuy first = insiderBuys.get(0);
        this.ticker = first.getTicker();
        this.companyName = first.getCompanyName();
        this.companyCik = first.getCompanyCik();
        this.tradeType = first.getFormattedTradeType();
    }

    /**
     * Get formatted insider roles for display
     * Shows role abbreviations (e.g., "D" = Director, "M" = Multiple roles)
     * @return formatted roles string
     */
    public String getFormattedInsiderRoles() {
        if (insiderRoles == null || insiderRoles.isEmpty()) {
            return "";
        }

        // If multiple roles, could show "M" for Multiple or the full list
        String[] roleArray = insiderRoles.split(",");
        if (roleArray.length > 2) {
            return "M";  // Multiple roles
        }

        return insiderRoles;
    }

    /**
     * Get a summary of insiders for tooltip/detail view
     * @return formatted string like "3 insiders: John Doe (CEO), Jane Smith (Dir), ..."
     */
    public String getInsiderSummary() {
        if (insiderBuys == null || insiderBuys.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(insiderCount).append(" insider");
        if (insiderCount > 1) {
            summary.append("s");
        }
        summary.append(": ");

        String insiders = insiderBuys.stream()
            .limit(3)  // Show first 3
            .map(buy -> buy.getInsiderName() + " (" + buy.getInsiderTitle() + ")")
            .collect(Collectors.joining(", "));

        summary.append(insiders);

        if (insiderBuys.size() > 3) {
            summary.append(", and ").append(insiderBuys.size() - 3).append(" more");
        }

        return summary.toString();
    }

    /**
     * Get formatted average ownership change with sign
     * @return formatted ownership change (e.g., "+15.2%")
     */
    public String getFormattedOwnershipChange() {
        if (averageOwnershipChange == null) {
            return "";
        }

        String sign = averageOwnershipChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + averageOwnershipChange.toString() + "%";
    }

    /**
     * Determine cluster significance score
     * Higher score = more significant (more insiders, higher values, director/officer involvement)
     * @return significance score (0-100)
     */
    public int getSignificanceScore() {
        int score = 0;

        // Insider count (0-30 points)
        if (insiderCount != null) {
            score += Math.min(insiderCount * 10, 30);
        }

        // Total value (0-30 points, logarithmic scale)
        if (totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0) {
            double valueMillions = totalValue.doubleValue() / 1_000_000.0;
            if (valueMillions > 10) score += 30;
            else if (valueMillions > 5) score += 25;
            else if (valueMillions > 1) score += 20;
            else if (valueMillions > 0.5) score += 15;
            else score += 10;
        }

        // Insider types (0-40 points)
        if (hasDirectorBuys) score += 15;
        if (hasOfficerBuys) score += 15;
        if (hasTenPercentOwnerBuys) score += 10;

        return Math.min(score, 100);
    }

    /**
     * Check if this is a high-significance cluster
     * @return true if significance score >= 70
     */
    public boolean isHighSignificance() {
        return getSignificanceScore() >= 70;
    }
}
