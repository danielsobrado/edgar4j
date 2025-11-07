package org.jds.edgar4j.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SEC Form 13F - Quarterly Report of Institutional Investment Managers
 *
 * Filed by institutional investment managers with $100M+ assets under management.
 * Shows equity holdings as of quarter end.
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "form13f")
public class Form13F {

    @Id
    private String id;

    /**
     * SEC accession number (unique identifier)
     */
    @Field(type = FieldType.Keyword)
    private String accessionNumber;

    /**
     * Filing date/time
     */
    @Field(type = FieldType.Date)
    private LocalDateTime filingDate;

    /**
     * Period of report (quarter end date)
     */
    @Field(type = FieldType.Date)
    private LocalDate periodOfReport;

    /**
     * Filer CIK (institutional investor)
     */
    @Field(type = FieldType.Keyword)
    private String filerCik;

    /**
     * Filer name (institution name)
     */
    @Field(type = FieldType.Text)
    private String filerName;

    /**
     * Filer IRS number (if available)
     */
    @Field(type = FieldType.Keyword)
    private String filerIrsNumber;

    /**
     * Filer street address
     */
    @Field(type = FieldType.Text)
    private String filerStreet1;

    @Field(type = FieldType.Text)
    private String filerStreet2;

    @Field(type = FieldType.Keyword)
    private String filerCity;

    @Field(type = FieldType.Keyword)
    private String filerState;

    @Field(type = FieldType.Keyword)
    private String filerZipCode;

    /**
     * Report type (e.g., "13F-HR" for holdings report)
     */
    @Field(type = FieldType.Keyword)
    private String reportType;

    /**
     * Amendment flag (true if this is an amendment)
     */
    @Field(type = FieldType.Boolean)
    private Boolean isAmendment;

    /**
     * Amendment number (if amendment)
     */
    @Field(type = FieldType.Integer)
    private Integer amendmentNumber;

    /**
     * Total value of holdings (in dollars)
     */
    @Field(type = FieldType.Double)
    private BigDecimal totalValue;

    /**
     * Number of holdings (info table entries)
     */
    @Field(type = FieldType.Integer)
    private Integer holdingsCount;

    /**
     * List of individual holdings (info table)
     */
    @Field(type = FieldType.Nested)
    private List<Holding> holdings;

    /**
     * Signature
     */
    @Field(type = FieldType.Text)
    private String signature;

    /**
     * Signature date
     */
    @Field(type = FieldType.Date)
    private LocalDate signatureDate;

    /**
     * Individual holding from the info table
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Holding {

        /**
         * Name of issuer (company name)
         */
        @Field(type = FieldType.Text)
        private String nameOfIssuer;

        /**
         * Title of class (e.g., "COM" for common stock)
         */
        @Field(type = FieldType.Keyword)
        private String titleOfClass;

        /**
         * CUSIP (unique security identifier)
         */
        @Field(type = FieldType.Keyword)
        private String cusip;

        /**
         * Value of holding (in thousands of dollars)
         */
        @Field(type = FieldType.Double)
        private BigDecimal value;

        /**
         * Number of shares or principal amount
         */
        @Field(type = FieldType.Long)
        private Long sharesOrPrincipalAmount;

        /**
         * Share/Principal indicator ("SH" for shares, "PRN" for principal)
         */
        @Field(type = FieldType.Keyword)
        private String shPrn;

        /**
         * Put/Call indicator (for options: "PUT" or "CALL")
         */
        @Field(type = FieldType.Keyword)
        private String putCall;

        /**
         * Investment discretion ("SOLE", "SHARED", or "NONE")
         */
        @Field(type = FieldType.Keyword)
        private String investmentDiscretion;

        /**
         * Other managers reporting for this holding
         */
        @Field(type = FieldType.Integer)
        private Integer otherManager;

        /**
         * Voting authority - sole
         */
        @Field(type = FieldType.Long)
        private Long votingAuthoritySole;

        /**
         * Voting authority - shared
         */
        @Field(type = FieldType.Long)
        private Long votingAuthorityShared;

        /**
         * Voting authority - none
         */
        @Field(type = FieldType.Long)
        private Long votingAuthorityNone;
    }

    /**
     * Calculate total holdings count
     */
    public void calculateHoldingsCount() {
        this.holdingsCount = (holdings != null) ? holdings.size() : 0;
    }

    /**
     * Calculate total value from holdings
     */
    public void calculateTotalValue() {
        if (holdings == null || holdings.isEmpty()) {
            this.totalValue = BigDecimal.ZERO;
            return;
        }

        this.totalValue = holdings.stream()
            .filter(h -> h.getValue() != null)
            .map(Holding::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get top holdings by value
     */
    public List<Holding> getTopHoldings(int limit) {
        if (holdings == null) {
            return List.of();
        }

        return holdings.stream()
            .sorted((h1, h2) -> {
                BigDecimal v1 = h1.getValue() != null ? h1.getValue() : BigDecimal.ZERO;
                BigDecimal v2 = h2.getValue() != null ? h2.getValue() : BigDecimal.ZERO;
                return v2.compareTo(v1);
            })
            .limit(limit)
            .toList();
    }

    /**
     * Get holding by CUSIP
     */
    public Holding getHoldingByCusip(String cusip) {
        if (holdings == null || cusip == null) {
            return null;
        }

        return holdings.stream()
            .filter(h -> cusip.equals(h.getCusip()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if institution holds a specific security
     */
    public boolean holdsSecurityByCusip(String cusip) {
        return getHoldingByCusip(cusip) != null;
    }
}
