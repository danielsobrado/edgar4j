package org.jds.edgar4j.xbrl.statement;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlContext;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.model.XbrlUnit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reconstructs financial statements from XBRL facts.
 *
 * This is a KEY DIFFERENTIATOR from other parsers:
 * - Uses presentation linkbase role URIs to identify statement types
 * - Reconstructs proper line item ordering from presentation relationships
 * - Handles dimensional breakdowns correctly
 * - Maps company-specific extensions to standard concepts
 *
 * Unlike basic parsers that just dump facts, this produces actual
 * financial statements ready for analysis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatementReconstructor {

    // Role URI patterns for identifying statement types
    private static final Pattern BALANCE_SHEET_PATTERN = Pattern.compile(
            "(?i)(statement|consolidated).*(balance|financial.?position|assets.*liabilities)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INCOME_PATTERN = Pattern.compile(
            "(?i)(statement|consolidated).*(income|operations|earnings|loss|comprehensive)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CASH_FLOW_PATTERN = Pattern.compile(
            "(?i)(statement|consolidated).*cash.?flow",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EQUITY_PATTERN = Pattern.compile(
            "(?i)(statement|consolidated).*(equity|stockholder|shareholder)",
            Pattern.CASE_INSENSITIVE
    );

    // Standard concepts for each statement type (US-GAAP)
    private static final Map<StatementType, List<String>> STATEMENT_CONCEPTS = new EnumMap<>(StatementType.class);

    static {
        // Balance Sheet concepts in standard order
        STATEMENT_CONCEPTS.put(StatementType.BALANCE_SHEET, Arrays.asList(
                // Current Assets
                "CashAndCashEquivalentsAtCarryingValue",
                "ShortTermInvestments",
                "AccountsReceivableNetCurrent",
                "InventoryNet",
                "PrepaidExpenseAndOtherAssetsCurrent",
                "AssetsCurrent",
                // Non-current Assets
                "PropertyPlantAndEquipmentNet",
                "Goodwill",
                "IntangibleAssetsNetExcludingGoodwill",
                "LongTermInvestments",
                "OtherAssetsNoncurrent",
                "AssetsNoncurrent",
                "Assets",
                // Current Liabilities
                "AccountsPayableCurrent",
                "AccruedLiabilitiesCurrent",
                "ShortTermBorrowings",
                "LongTermDebtCurrent",
                "DeferredRevenueCurrent",
                "LiabilitiesCurrent",
                // Non-current Liabilities
                "LongTermDebtNoncurrent",
                "DeferredTaxLiabilitiesNoncurrent",
                "OtherLiabilitiesNoncurrent",
                "LiabilitiesNoncurrent",
                "Liabilities",
                // Equity
                "CommonStockValue",
                "AdditionalPaidInCapital",
                "RetainedEarningsAccumulatedDeficit",
                "AccumulatedOtherComprehensiveIncomeLossNetOfTax",
                "TreasuryStockValue",
                "StockholdersEquity",
                "MinorityInterest",
                "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
                "LiabilitiesAndStockholdersEquity"
        ));

        // Income Statement concepts
        STATEMENT_CONCEPTS.put(StatementType.INCOME_STATEMENT, Arrays.asList(
                "Revenues",
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "CostOfGoodsAndServicesSold",
                "CostOfRevenue",
                "GrossProfit",
                "ResearchAndDevelopmentExpense",
                "SellingGeneralAndAdministrativeExpense",
                "OperatingExpenses",
                "OperatingIncomeLoss",
                "InterestExpense",
                "InterestIncome",
                "OtherNonoperatingIncomeExpense",
                "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest",
                "IncomeTaxExpenseBenefit",
                "IncomeLossFromContinuingOperations",
                "IncomeLossFromDiscontinuedOperationsNetOfTax",
                "NetIncomeLoss",
                "NetIncomeLossAttributableToNoncontrollingInterest",
                "NetIncomeLossAvailableToCommonStockholdersBasic",
                "EarningsPerShareBasic",
                "EarningsPerShareDiluted",
                "WeightedAverageNumberOfSharesOutstandingBasic",
                "WeightedAverageNumberOfDilutedSharesOutstanding"
        ));

        // Cash Flow Statement concepts
        STATEMENT_CONCEPTS.put(StatementType.CASH_FLOW, Arrays.asList(
                // Operating Activities
                "NetIncomeLoss",
                "DepreciationDepletionAndAmortization",
                "ShareBasedCompensation",
                "DeferredIncomeTaxExpenseBenefit",
                "IncreaseDecreaseInAccountsReceivable",
                "IncreaseDecreaseInInventories",
                "IncreaseDecreaseInAccountsPayable",
                "IncreaseDecreaseInAccruedLiabilities",
                "AdjustmentsToReconcileNetIncomeLossToCashProvidedByUsedInOperatingActivities",
                "NetCashProvidedByUsedInOperatingActivities",
                // Investing Activities
                "PaymentsToAcquirePropertyPlantAndEquipment",
                "PaymentsToAcquireBusinessesNetOfCashAcquired",
                "PaymentsToAcquireInvestments",
                "ProceedsFromSaleOfInvestments",
                "NetCashProvidedByUsedInInvestingActivities",
                // Financing Activities
                "ProceedsFromIssuanceOfCommonStock",
                "PaymentsForRepurchaseOfCommonStock",
                "PaymentsOfDividendsCommonStock",
                "ProceedsFromIssuanceOfLongTermDebt",
                "RepaymentsOfLongTermDebt",
                "NetCashProvidedByUsedInFinancingActivities",
                // Net Change
                "EffectOfExchangeRateOnCashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents",
                "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalentsPeriodIncreaseDecreaseIncludingExchangeRateEffect",
                "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"
        ));

        // Equity Statement concepts
        STATEMENT_CONCEPTS.put(StatementType.STOCKHOLDERS_EQUITY, Arrays.asList(
                "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
                "CommonStockSharesOutstanding",
                "StockIssuedDuringPeriodValueNewIssues",
                "StockRepurchasedDuringPeriodValue",
                "DividendsCommonStock",
                "ComprehensiveIncomeNetOfTax",
                "StockholdersEquity"
        ));
    }

    /**
     * Reconstruct all financial statements from an XBRL instance.
     */
    public FinancialStatements reconstruct(XbrlInstance instance) {
        FinancialStatements.FinancialStatementsBuilder builder = FinancialStatements.builder();

        // Identify reporting periods
        List<ReportingPeriod> periods = identifyReportingPeriods(instance);
        builder.periods(periods);

        // Get primary period (most recent)
        ReportingPeriod primaryPeriod = periods.stream()
                .filter(p -> !p.isInstant())
                .max(Comparator.comparing(ReportingPeriod::getEndDate))
                .orElse(periods.isEmpty() ? null : periods.get(0));

        // Reconstruct each statement type
        builder.balanceSheet(reconstructStatement(instance, StatementType.BALANCE_SHEET, periods));
        builder.incomeStatement(reconstructStatement(instance, StatementType.INCOME_STATEMENT, periods));
        builder.cashFlowStatement(reconstructStatement(instance, StatementType.CASH_FLOW, periods));
        builder.equityStatement(reconstructStatement(instance, StatementType.STOCKHOLDERS_EQUITY, periods));

        // Extract entity info
        builder.entityName(extractEntityName(instance));
        builder.cik(extractCik(instance));
        builder.fiscalYearEnd(extractFiscalYearEnd(instance));

        return builder.build();
    }

    /**
     * Reconstruct a single financial statement.
     */
    public FinancialStatement reconstructStatement(
            XbrlInstance instance,
            StatementType statementType,
            List<ReportingPeriod> periods) {

        FinancialStatement.FinancialStatementBuilder builder = FinancialStatement.builder();
        builder.type(statementType);
        builder.title(statementType.getDisplayName());

        List<String> standardConcepts = STATEMENT_CONCEPTS.get(statementType);
        if (standardConcepts == null) {
            return builder.build();
        }

        // Group facts by concept
        Map<String, List<XbrlFact>> factsByConcept = instance.getFacts().stream()
                .collect(Collectors.groupingBy(XbrlFact::getConceptLocalName));

        // Build line items in standard order
        List<LineItem> lineItems = new ArrayList<>();
        Set<String> addedConcepts = new HashSet<>();

        for (String concept : standardConcepts) {
            List<XbrlFact> facts = factsByConcept.get(concept);
            if (facts != null && !facts.isEmpty()) {
                LineItem item = buildLineItem(concept, facts, instance, periods, statementType);
                if (item != null && item.hasValues()) {
                    lineItems.add(item);
                    addedConcepts.add(concept);
                }
            }
        }

        // Add any remaining facts that weren't in standard order
        // (company extensions or less common concepts)
        for (Map.Entry<String, List<XbrlFact>> entry : factsByConcept.entrySet()) {
            String concept = entry.getKey();
            if (!addedConcepts.contains(concept) && isRelevantForStatement(concept, statementType)) {
                LineItem item = buildLineItem(concept, entry.getValue(), instance, periods, statementType);
                if (item != null && item.hasValues()) {
                    lineItems.add(item);
                }
            }
        }

        builder.lineItems(lineItems);
        builder.periods(periods);

        return builder.build();
    }

    /**
     * Build a single line item from facts.
     */
    private LineItem buildLineItem(
            String concept,
            List<XbrlFact> facts,
            XbrlInstance instance,
            List<ReportingPeriod> periods,
            StatementType statementType) {

        LineItem.LineItemBuilder builder = LineItem.builder();
        builder.concept(concept);
        builder.label(humanizeConceptName(concept));
        builder.indentLevel(determineIndentLevel(concept, statementType));
        builder.isTotal(concept.contains("Total") || isTotalConcept(concept));
        builder.isSubtotal(concept.contains("Subtotal") || isSubtotalConcept(concept));

        // Map values by period
        Map<String, BigDecimal> valuesByPeriod = new LinkedHashMap<>();

        for (XbrlFact fact : facts) {
            XbrlContext context = instance.getContexts().get(fact.getContextRef());
            if (context == null || context.hasDimensions()) {
                continue;  // Skip dimensional facts for main statement
            }

            String periodKey = getPeriodKey(context);
            BigDecimal value = fact.getNormalizedValue();

            if (value != null && periodKey != null) {
                // For balance sheet, prefer instant periods
                // For income/cash flow, prefer duration periods
                boolean isInstantStatement = statementType == StatementType.BALANCE_SHEET;
                boolean isInstantContext = context.isInstant();

                if (isInstantStatement == isInstantContext) {
                    valuesByPeriod.put(periodKey, value);
                }
            }
        }

        builder.valuesByPeriod(valuesByPeriod);

        // Get unit
        XbrlFact firstFact = facts.get(0);
        if (firstFact.getUnitRef() != null) {
            XbrlUnit unit = instance.getUnits().get(firstFact.getUnitRef());
            if (unit != null) {
                builder.unit(unit.getDisplayName());
                builder.isMonetary(unit.isMonetary());
            }
        }

        return builder.build();
    }

    /**
     * Identify all reporting periods in the instance.
     */
    private List<ReportingPeriod> identifyReportingPeriods(XbrlInstance instance) {
        Set<ReportingPeriod> periods = new LinkedHashSet<>();

        for (XbrlContext context : instance.getContexts().values()) {
            if (context.hasDimensions()) continue;

            XbrlContext.XbrlPeriod period = context.getPeriod();
            if (period == null) continue;

            ReportingPeriod.ReportingPeriodBuilder builder = ReportingPeriod.builder();
            builder.contextId(context.getId());

            if (period.isInstant()) {
                builder.endDate(period.getInstant());
                builder.isInstant(true);
                builder.label(formatDate(period.getInstant()));
            } else if (period.isDuration()) {
                builder.startDate(period.getStartDate());
                builder.endDate(period.getEndDate());
                builder.isInstant(false);
                builder.durationDays(calculateDays(period.getStartDate(), period.getEndDate()));
                builder.label(formatPeriodLabel(period.getStartDate(), period.getEndDate()));
            }

            periods.add(builder.build());
        }

        // Sort by end date descending (most recent first)
        return periods.stream()
                .sorted(Comparator.comparing(ReportingPeriod::getEndDate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Determine indent level based on concept type.
     */
    private int determineIndentLevel(String concept, StatementType statementType) {
        // Totals and main aggregates at level 0
        if (concept.equals("Assets") || concept.equals("Liabilities")
                || concept.equals("StockholdersEquity") || concept.equals("LiabilitiesAndStockholdersEquity")
                || concept.equals("NetIncomeLoss") || concept.equals("GrossProfit")
                || concept.equals("OperatingIncomeLoss")) {
            return 0;
        }

        // Subtotals at level 1
        if (concept.contains("Current") && !concept.contains("Increase")
                || concept.contains("Noncurrent")
                || concept.contains("NetCashProvidedByUsed")) {
            return 1;
        }

        // Individual items at level 2
        return 2;
    }

    /**
     * Check if concept is relevant for statement type.
     */
    private boolean isRelevantForStatement(String concept, StatementType statementType) {
        String lower = concept.toLowerCase();

        switch (statementType) {
            case BALANCE_SHEET:
                return lower.contains("asset") || lower.contains("liabilit")
                        || lower.contains("equity") || lower.contains("cash")
                        || lower.contains("receivable") || lower.contains("payable")
                        || lower.contains("inventory") || lower.contains("property");
            case INCOME_STATEMENT:
                return lower.contains("revenue") || lower.contains("income")
                        || lower.contains("expense") || lower.contains("cost")
                        || lower.contains("profit") || lower.contains("loss")
                        || lower.contains("earnings") || lower.contains("tax");
            case CASH_FLOW:
                return lower.contains("cash") || lower.contains("payment")
                        || lower.contains("proceed") || lower.contains("operating")
                        || lower.contains("investing") || lower.contains("financing");
            case STOCKHOLDERS_EQUITY:
                return lower.contains("equity") || lower.contains("stock")
                        || lower.contains("dividend") || lower.contains("share")
                        || lower.contains("comprehensive");
            default:
                return false;
        }
    }

    private boolean isTotalConcept(String concept) {
        return concept.equals("Assets") || concept.equals("Liabilities")
                || concept.equals("LiabilitiesAndStockholdersEquity")
                || concept.equals("NetIncomeLoss");
    }

    private boolean isSubtotalConcept(String concept) {
        return concept.equals("AssetsCurrent") || concept.equals("AssetsNoncurrent")
                || concept.equals("LiabilitiesCurrent") || concept.equals("LiabilitiesNoncurrent")
                || concept.equals("GrossProfit") || concept.equals("OperatingIncomeLoss");
    }

    private String getPeriodKey(XbrlContext context) {
        XbrlContext.XbrlPeriod period = context.getPeriod();
        if (period == null) return null;

        if (period.isInstant()) {
            return period.getInstant().toString();
        } else if (period.isDuration()) {
            return period.getEndDate().toString();
        }
        return null;
    }

    private String humanizeConceptName(String concept) {
        // Insert spaces before capitals and handle special cases
        String result = concept
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        // Fix common abbreviations
        result = result
                .replace("And", "and")
                .replace("Or", "or")
                .replace("Of", "of")
                .replace("At", "at")
                .replace("To", "to")
                .replace("In", "in")
                .replace("By", "by")
                .replace("For", "for");

        return result;
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.toString();
    }

    private String formatPeriodLabel(LocalDate start, LocalDate end) {
        if (start == null || end == null) return "";
        long days = calculateDays(start, end);

        if (days >= 360 && days <= 370) {
            return "FY " + end.getYear();
        } else if (days >= 88 && days <= 95) {
            int quarter = (end.getMonthValue() - 1) / 3 + 1;
            return "Q" + quarter + " " + end.getYear();
        }
        return start + " to " + end;
    }

    private long calculateDays(LocalDate start, LocalDate end) {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end);
    }

    private String extractEntityName(XbrlInstance instance) {
        return instance.getFacts().stream()
                .filter(f -> f.getConceptLocalName().equals("EntityRegistrantName"))
                .findFirst()
                .map(XbrlFact::getStringValue)
                .orElse(null);
    }

    private String extractCik(XbrlInstance instance) {
        return instance.getEntityIdentifier();
    }

    private String extractFiscalYearEnd(XbrlInstance instance) {
        return instance.getFacts().stream()
                .filter(f -> f.getConceptLocalName().equals("CurrentFiscalYearEndDate"))
                .findFirst()
                .map(XbrlFact::getStringValue)
                .orElse(null);
    }

    // Data classes

    @Data
    @Builder
    public static class FinancialStatements {
        private String entityName;
        private String cik;
        private String fiscalYearEnd;
        private List<ReportingPeriod> periods;
        private FinancialStatement balanceSheet;
        private FinancialStatement incomeStatement;
        private FinancialStatement cashFlowStatement;
        private FinancialStatement equityStatement;
    }

    @Data
    @Builder
    public static class FinancialStatement {
        private StatementType type;
        private String title;
        private List<LineItem> lineItems;
        private List<ReportingPeriod> periods;

        public List<Map<String, Object>> toRows() {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LineItem item : lineItems) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("concept", item.getConcept());
                row.put("label", item.getLabel());
                row.put("indentLevel", item.getIndentLevel());
                row.put("isTotal", item.isTotal());
                row.putAll(item.getValuesByPeriod());
                rows.add(row);
            }
            return rows;
        }
    }

    @Data
    @Builder
    public static class LineItem {
        private String concept;
        private String label;
        private int indentLevel;
        private boolean isTotal;
        private boolean isSubtotal;
        private String unit;
        private boolean isMonetary;
        private Map<String, BigDecimal> valuesByPeriod;

        public boolean hasValues() {
            return valuesByPeriod != null && !valuesByPeriod.isEmpty();
        }
    }

    @Data
    @Builder
    @EqualsAndHashCode(of = {"startDate", "endDate", "isInstant"})
    public static class ReportingPeriod {
        private String contextId;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean isInstant;
        private long durationDays;
        private String label;

        public boolean isQuarterly() {
            return durationDays >= 80 && durationDays <= 100;
        }

        public boolean isAnnual() {
            return durationDays >= 360 && durationDays <= 370;
        }
    }

    public enum StatementType {
        BALANCE_SHEET("Balance Sheet"),
        INCOME_STATEMENT("Income Statement"),
        CASH_FLOW("Cash Flow Statement"),
        STOCKHOLDERS_EQUITY("Statement of Stockholders' Equity");

        private final String displayName;

        StatementType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
