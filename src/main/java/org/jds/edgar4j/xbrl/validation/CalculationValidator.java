package org.jds.edgar4j.xbrl.validation;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlContext;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.taxonomy.TaxonomyResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates XBRL calculation relationships.
 * Checks that parent totals equal the sum of their children.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculationValidator {

    private final TaxonomyResolver taxonomyResolver;

    // Common calculation relationships (parent -> children with weights)
    // Positive weight = add, Negative weight = subtract
    private static final Map<String, List<CalcComponent>> KNOWN_CALCULATIONS = new HashMap<>();

    static {
        // Assets = Current Assets + Noncurrent Assets
        KNOWN_CALCULATIONS.put("Assets", Arrays.asList(
                new CalcComponent("AssetsCurrent", BigDecimal.ONE),
                new CalcComponent("AssetsNoncurrent", BigDecimal.ONE)
        ));

        // Liabilities = Current Liabilities + Noncurrent Liabilities
        KNOWN_CALCULATIONS.put("Liabilities", Arrays.asList(
                new CalcComponent("LiabilitiesCurrent", BigDecimal.ONE),
                new CalcComponent("LiabilitiesNoncurrent", BigDecimal.ONE)
        ));

        // Liabilities and Equity = Liabilities + Equity
        KNOWN_CALCULATIONS.put("LiabilitiesAndStockholdersEquity", Arrays.asList(
                new CalcComponent("Liabilities", BigDecimal.ONE),
                new CalcComponent("StockholdersEquity", BigDecimal.ONE),
                new CalcComponent("StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest", BigDecimal.ONE)
        ));

        // Net Income calculations
        KNOWN_CALCULATIONS.put("NetIncomeLoss", Arrays.asList(
                new CalcComponent("IncomeLossFromContinuingOperations", BigDecimal.ONE),
                new CalcComponent("IncomeLossFromDiscontinuedOperationsNetOfTax", BigDecimal.ONE)
        ));

        // Comprehensive Income
        KNOWN_CALCULATIONS.put("ComprehensiveIncomeNetOfTax", Arrays.asList(
                new CalcComponent("NetIncomeLoss", BigDecimal.ONE),
                new CalcComponent("OtherComprehensiveIncomeLossNetOfTax", BigDecimal.ONE)
        ));

        // Cash Flow from Operations
        KNOWN_CALCULATIONS.put("NetCashProvidedByUsedInOperatingActivities", Arrays.asList(
                new CalcComponent("NetIncomeLoss", BigDecimal.ONE),
                new CalcComponent("AdjustmentsToReconcileNetIncomeLossToCashProvidedByUsedInOperatingActivities", BigDecimal.ONE)
        ));

        // Total Cash Flow
        KNOWN_CALCULATIONS.put("CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalentsPeriodIncreaseDecreaseIncludingExchangeRateEffect", Arrays.asList(
                new CalcComponent("NetCashProvidedByUsedInOperatingActivities", BigDecimal.ONE),
                new CalcComponent("NetCashProvidedByUsedInInvestingActivities", BigDecimal.ONE),
                new CalcComponent("NetCashProvidedByUsedInFinancingActivities", BigDecimal.ONE),
                new CalcComponent("EffectOfExchangeRateOnCashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents", BigDecimal.ONE)
        ));
    }

    // Tolerance for calculation differences (accounts for rounding)
    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("1.0");

    /**
     * Validate all calculations in an XBRL instance.
     */
    public ValidationResult validate(XbrlInstance instance) {
        ValidationResult result = new ValidationResult();

        // Group facts by context for validation
        Map<String, List<XbrlFact>> factsByContext = instance.getFacts().stream()
                .filter(f -> f.getContextRef() != null)
                .collect(Collectors.groupingBy(XbrlFact::getContextRef));

        // Validate each context
        for (Map.Entry<String, List<XbrlFact>> entry : factsByContext.entrySet()) {
            String contextId = entry.getKey();
            List<XbrlFact> contextFacts = entry.getValue();
            XbrlContext context = instance.getContexts().get(contextId);

            // Skip dimensional contexts for basic validation
            if (context != null && context.hasDimensions()) {
                continue;
            }

            // Build fact lookup by concept name
            Map<String, XbrlFact> factLookup = new HashMap<>();
            for (XbrlFact fact : contextFacts) {
                factLookup.put(fact.getConceptLocalName(), fact);
            }

            // Check known calculations
            for (Map.Entry<String, List<CalcComponent>> calc : KNOWN_CALCULATIONS.entrySet()) {
                String parentConcept = calc.getKey();
                List<CalcComponent> components = calc.getValue();

                XbrlFact parentFact = factLookup.get(parentConcept);
                if (parentFact == null || parentFact.getNormalizedValue() == null) {
                    continue;  // Parent not present in this context
                }

                BigDecimal expectedSum = BigDecimal.ZERO;
                List<String> presentComponents = new ArrayList<>();
                boolean hasAnyComponent = false;

                for (CalcComponent component : components) {
                    XbrlFact childFact = factLookup.get(component.conceptName);
                    if (childFact != null && childFact.getNormalizedValue() != null) {
                        hasAnyComponent = true;
                        BigDecimal contribution = childFact.getNormalizedValue()
                                .multiply(component.weight);
                        expectedSum = expectedSum.add(contribution);
                        presentComponents.add(component.conceptName);
                    }
                }

                if (!hasAnyComponent) {
                    continue;  // No components present
                }

                BigDecimal actualValue = parentFact.getNormalizedValue();
                BigDecimal difference = actualValue.subtract(expectedSum).abs();

                // Determine tolerance based on decimals
                BigDecimal tolerance = calculateTolerance(parentFact);

                if (difference.compareTo(tolerance) > 0) {
                    CalcError error = new CalcError();
                    error.setParentConcept(parentConcept);
                    error.setParentValue(actualValue);
                    error.setCalculatedSum(expectedSum);
                    error.setDifference(difference);
                    error.setTolerance(tolerance);
                    error.setContextId(contextId);
                    error.setComponents(presentComponents);

                    result.getErrors().add(error);
                    log.debug("Calculation error: {} = {} (expected {}), diff = {}",
                            parentConcept, actualValue, expectedSum, difference);
                } else {
                    result.setValidCalculations(result.getValidCalculations() + 1);
                }
            }
        }

        result.setTotalChecks(result.getValidCalculations() + result.getErrors().size());
        return result;
    }

    /**
     * Check if a specific calculation is valid.
     */
    public boolean checkCalculation(XbrlInstance instance, String parentConcept,
                                     List<String> childConcepts, String contextId) {
        XbrlContext context = instance.getContexts().get(contextId);
        List<XbrlFact> contextFacts = instance.getFactsByContextId(contextId);

        Map<String, XbrlFact> factLookup = contextFacts.stream()
                .collect(Collectors.toMap(
                        XbrlFact::getConceptLocalName,
                        f -> f,
                        (a, b) -> a  // Keep first if duplicate
                ));

        XbrlFact parentFact = factLookup.get(parentConcept);
        if (parentFact == null || parentFact.getNormalizedValue() == null) {
            return true;  // Can't validate if parent missing
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (String childConcept : childConcepts) {
            XbrlFact childFact = factLookup.get(childConcept);
            if (childFact != null && childFact.getNormalizedValue() != null) {
                sum = sum.add(childFact.getNormalizedValue());
            }
        }

        BigDecimal tolerance = calculateTolerance(parentFact);
        BigDecimal difference = parentFact.getNormalizedValue().subtract(sum).abs();

        return difference.compareTo(tolerance) <= 0;
    }

    /**
     * Find potential calculation inconsistencies.
     */
    public List<CalcSuggestion> findPotentialIssues(XbrlInstance instance) {
        List<CalcSuggestion> suggestions = new ArrayList<>();

        // Check Assets = Liabilities + Equity (fundamental equation)
        XbrlContext primaryContext = instance.getPrimaryContext();
        if (primaryContext != null) {
            List<XbrlFact> facts = instance.getFactsByContextId(primaryContext.getId());
            Map<String, BigDecimal> values = facts.stream()
                    .filter(f -> f.getNormalizedValue() != null)
                    .collect(Collectors.toMap(
                            XbrlFact::getConceptLocalName,
                            XbrlFact::getNormalizedValue,
                            (a, b) -> a
                    ));

            // Check balance sheet equation
            BigDecimal assets = values.get("Assets");
            BigDecimal liabEquity = values.get("LiabilitiesAndStockholdersEquity");

            if (assets != null && liabEquity != null) {
                BigDecimal diff = assets.subtract(liabEquity).abs();
                if (diff.compareTo(BigDecimal.ONE) > 0) {
                    CalcSuggestion suggestion = new CalcSuggestion();
                    suggestion.setType("BALANCE_SHEET_EQUATION");
                    suggestion.setDescription("Assets should equal Liabilities + Equity");
                    suggestion.setExpected(assets);
                    suggestion.setActual(liabEquity);
                    suggestions.add(suggestion);
                }
            }
        }

        return suggestions;
    }

    /**
     * Calculate appropriate tolerance based on decimals attribute.
     */
    private BigDecimal calculateTolerance(XbrlFact fact) {
        if (fact.getDecimals() == null) {
            return DEFAULT_TOLERANCE;
        }

        int decimals = fact.getDecimals();
        if (decimals >= 0) {
            // Tolerance is 0.5 at the specified decimal place
            return new BigDecimal("0.5").scaleByPowerOfTen(-decimals);
        } else {
            // Negative decimals means rounding to powers of 10
            // Tolerance is half the rounding unit
            return new BigDecimal("0.5").scaleByPowerOfTen(-decimals);
        }
    }

    /**
     * Calculation component with weight.
     */
    @Data
    public static class CalcComponent {
        private final String conceptName;
        private final BigDecimal weight;
    }

    /**
     * Validation result.
     */
    @Data
    public static class ValidationResult {
        private int totalChecks;
        private int validCalculations;
        private List<CalcError> errors = new ArrayList<>();

        public boolean isValid() {
            return errors.isEmpty();
        }

        public double getSuccessRate() {
            if (totalChecks == 0) return 1.0;
            return (double) validCalculations / totalChecks;
        }
    }

    /**
     * Calculation error details.
     */
    @Data
    public static class CalcError {
        private String parentConcept;
        private BigDecimal parentValue;
        private BigDecimal calculatedSum;
        private BigDecimal difference;
        private BigDecimal tolerance;
        private String contextId;
        private List<String> components;

        public String getDescription() {
            return String.format("%s = %s, but components sum to %s (diff: %s)",
                    parentConcept, parentValue, calculatedSum, difference);
        }
    }

    /**
     * Calculation suggestion.
     */
    @Data
    public static class CalcSuggestion {
        private String type;
        private String description;
        private BigDecimal expected;
        private BigDecimal actual;
    }
}
