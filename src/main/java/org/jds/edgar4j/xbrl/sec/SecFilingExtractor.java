package org.jds.edgar4j.xbrl.sec;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts SEC-specific metadata from XBRL filings.
 *
 * KEY DIFFERENTIATOR:
 * - Extracts DEI (Document and Entity Information) facts
 * - Identifies form type, fiscal periods, amendments
 * - Provides structured company identification
 * - Handles SEC-specific concepts and extensions
 *
 * This makes the parsed data immediately useful for SEC filing analysis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecFilingExtractor {

    // DEI concepts for entity identification
    private static final List<String> ENTITY_NAME_CONCEPTS = Arrays.asList(
            "EntityRegistrantName",
            "EntityCommonStockSharesOutstanding"
    );

    private static final List<String> CIK_CONCEPTS = Arrays.asList(
            "EntityCentralIndexKey"
    );

    private static final List<String> FORM_TYPE_CONCEPTS = Arrays.asList(
            "DocumentType",
            "DocumentInformationDocumentType"
    );

    // Fiscal period concepts
    private static final List<String> FISCAL_YEAR_CONCEPTS = Arrays.asList(
            "DocumentFiscalYearFocus",
            "CurrentFiscalYearEndDate"
    );

    private static final List<String> FISCAL_PERIOD_CONCEPTS = Arrays.asList(
            "DocumentFiscalPeriodFocus"
    );

    // Document metadata concepts
    private static final List<String> DOCUMENT_PERIOD_CONCEPTS = Arrays.asList(
            "DocumentPeriodEndDate"
    );

    private static final List<String> AMENDMENT_CONCEPTS = Arrays.asList(
            "AmendmentFlag",
            "DocumentAmendmentFlag"
    );

    // SEC form type patterns
    private static final Map<String, FormTypeInfo> FORM_TYPES = new LinkedHashMap<>();

    static {
        FORM_TYPES.put("10-K", new FormTypeInfo("10-K", "Annual Report", PeriodType.ANNUAL, true));
        FORM_TYPES.put("10-K/A", new FormTypeInfo("10-K/A", "Annual Report Amendment", PeriodType.ANNUAL, true));
        FORM_TYPES.put("10-Q", new FormTypeInfo("10-Q", "Quarterly Report", PeriodType.QUARTERLY, true));
        FORM_TYPES.put("10-Q/A", new FormTypeInfo("10-Q/A", "Quarterly Report Amendment", PeriodType.QUARTERLY, true));
        FORM_TYPES.put("8-K", new FormTypeInfo("8-K", "Current Report", PeriodType.EVENT, false));
        FORM_TYPES.put("8-K/A", new FormTypeInfo("8-K/A", "Current Report Amendment", PeriodType.EVENT, false));
        FORM_TYPES.put("20-F", new FormTypeInfo("20-F", "Annual Report (Foreign)", PeriodType.ANNUAL, true));
        FORM_TYPES.put("40-F", new FormTypeInfo("40-F", "Annual Report (Canadian)", PeriodType.ANNUAL, true));
        FORM_TYPES.put("6-K", new FormTypeInfo("6-K", "Report of Foreign Issuer", PeriodType.OTHER, false));
        FORM_TYPES.put("S-1", new FormTypeInfo("S-1", "Registration Statement", PeriodType.OTHER, false));
        FORM_TYPES.put("424B", new FormTypeInfo("424B", "Prospectus", PeriodType.OTHER, false));
    }

    // CIK pattern
    private static final Pattern CIK_PATTERN = Pattern.compile("^\\d{10}$");

    /**
     * Extract SEC filing metadata from XBRL instance.
     */
    public SecFilingMetadata extract(XbrlInstance instance) {
        SecFilingMetadata.SecFilingMetadataBuilder builder = SecFilingMetadata.builder();

        // Build fact lookup by concept
        Map<String, List<XbrlFact>> factsByConcept = instance.getFacts().stream()
                .collect(Collectors.groupingBy(XbrlFact::getConceptLocalName));

        // Extract entity information
        builder.entityName(extractFirstValue(factsByConcept, ENTITY_NAME_CONCEPTS));
        builder.cik(extractCik(instance, factsByConcept));

        // Extract form type
        String formType = extractFirstValue(factsByConcept, FORM_TYPE_CONCEPTS);
        builder.formType(formType);
        builder.formTypeInfo(FORM_TYPES.get(formType));

        // Extract fiscal information
        builder.fiscalYear(extractFiscalYear(factsByConcept));
        builder.fiscalPeriod(extractFirstValue(factsByConcept, FISCAL_PERIOD_CONCEPTS));
        builder.fiscalYearEndDate(extractFiscalYearEnd(factsByConcept));

        // Extract document period
        builder.documentPeriodEndDate(extractDate(factsByConcept, DOCUMENT_PERIOD_CONCEPTS));

        // Check for amendment
        builder.isAmendment(extractAmendmentFlag(factsByConcept));

        // Extract additional DEI facts
        builder.deiData(extractAllDeiFacts(factsByConcept));

        // Extract trading information
        builder.tradingSymbol(extractFirstValue(factsByConcept, List.of("TradingSymbol")));
        builder.securityExchange(extractFirstValue(factsByConcept, List.of(
                "SecurityExchangeName", "Security12bTitle")));

        // Extract share information
        builder.sharesOutstanding(extractSharesOutstanding(factsByConcept));

        // Determine filing category
        builder.filingCategory(determineFilingCategory(formType, instance));

        return builder.build();
    }

    /**
     * Extract CIK from multiple sources.
     */
    private String extractCik(XbrlInstance instance, Map<String, List<XbrlFact>> factsByConcept) {
        // Try DEI fact first
        String cik = extractFirstValue(factsByConcept, CIK_CONCEPTS);
        if (cik != null && CIK_PATTERN.matcher(cik).matches()) {
            return cik;
        }

        // Try entity identifier from context
        String entityId = instance.getEntityIdentifier();
        if (entityId != null) {
            // May be just the number or include scheme
            String digits = entityId.replaceAll("\\D", "");
            if (digits.length() == 10) {
                return digits;
            }
            // Pad with leading zeros
            if (digits.length() > 0 && digits.length() < 10) {
                return String.format("%010d", Long.parseLong(digits));
            }
        }

        return null;
    }

    /**
     * Extract fiscal year from concepts.
     */
    private Integer extractFiscalYear(Map<String, List<XbrlFact>> factsByConcept) {
        String value = extractFirstValue(factsByConcept, FISCAL_YEAR_CONCEPTS);
        if (value != null) {
            try {
                // May be just year or a date
                if (value.length() == 4) {
                    return Integer.parseInt(value);
                }
                // Parse as date and extract year
                LocalDate date = parseDate(value);
                if (date != null) {
                    return date.getYear();
                }
            } catch (NumberFormatException e) {
                log.trace("Could not parse fiscal year: {}", value);
            }
        }
        return null;
    }

    /**
     * Extract fiscal year end date.
     */
    private String extractFiscalYearEnd(Map<String, List<XbrlFact>> factsByConcept) {
        String value = extractFirstValue(factsByConcept, List.of("CurrentFiscalYearEndDate"));
        if (value != null) {
            // Usually in --MM-DD format
            if (value.startsWith("--")) {
                return value.substring(2);  // Remove leading --
            }
            return value;
        }
        return null;
    }

    /**
     * Extract date from concepts.
     */
    private LocalDate extractDate(Map<String, List<XbrlFact>> factsByConcept, List<String> concepts) {
        String value = extractFirstValue(factsByConcept, concepts);
        return parseDate(value);
    }

    /**
     * Parse date from various formats.
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return null;

        // Try ISO format first
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            // Try other formats
        }

        // Try MM/dd/yyyy
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (DateTimeParseException e) {
            // Continue
        }

        // Try with time component
        if (value.contains("T")) {
            try {
                return LocalDate.parse(value.substring(0, value.indexOf("T")));
            } catch (DateTimeParseException e) {
                // Continue
            }
        }

        return null;
    }

    /**
     * Extract amendment flag.
     */
    private boolean extractAmendmentFlag(Map<String, List<XbrlFact>> factsByConcept) {
        String value = extractFirstValue(factsByConcept, AMENDMENT_CONCEPTS);
        if (value != null) {
            return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        }
        return false;
    }

    /**
     * Extract shares outstanding.
     */
    private Long extractSharesOutstanding(Map<String, List<XbrlFact>> factsByConcept) {
        List<XbrlFact> facts = factsByConcept.get("EntityCommonStockSharesOutstanding");
        if (facts != null && !facts.isEmpty()) {
            XbrlFact fact = facts.get(0);
            if (fact.getNormalizedValue() != null) {
                return fact.getNormalizedValue().longValue();
            }
        }
        return null;
    }

    /**
     * Extract all DEI (Document and Entity Information) facts.
     */
    private Map<String, String> extractAllDeiFacts(Map<String, List<XbrlFact>> factsByConcept) {
        Map<String, String> deiData = new LinkedHashMap<>();

        // Common DEI concepts
        List<String> deiConcepts = Arrays.asList(
                "EntityRegistrantName",
                "EntityCentralIndexKey",
                "DocumentType",
                "DocumentPeriodEndDate",
                "DocumentFiscalYearFocus",
                "DocumentFiscalPeriodFocus",
                "AmendmentFlag",
                "CurrentFiscalYearEndDate",
                "EntityCurrentReportingStatus",
                "EntityShellCompany",
                "EntitySmallBusiness",
                "EntityEmergingGrowthCompany",
                "EntityFilerCategory",
                "EntityIncorporationStateCountryCode",
                "EntityTaxIdentificationNumber",
                "EntityAddressAddressLine1",
                "EntityAddressCityOrTown",
                "EntityAddressStateOrProvince",
                "EntityAddressPostalZipCode",
                "CityAreaCode",
                "LocalPhoneNumber",
                "TradingSymbol",
                "SecurityExchangeName",
                "EntityPublicFloat",
                "EntityCommonStockSharesOutstanding",
                "EntityListingDepositoryReceiptRatio",
                "EntityVoluntaryFilers",
                "EntityWellKnownSeasonedIssuer",
                "IcfrAuditorAttestationFlag"
        );

        for (String concept : deiConcepts) {
            List<XbrlFact> facts = factsByConcept.get(concept);
            if (facts != null && !facts.isEmpty()) {
                XbrlFact fact = facts.get(0);
                String value = fact.getStringValue();
                if (value == null && fact.getNormalizedValue() != null) {
                    value = fact.getNormalizedValue().toString();
                }
                if (value != null) {
                    deiData.put(concept, value);
                }
            }
        }

        return deiData;
    }

    /**
     * Determine filing category based on form type and content.
     */
    private FilingCategory determineFilingCategory(String formType, XbrlInstance instance) {
        if (formType == null) return FilingCategory.OTHER;

        String upper = formType.toUpperCase();

        if (upper.startsWith("10-K") || upper.equals("20-F") || upper.equals("40-F")) {
            return FilingCategory.ANNUAL_REPORT;
        }
        if (upper.startsWith("10-Q")) {
            return FilingCategory.QUARTERLY_REPORT;
        }
        if (upper.startsWith("8-K")) {
            return FilingCategory.CURRENT_REPORT;
        }
        if (upper.startsWith("S-") || upper.startsWith("F-")) {
            return FilingCategory.REGISTRATION;
        }
        if (upper.startsWith("424")) {
            return FilingCategory.PROSPECTUS;
        }
        if (upper.contains("DEF") || upper.contains("PRE")) {
            return FilingCategory.PROXY;
        }

        return FilingCategory.OTHER;
    }

    /**
     * Extract first non-null value from a list of concepts.
     */
    private String extractFirstValue(Map<String, List<XbrlFact>> factsByConcept, List<String> concepts) {
        for (String concept : concepts) {
            List<XbrlFact> facts = factsByConcept.get(concept);
            if (facts != null && !facts.isEmpty()) {
                XbrlFact fact = facts.get(0);
                String value = fact.getStringValue();
                if (value == null && fact.getNormalizedValue() != null) {
                    value = fact.getNormalizedValue().toString();
                }
                if (value != null && !value.isEmpty()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    // Data classes

    @Data
    @Builder
    public static class SecFilingMetadata {
        // Entity identification
        private String entityName;
        private String cik;
        private String tradingSymbol;
        private String securityExchange;

        // Document information
        private String formType;
        private FormTypeInfo formTypeInfo;
        private boolean isAmendment;
        private LocalDate documentPeriodEndDate;

        // Fiscal information
        private Integer fiscalYear;
        private String fiscalPeriod;  // Q1, Q2, Q3, Q4, FY
        private String fiscalYearEndDate;  // MM-DD

        // Share information
        private Long sharesOutstanding;

        // Classification
        private FilingCategory filingCategory;

        // All DEI data
        private Map<String, String> deiData;

        public boolean isAnnualReport() {
            return filingCategory == FilingCategory.ANNUAL_REPORT;
        }

        public boolean isQuarterlyReport() {
            return filingCategory == FilingCategory.QUARTERLY_REPORT;
        }

        public String getFormattedCik() {
            return cik != null ? String.format("%010d", Long.parseLong(cik)) : null;
        }
    }

    @Data
    public static class FormTypeInfo {
        private final String formType;
        private final String description;
        private final PeriodType periodType;
        private final boolean hasFinancialStatements;
    }

    public enum PeriodType {
        ANNUAL, QUARTERLY, EVENT, OTHER
    }

    public enum FilingCategory {
        ANNUAL_REPORT,
        QUARTERLY_REPORT,
        CURRENT_REPORT,
        REGISTRATION,
        PROSPECTUS,
        PROXY,
        OTHER
    }
}
