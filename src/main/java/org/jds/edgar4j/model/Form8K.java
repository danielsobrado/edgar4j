package org.jds.edgar4j.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * SEC Form 8-K - Current Report
 *
 * Filed when material events occur that shareholders should know about.
 * Events include mergers, acquisitions, executive changes, earnings, bankruptcy, etc.
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "form8k")
public class Form8K {

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
     * Event date (date of earliest event reported)
     */
    @Field(type = FieldType.Date)
    private LocalDate eventDate;

    /**
     * Company CIK
     */
    @Field(type = FieldType.Keyword)
    private String companyCik;

    /**
     * Company name
     */
    @Field(type = FieldType.Text)
    private String companyName;

    /**
     * Trading symbol
     */
    @Field(type = FieldType.Keyword)
    private String tradingSymbol;

    /**
     * Company IRS number
     */
    @Field(type = FieldType.Keyword)
    private String irsNumber;

    /**
     * Company state of incorporation
     */
    @Field(type = FieldType.Keyword)
    private String stateOfIncorporation;

    /**
     * Company fiscal year end (MMDD format)
     */
    @Field(type = FieldType.Keyword)
    private String fiscalYearEnd;

    /**
     * List of item numbers reported (e.g., "1.01", "2.02", "5.02")
     */
    @Field(type = FieldType.Keyword)
    private List<String> items;

    /**
     * List of event details
     */
    @Field(type = FieldType.Nested)
    private List<EventItem> eventItems;

    /**
     * Full text content (for search)
     */
    @Field(type = FieldType.Text)
    private String textContent;

    /**
     * Industry classification (from lookup service)
     */
    @Field(type = FieldType.Keyword)
    private String industry;

    /**
     * Whether this is an amendment
     */
    @Field(type = FieldType.Boolean)
    private Boolean isAmendment;

    /**
     * Individual event item from Form 8-K
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventItem {

        /**
         * Item number (e.g., "1.01", "2.02", "5.02")
         */
        @Field(type = FieldType.Keyword)
        private String itemNumber;

        /**
         * Item title/description
         */
        @Field(type = FieldType.Text)
        private String title;

        /**
         * Event category
         */
        @Field(type = FieldType.Keyword)
        private String category;

        /**
         * Item text content
         */
        @Field(type = FieldType.Text)
        private String content;

        /**
         * Get human-readable event type
         */
        public String getEventType() {
            return getEventTypeForItem(itemNumber);
        }
    }

    /**
     * Get human-readable description for an item number
     */
    public static String getEventTypeForItem(String itemNumber) {
        if (itemNumber == null) return "Unknown";

        return switch (itemNumber) {
            case "1.01" -> "Entry into Material Agreement";
            case "1.02" -> "Termination of Material Agreement";
            case "1.03" -> "Bankruptcy or Receivership";
            case "1.04" -> "Mine Safety Reporting";
            case "2.01" -> "Completion of Acquisition or Disposition";
            case "2.02" -> "Results of Operations and Financial Condition";
            case "2.03" -> "Creation of Direct Financial Obligation";
            case "2.04" -> "Triggering Events That Accelerate Obligations";
            case "2.05" -> "Costs Associated with Exit or Disposal Activities";
            case "2.06" -> "Material Impairments";
            case "3.01" -> "Notice of Delisting or Failure to Satisfy Listing Rule";
            case "3.02" -> "Unregistered Sales of Equity Securities";
            case "3.03" -> "Material Modification to Rights of Security Holders";
            case "4.01" -> "Changes in Registrant's Certifying Accountant";
            case "4.02" -> "Non-Reliance on Previously Issued Financial Statements";
            case "5.01" -> "Changes in Control of Registrant";
            case "5.02" -> "Departure/Election of Directors or Officers";
            case "5.03" -> "Amendments to Articles/Bylaws; Change in Fiscal Year";
            case "5.04" -> "Temporary Suspension of Trading Under Employee Benefit Plans";
            case "5.05" -> "Amendments to Registrant's Code of Ethics";
            case "5.06" -> "Change in Shell Company Status";
            case "5.07" -> "Submission of Matters to Vote of Security Holders";
            case "5.08" -> "Shareholder Nominations";
            case "6.01" -> "ABS Informational and Computational Material";
            case "6.02" -> "Change of Servicer or Trustee";
            case "6.03" -> "Change in Credit Enhancement";
            case "6.04" -> "Failure to Make Required Distribution";
            case "6.05" -> "Securities Act Updating Disclosure";
            case "7.01" -> "Regulation FD Disclosure";
            case "8.01" -> "Other Events";
            case "9.01" -> "Financial Statements and Exhibits";
            default -> "Item " + itemNumber;
        };
    }

    /**
     * Get category for an item number
     */
    public static String getCategoryForItem(String itemNumber) {
        if (itemNumber == null) return "Other";

        if (itemNumber.startsWith("1.")) return "Agreements";
        if (itemNumber.startsWith("2.")) return "Financial Information";
        if (itemNumber.startsWith("3.")) return "Securities and Trading Markets";
        if (itemNumber.startsWith("4.")) return "Accountants and Financial Statements";
        if (itemNumber.startsWith("5.")) return "Corporate Governance";
        if (itemNumber.startsWith("6.")) return "Asset-Backed Securities";
        if (itemNumber.startsWith("7.")) return "Regulation FD";
        if (itemNumber.startsWith("8.")) return "Other Events";
        if (itemNumber.startsWith("9.")) return "Financial Statements and Exhibits";

        return "Other";
    }

    /**
     * Check if this 8-K contains a specific item
     */
    public boolean hasItem(String itemNumber) {
        return items != null && items.contains(itemNumber);
    }

    /**
     * Get event items by category
     */
    public List<EventItem> getEventItemsByCategory(String category) {
        if (eventItems == null) {
            return new ArrayList<>();
        }

        return eventItems.stream()
            .filter(item -> category.equals(item.getCategory()))
            .toList();
    }

    /**
     * Check if this is an earnings report (Item 2.02)
     */
    public boolean isEarningsReport() {
        return hasItem("2.02");
    }

    /**
     * Check if this is a management change (Item 5.02)
     */
    public boolean isManagementChange() {
        return hasItem("5.02");
    }

    /**
     * Check if this is an acquisition/disposition (Item 2.01)
     */
    public boolean isAcquisitionOrDisposition() {
        return hasItem("2.01");
    }

    /**
     * Check if this is a material agreement (Item 1.01)
     */
    public boolean isMaterialAgreement() {
        return hasItem("1.01");
    }

    /**
     * Get primary event type (most significant item)
     */
    public String getPrimaryEventType() {
        if (items == null || items.isEmpty()) {
            return "Unknown";
        }

        // Priority order for determining primary event
        String[] priorityItems = {
            "1.03", // Bankruptcy
            "5.01", // Change in Control
            "2.01", // Acquisition/Disposition
            "5.02", // Management Changes
            "2.02", // Earnings
            "1.01", // Material Agreement
            "4.01", // Auditor Change
        };

        for (String priorityItem : priorityItems) {
            if (items.contains(priorityItem)) {
                return getEventTypeForItem(priorityItem);
            }
        }

        // Return first item if no priority match
        return getEventTypeForItem(items.get(0));
    }
}
