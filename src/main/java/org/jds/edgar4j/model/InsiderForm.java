package org.jds.edgar4j.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Unified model for SEC Insider Forms (Forms 3, 4, and 5)
 *
 * Form 3: Initial Statement of Beneficial Ownership
 *   - Filed when someone becomes an insider (officer, director, 10% owner)
 *   - Reports initial holdings, no transactions
 *
 * Form 4: Statement of Changes in Beneficial Ownership
 *   - Filed when insiders buy or sell company stock
 *   - Reports transactions (purchases, sales, grants, etc.)
 *
 * Form 5: Annual Statement of Changes in Beneficial Ownership
 *   - Filed annually within 45 days of fiscal year end
 *   - Reports transactions exempt from timely Form 4 reporting
 *
 * All three forms share the same XML structure and can be parsed identically.
 *
 * @author J. Daniel Sobrado
 * @version 3.0
 * @since 2025-11-07
 */
@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(indexName = "insider_forms")
public class InsiderForm {

    /** Unique identifier - combination of accessionNumber */
    @Id
    private String id;

    /** Form type: "3" (initial), "4" (transaction), or "5" (annual) */
    @Field(type = FieldType.Keyword)
    private String formType;

    /** SEC Accession Number (unique filing identifier) */
    @Field(type = FieldType.Keyword)
    private String accessionNumber;

    /** Date and time the form was filed with SEC */
    @Field(type = FieldType.Date)
    private LocalDateTime filingDate;

    /** Date of earliest transaction reported in this filing (or report period for Form 3/5) */
    @Field(type = FieldType.Date)
    private LocalDate periodOfReport;

    /** Issuer (company) CIK */
    @Field(type = FieldType.Keyword)
    private String issuerCik;

    /** Issuer (company) name */
    @Field(type = FieldType.Text)
    private String issuerName;

    /** Trading symbol (ticker) */
    @Field(type = FieldType.Keyword)
    private String tradingSymbol;

    /** Reporting owner(s) information - can have multiple owners in one filing */
    @Field(type = FieldType.Nested)
    @Builder.Default
    private List<ReportingOwner> reportingOwners = new ArrayList<>();

    /** Non-derivative transactions (Table I) - common stock, preferred stock, etc. */
    @Field(type = FieldType.Nested)
    @Builder.Default
    private List<NonDerivativeTransaction> nonDerivativeTransactions = new ArrayList<>();

    /** Derivative transactions (Table II) - options, warrants, RSUs, etc. */
    @Field(type = FieldType.Nested)
    @Builder.Default
    private List<DerivativeTransaction> derivativeTransactions = new ArrayList<>();

    /** Footnotes and remarks */
    @Field(type = FieldType.Text)
    private String footnotes;

    /** Remarks */
    @Field(type = FieldType.Text)
    private String remarks;

    /** Signature */
    @Field(type = FieldType.Text)
    private String signature;

    /** Signature date */
    @Field(type = FieldType.Date)
    private LocalDate signatureDate;

    /** Is this an amendment to a previously filed form? */
    private boolean isAmendment;

    /** Original filing date (if amendment) */
    @Field(type = FieldType.Date)
    private LocalDate originalFilingDate;

    /** No longer subject to Section 16 */
    private boolean notSubjectToSection16;

    /** Form filed by multiple reporting persons */
    private boolean formFiledByMultiplePersons;

    /** XML document URL */
    @Field(type = FieldType.Keyword)
    private String documentUrl;

    /**
     * Get the primary reporting owner (first owner in the list)
     * @return primary reporting owner or null if none
     */
    public ReportingOwner getPrimaryReportingOwner() {
        return reportingOwners != null && !reportingOwners.isEmpty()
            ? reportingOwners.get(0)
            : null;
    }

    /**
     * Check if this filing contains any purchase transactions
     * @return true if there are any purchases
     */
    public boolean hasPurchases() {
        if (nonDerivativeTransactions != null) {
            return nonDerivativeTransactions.stream().anyMatch(NonDerivativeTransaction::isPurchase);
        }
        return false;
    }

    /**
     * Check if this filing contains any sale transactions
     * @return true if there are any sales
     */
    public boolean hasSales() {
        if (nonDerivativeTransactions != null) {
            return nonDerivativeTransactions.stream().anyMatch(NonDerivativeTransaction::isSale);
        }
        return false;
    }

    /**
     * Get all purchase transactions from this filing
     * @return list of purchase transactions
     */
    public List<NonDerivativeTransaction> getPurchaseTransactions() {
        if (nonDerivativeTransactions == null) {
            return new ArrayList<>();
        }
        return nonDerivativeTransactions.stream()
            .filter(NonDerivativeTransaction::isPurchase)
            .toList();
    }

    /**
     * Get all sale transactions from this filing
     * @return list of sale transactions
     */
    public List<NonDerivativeTransaction> getSaleTransactions() {
        if (nonDerivativeTransactions == null) {
            return new ArrayList<>();
        }
        return nonDerivativeTransactions.stream()
            .filter(NonDerivativeTransaction::isSale)
            .toList();
    }

    /**
     * Check if this is a Form 3 (initial ownership statement)
     * @return true if formType is "3"
     */
    public boolean isForm3() {
        return "3".equals(formType);
    }

    /**
     * Check if this is a Form 4 (transaction report)
     * @return true if formType is "4"
     */
    public boolean isForm4() {
        return "4".equals(formType);
    }

    /**
     * Check if this is a Form 5 (annual report)
     * @return true if formType is "5"
     */
    public boolean isForm5() {
        return "5".equals(formType);
    }

    /**
     * Get form type description
     * @return human-readable form type description
     */
    public String getFormTypeDescription() {
        if (formType == null) {
            return "Unknown";
        }
        switch (formType) {
            case "3":
                return "Initial Statement of Beneficial Ownership";
            case "4":
                return "Statement of Changes in Beneficial Ownership";
            case "5":
                return "Annual Statement of Changes in Beneficial Ownership";
            default:
                return "Unknown Form Type: " + formType;
        }
    }
}
