package org.jds.edgar4j.xbrl.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a single XBRL fact (data point).
 * Handles both traditional XBRL facts and iXBRL facts.
 */
@Data
@Builder
public class XbrlFact {

    // Concept identification
    private String conceptNamespace;
    private String conceptLocalName;
    private String conceptPrefix;

    // Context and unit references
    private String contextRef;
    private String unitRef;

    // Value information
    private String rawValue;           // Original string value from document
    private BigDecimal numericValue;   // Parsed numeric value (if applicable)
    private String stringValue;        // Non-numeric value
    private FactType factType;

    // Precision/accuracy attributes
    private Integer decimals;
    private Integer precision;
    private BigDecimal scale;          // For iXBRL scale attribute

    // iXBRL specific
    private String format;             // iXBRL format code (e.g., ixt:num-dot-decimal)
    private String sign;               // Explicit sign attribute
    private boolean isNil;             // xsi:nil="true"
    private boolean isNested;          // Fact was extracted from nested iXBRL structure

    // Source tracking for debugging
    private int sourceLineNumber;
    private String sourceElement;      // Original element name (e.g., ix:nonFraction)

    // Footnote references
    @Builder.Default
    private String[] footnoteRefs = new String[0];

    /**
     * Get the fully qualified concept name.
     */
    public String getQName() {
        if (conceptPrefix != null && !conceptPrefix.isEmpty()) {
            return conceptPrefix + ":" + conceptLocalName;
        }
        return conceptLocalName;
    }

    /**
     * Get the normalized numeric value, applying scale and sign adjustments.
     */
    public BigDecimal getNormalizedValue() {
        if (numericValue == null) {
            return null;
        }

        BigDecimal result = numericValue;

        // Apply scale (iXBRL scaling)
        if (scale != null) {
            result = result.multiply(BigDecimal.TEN.pow(scale.intValue()));
        }

        // Apply sign
        if ("-".equals(sign)) {
            result = result.negate();
        }

        return result;
    }

    /**
     * Get the value rounded to the specified decimals.
     */
    public BigDecimal getRoundedValue() {
        BigDecimal normalized = getNormalizedValue();
        if (normalized == null || decimals == null) {
            return normalized;
        }

        if (decimals >= 0) {
            return normalized.setScale(decimals, RoundingMode.HALF_UP);
        } else {
            // Negative decimals means round to powers of 10
            BigDecimal divisor = BigDecimal.TEN.pow(-decimals);
            return normalized.divide(divisor, 0, RoundingMode.HALF_UP).multiply(divisor);
        }
    }

    /**
     * Check if this is a monetary fact.
     */
    public boolean isMonetary() {
        return factType == FactType.MONETARY;
    }

    /**
     * Check if this is a numeric fact.
     */
    public boolean isNumeric() {
        return factType == FactType.MONETARY
                || factType == FactType.DECIMAL
                || factType == FactType.INTEGER
                || factType == FactType.SHARES
                || factType == FactType.PURE;
    }

    public enum FactType {
        MONETARY,       // xbrli:monetaryItemType
        DECIMAL,        // xbrli:decimalItemType
        INTEGER,        // xbrli:integerItemType
        STRING,         // xbrli:stringItemType
        BOOLEAN,        // xbrli:booleanItemType
        DATE,           // xbrli:dateItemType
        SHARES,         // xbrli:sharesItemType
        PURE,           // xbrli:pureItemType (ratios, percentages)
        PER_SHARE,      // xbrli:perShareItemType
        TEXT_BLOCK,     // us-gaap:textBlockItemType
        DOMAIN_MEMBER,  // Domain member (dimensional)
        UNKNOWN
    }
}
