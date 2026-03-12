package org.jds.edgar4j.xbrl.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents an XBRL unit definition.
 * Supports simple units, divide units, and multiply units.
 */
@Data
@Builder
public class XbrlUnit {

    private String id;
    private UnitType type;

    // For simple units
    private String measure;
    private String measureNamespace;
    private String measureLocalName;

    // For complex units (divide or multiply)
    private List<String> numeratorMeasures;
    private List<String> denominatorMeasures;

    /**
     * Check if this is a monetary unit (ISO currency code).
     */
    public boolean isMonetary() {
        if (measure == null) return false;

        // Check for ISO 4217 currency namespace
        if (measure.contains("iso4217:") || measure.contains("iso4217/")) {
            return true;
        }

        // Check common currency patterns
        String lower = measure.toLowerCase();
        return lower.endsWith(":usd") || lower.endsWith(":eur")
                || lower.endsWith(":gbp") || lower.endsWith(":jpy")
                || lower.endsWith(":cad") || lower.endsWith(":aud")
                || lower.endsWith(":chf") || lower.endsWith(":cny");
    }

    /**
     * Get the currency code if this is a monetary unit.
     */
    public String getCurrencyCode() {
        if (!isMonetary() || measureLocalName == null) {
            return null;
        }
        return measureLocalName.toUpperCase();
    }

    /**
     * Check if this is a pure/ratio unit.
     */
    public boolean isPure() {
        return measure != null && measure.toLowerCase().contains("pure");
    }

    /**
     * Check if this is a shares unit.
     */
    public boolean isShares() {
        return measure != null && measure.toLowerCase().contains("shares");
    }

    /**
     * Get a human-readable representation of the unit.
     */
    public String getDisplayName() {
        switch (type) {
            case SIMPLE:
                return measureLocalName != null ? measureLocalName : measure;
            case DIVIDE:
                return String.join("*", numeratorMeasures) + " / " + String.join("*", denominatorMeasures);
            case MULTIPLY:
                return String.join("*", numeratorMeasures);
            default:
                return id;
        }
    }

    public enum UnitType {
        SIMPLE,     // Single measure
        DIVIDE,     // Numerator / Denominator
        MULTIPLY    // Multiple measures multiplied
    }
}
