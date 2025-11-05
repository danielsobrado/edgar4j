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
 * Enhanced SEC Form 4 model representing a Statement of Changes in Beneficial Ownership
 * Filed by company insiders (directors, officers, 10% owners) when they buy or sell company stock
 *
 * @author J. Daniel Sobrado
 * @version 2.0
 * @since 2025-11-05
 */
@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(indexName = "form4")
public class Form4 {

    /** Unique identifier - combination of accessionNumber */
    @Id
    private String id;

    /** SEC Accession Number (unique filing identifier) */
    @Field(type = FieldType.Keyword)
    private String accessionNumber;

    /** Date and time the form was filed with SEC */
    @Field(type = FieldType.Date)
    private LocalDateTime filingDate;

    /** Date of earliest transaction reported in this filing */
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

    /** Is this an amendment to a previously filed Form 4? */
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
}
