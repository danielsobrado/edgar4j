package org.jds.edgar4j.xbrl.standardization;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standardizes XBRL concepts across different companies for comparison.
 *
 * KEY DIFFERENTIATOR:
 * - Maps company-specific extensions to standard US-GAAP concepts
 * - Normalizes sign conventions (expenses always positive, etc.)
 * - Handles concept hierarchy variations
 * - Enables true cross-company financial comparison
 *
 * Problem this solves:
 * - Apple might report "ProductRevenue" while Microsoft uses "RevenueFromProductsAndServices"
 * - Both should map to a standard "Revenue" concept for comparison
 */
@Slf4j
@Component
public class ConceptStandardizer {

    // Standard concept mappings: standard concept -> list of possible GAAP/extension names
    private static final Map<String, ConceptMapping> STANDARD_MAPPINGS = new LinkedHashMap<>();

    static {
        // REVENUE concepts
        STANDARD_MAPPINGS.put("Revenue", ConceptMapping.builder()
                .standardConcept("Revenue")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "Revenues",
                        "RevenueFromContractWithCustomerExcludingAssessedTax",
                        "RevenueFromContractWithCustomerIncludingAssessedTax",
                        "SalesRevenueNet",
                        "SalesRevenueGoodsNet",
                        "SalesRevenueServicesNet",
                        "NetRevenue",
                        "TotalRevenue",
                        "TotalRevenuesAndOtherIncome"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total revenue from all sources")
                .build());

        // COST OF REVENUE
        STANDARD_MAPPINGS.put("CostOfRevenue", ConceptMapping.builder()
                .standardConcept("CostOfRevenue")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "CostOfGoodsAndServicesSold",
                        "CostOfGoodsSold",
                        "CostOfServices",
                        "CostOfRevenue",
                        "CostOfSales"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Direct costs attributable to revenue")
                .build());

        // GROSS PROFIT
        STANDARD_MAPPINGS.put("GrossProfit", ConceptMapping.builder()
                .standardConcept("GrossProfit")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "GrossProfit",
                        "GrossMargin"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Revenue minus cost of revenue")
                .build());

        // R&D EXPENSE
        STANDARD_MAPPINGS.put("ResearchAndDevelopment", ConceptMapping.builder()
                .standardConcept("ResearchAndDevelopment")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "ResearchAndDevelopmentExpense",
                        "ResearchAndDevelopmentExpenseExcludingAcquiredInProcessCost",
                        "ResearchAndDevelopmentCosts"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Research and development costs")
                .build());

        // SG&A EXPENSE
        STANDARD_MAPPINGS.put("SellingGeneralAndAdministrative", ConceptMapping.builder()
                .standardConcept("SellingGeneralAndAdministrative")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "SellingGeneralAndAdministrativeExpense",
                        "SellingAndMarketingExpense",
                        "GeneralAndAdministrativeExpense"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Selling, general and administrative expenses")
                .build());

        // OPERATING INCOME
        STANDARD_MAPPINGS.put("OperatingIncome", ConceptMapping.builder()
                .standardConcept("OperatingIncome")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "OperatingIncomeLoss",
                        "IncomeLossFromOperations",
                        "OperatingProfit"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Income from core operations")
                .build());

        // NET INCOME
        STANDARD_MAPPINGS.put("NetIncome", ConceptMapping.builder()
                .standardConcept("NetIncome")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "NetIncomeLoss",
                        "NetIncomeLossAvailableToCommonStockholdersBasic",
                        "ProfitLoss",
                        "NetIncomeLossAttributableToParent"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Bottom line net income/loss")
                .build());

        // EPS
        STANDARD_MAPPINGS.put("EarningsPerShareBasic", ConceptMapping.builder()
                .standardConcept("EarningsPerShareBasic")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "EarningsPerShareBasic",
                        "BasicEarningsLossPerShare",
                        "IncomeLossFromContinuingOperationsPerBasicShare"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Basic earnings per share")
                .build());

        STANDARD_MAPPINGS.put("EarningsPerShareDiluted", ConceptMapping.builder()
                .standardConcept("EarningsPerShareDiluted")
                .category(ConceptCategory.INCOME_STATEMENT)
                .alternateNames(Arrays.asList(
                        "EarningsPerShareDiluted",
                        "DilutedEarningsLossPerShare",
                        "IncomeLossFromContinuingOperationsPerDilutedShare"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Diluted earnings per share")
                .build());

        // BALANCE SHEET - ASSETS
        STANDARD_MAPPINGS.put("TotalAssets", ConceptMapping.builder()
                .standardConcept("TotalAssets")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "Assets",
                        "TotalAssets"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total assets")
                .build());

        STANDARD_MAPPINGS.put("Cash", ConceptMapping.builder()
                .standardConcept("Cash")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "CashAndCashEquivalentsAtCarryingValue",
                        "Cash",
                        "CashAndCashEquivalents",
                        "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Cash and cash equivalents")
                .build());

        STANDARD_MAPPINGS.put("TotalCurrentAssets", ConceptMapping.builder()
                .standardConcept("TotalCurrentAssets")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "AssetsCurrent",
                        "TotalCurrentAssets"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total current assets")
                .build());

        // LIABILITIES
        STANDARD_MAPPINGS.put("TotalLiabilities", ConceptMapping.builder()
                .standardConcept("TotalLiabilities")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "Liabilities",
                        "TotalLiabilities"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total liabilities")
                .build());

        STANDARD_MAPPINGS.put("TotalCurrentLiabilities", ConceptMapping.builder()
                .standardConcept("TotalCurrentLiabilities")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "LiabilitiesCurrent",
                        "TotalCurrentLiabilities"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total current liabilities")
                .build());

        STANDARD_MAPPINGS.put("LongTermDebt", ConceptMapping.builder()
                .standardConcept("LongTermDebt")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "LongTermDebtNoncurrent",
                        "LongTermDebt",
                        "LongTermDebtAndCapitalLeaseObligations"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Long-term debt")
                .build());

        // EQUITY
        STANDARD_MAPPINGS.put("TotalEquity", ConceptMapping.builder()
                .standardConcept("TotalEquity")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "StockholdersEquity",
                        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
                        "TotalEquity",
                        "TotalShareholdersEquity"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Total shareholders equity")
                .build());

        STANDARD_MAPPINGS.put("RetainedEarnings", ConceptMapping.builder()
                .standardConcept("RetainedEarnings")
                .category(ConceptCategory.BALANCE_SHEET)
                .alternateNames(Arrays.asList(
                        "RetainedEarningsAccumulatedDeficit",
                        "RetainedEarnings"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Retained earnings (accumulated deficit)")
                .build());

        // CASH FLOW
        STANDARD_MAPPINGS.put("OperatingCashFlow", ConceptMapping.builder()
                .standardConcept("OperatingCashFlow")
                .category(ConceptCategory.CASH_FLOW)
                .alternateNames(Arrays.asList(
                        "NetCashProvidedByUsedInOperatingActivities",
                        "CashFlowsFromOperatingActivities"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Net cash from operating activities")
                .build());

        STANDARD_MAPPINGS.put("InvestingCashFlow", ConceptMapping.builder()
                .standardConcept("InvestingCashFlow")
                .category(ConceptCategory.CASH_FLOW)
                .alternateNames(Arrays.asList(
                        "NetCashProvidedByUsedInInvestingActivities",
                        "CashFlowsFromInvestingActivities"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Net cash from investing activities")
                .build());

        STANDARD_MAPPINGS.put("FinancingCashFlow", ConceptMapping.builder()
                .standardConcept("FinancingCashFlow")
                .category(ConceptCategory.CASH_FLOW)
                .alternateNames(Arrays.asList(
                        "NetCashProvidedByUsedInFinancingActivities",
                        "CashFlowsFromFinancingActivities"
                ))
                .signConvention(SignConvention.POSITIVE_INCOME)
                .description("Net cash from financing activities")
                .build());

        STANDARD_MAPPINGS.put("CapitalExpenditures", ConceptMapping.builder()
                .standardConcept("CapitalExpenditures")
                .category(ConceptCategory.CASH_FLOW)
                .alternateNames(Arrays.asList(
                        "PaymentsToAcquirePropertyPlantAndEquipment",
                        "CapitalExpendituresIncurredButNotYetPaid",
                        "PurchaseOfPropertyPlantAndEquipment"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Capital expenditures")
                .build());

        // SHARES
        STANDARD_MAPPINGS.put("SharesOutstanding", ConceptMapping.builder()
                .standardConcept("SharesOutstanding")
                .category(ConceptCategory.SHARES)
                .alternateNames(Arrays.asList(
                        "CommonStockSharesOutstanding",
                        "WeightedAverageNumberOfSharesOutstandingBasic",
                        "SharesOutstanding"
                ))
                .signConvention(SignConvention.POSITIVE)
                .description("Shares outstanding")
                .build());
    }

    // Reverse lookup: GAAP concept -> standard concept
    private static final Map<String, String> REVERSE_MAPPING = new HashMap<>();

    static {
        for (Map.Entry<String, ConceptMapping> entry : STANDARD_MAPPINGS.entrySet()) {
            String standardConcept = entry.getKey();
            ConceptMapping mapping = entry.getValue();

            for (String alternate : mapping.getAlternateNames()) {
                REVERSE_MAPPING.put(alternate.toLowerCase(), standardConcept);
            }
        }
    }

    /**
     * Standardize all facts in an XBRL instance.
     */
    public StandardizedData standardize(XbrlInstance instance) {
        StandardizedData.StandardizedDataBuilder builder = StandardizedData.builder();
        builder.originalInstance(instance);

        Map<String, StandardizedFact> standardizedFacts = new LinkedHashMap<>();
        List<String> unmappedConcepts = new ArrayList<>();

        // Group facts by standard concept
        for (XbrlFact fact : instance.getFacts()) {
            String originalConcept = fact.getConceptLocalName();
            String standardConcept = mapToStandard(originalConcept);

            if (standardConcept != null) {
                ConceptMapping mapping = STANDARD_MAPPINGS.get(standardConcept);
                BigDecimal normalizedValue = normalizeValue(fact, mapping);

                StandardizedFact standardized = StandardizedFact.builder()
                        .standardConcept(standardConcept)
                        .originalConcept(originalConcept)
                        .value(normalizedValue)
                        .originalValue(fact.getNormalizedValue())
                        .contextRef(fact.getContextRef())
                        .category(mapping.getCategory())
                        .description(mapping.getDescription())
                        .build();

                // Use first fact for each concept (primary, non-dimensional)
                String key = standardConcept + "_" + fact.getContextRef();
                if (!standardizedFacts.containsKey(key)) {
                    standardizedFacts.put(key, standardized);
                }
            } else {
                if (!unmappedConcepts.contains(originalConcept)) {
                    unmappedConcepts.add(originalConcept);
                }
            }
        }

        builder.facts(new ArrayList<>(standardizedFacts.values()));
        builder.unmappedConcepts(unmappedConcepts);

        return builder.build();
    }

    /**
     * Map an original concept to its standard equivalent.
     */
    public String mapToStandard(String originalConcept) {
        if (originalConcept == null) return null;

        // Direct lookup
        String standard = REVERSE_MAPPING.get(originalConcept.toLowerCase());
        if (standard != null) return standard;

        // Try exact match
        if (STANDARD_MAPPINGS.containsKey(originalConcept)) {
            return originalConcept;
        }

        // Fuzzy match for extensions
        return fuzzyMatch(originalConcept);
    }

    /**
     * Fuzzy match for company extension concepts.
     */
    private String fuzzyMatch(String concept) {
        String lower = concept.toLowerCase();

        // Revenue patterns
        if (lower.contains("revenue") || lower.contains("sales")) {
            if (lower.contains("cost")) return "CostOfRevenue";
            return "Revenue";
        }

        // Expense patterns
        if (lower.contains("research") && lower.contains("development")) {
            return "ResearchAndDevelopment";
        }
        if (lower.contains("selling") || lower.contains("administrative")) {
            return "SellingGeneralAndAdministrative";
        }

        // Income patterns
        if (lower.contains("netincome") || lower.contains("netloss") ||
                (lower.contains("net") && lower.contains("income"))) {
            return "NetIncome";
        }
        if (lower.contains("operating") && (lower.contains("income") || lower.contains("profit"))) {
            return "OperatingIncome";
        }
        if (lower.contains("gross") && lower.contains("profit")) {
            return "GrossProfit";
        }

        // Balance sheet patterns
        if (lower.equals("assets") || lower.contains("totalasset")) {
            return "TotalAssets";
        }
        if (lower.equals("liabilities") || lower.contains("totalliabilit")) {
            return "TotalLiabilities";
        }
        if (lower.contains("equity") && (lower.contains("total") || lower.contains("stockholder"))) {
            return "TotalEquity";
        }
        if (lower.contains("cash") && lower.contains("equivalent")) {
            return "Cash";
        }

        // Cash flow patterns
        if (lower.contains("operating") && lower.contains("cash")) {
            return "OperatingCashFlow";
        }
        if (lower.contains("capex") || (lower.contains("capital") && lower.contains("expenditure"))) {
            return "CapitalExpenditures";
        }

        return null;
    }

    /**
     * Normalize value according to sign convention.
     */
    private BigDecimal normalizeValue(XbrlFact fact, ConceptMapping mapping) {
        BigDecimal value = fact.getNormalizedValue();
        if (value == null) return null;

        switch (mapping.getSignConvention()) {
            case POSITIVE:
                // Always positive (e.g., expenses, assets)
                return value.abs();

            case POSITIVE_INCOME:
                // Positive for income, negative for loss
                // Keep as-is since XBRL typically represents this correctly
                return value;

            case NEGATIVE_EXPENSE:
                // Expenses shown as negative
                return value.compareTo(BigDecimal.ZERO) > 0 ? value.negate() : value;

            default:
                return value;
        }
    }

    /**
     * Compare standardized data across multiple companies.
     */
    public ComparisonResult compare(List<StandardizedData> companies) {
        ComparisonResult.ComparisonResultBuilder builder = ComparisonResult.builder();

        Map<String, Map<String, BigDecimal>> conceptsByCompany = new LinkedHashMap<>();

        for (StandardizedData company : companies) {
            String companyId = company.getOriginalInstance().getEntityIdentifier();
            Map<String, BigDecimal> values = new HashMap<>();

            for (StandardizedFact fact : company.getFacts()) {
                // Take first value for each concept (most recent period)
                if (!values.containsKey(fact.getStandardConcept())) {
                    values.put(fact.getStandardConcept(), fact.getValue());
                }
            }

            conceptsByCompany.put(companyId, values);
        }

        builder.conceptsByCompany(conceptsByCompany);

        // Find common concepts
        Set<String> commonConcepts = null;
        for (Map<String, BigDecimal> values : conceptsByCompany.values()) {
            if (commonConcepts == null) {
                commonConcepts = new HashSet<>(values.keySet());
            } else {
                commonConcepts.retainAll(values.keySet());
            }
        }
        builder.commonConcepts(new ArrayList<>(commonConcepts != null ? commonConcepts : Set.of()));

        return builder.build();
    }

    /**
     * Get all standard concept names.
     */
    public Set<String> getStandardConcepts() {
        return STANDARD_MAPPINGS.keySet();
    }

    /**
     * Get mapping for a standard concept.
     */
    public ConceptMapping getMapping(String standardConcept) {
        return STANDARD_MAPPINGS.get(standardConcept);
    }

    // Data classes

    @Data
    @Builder
    public static class ConceptMapping {
        private String standardConcept;
        private ConceptCategory category;
        private List<String> alternateNames;
        private SignConvention signConvention;
        private String description;
    }

    @Data
    @Builder
    public static class StandardizedData {
        private XbrlInstance originalInstance;
        private List<StandardizedFact> facts;
        private List<String> unmappedConcepts;

        public Map<String, BigDecimal> getLatestValues() {
            return facts.stream()
                    .collect(Collectors.toMap(
                            StandardizedFact::getStandardConcept,
                            StandardizedFact::getValue,
                            (a, b) -> a  // Keep first
                    ));
        }
    }

    @Data
    @Builder
    public static class StandardizedFact {
        private String standardConcept;
        private String originalConcept;
        private BigDecimal value;
        private BigDecimal originalValue;
        private String contextRef;
        private ConceptCategory category;
        private String description;
    }

    @Data
    @Builder
    public static class ComparisonResult {
        private Map<String, Map<String, BigDecimal>> conceptsByCompany;
        private List<String> commonConcepts;
    }

    public enum ConceptCategory {
        INCOME_STATEMENT,
        BALANCE_SHEET,
        CASH_FLOW,
        SHARES,
        OTHER
    }

    public enum SignConvention {
        POSITIVE,           // Always positive
        POSITIVE_INCOME,    // Positive = income, Negative = loss
        NEGATIVE_EXPENSE    // Expenses shown as negative
    }
}
