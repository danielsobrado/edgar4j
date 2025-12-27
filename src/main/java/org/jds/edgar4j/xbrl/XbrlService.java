package org.jds.edgar4j.xbrl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.analysis.MultiPeriodAnalyzer;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.parser.StreamingXbrlParser;
import org.jds.edgar4j.xbrl.parser.XbrlPackageHandler;
import org.jds.edgar4j.xbrl.parser.XbrlParser;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.jds.edgar4j.xbrl.statement.StatementReconstructor;
import org.jds.edgar4j.xbrl.taxonomy.TaxonomyResolver;
import org.jds.edgar4j.xbrl.validation.CalculationValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main service for XBRL parsing and analysis.
 * Provides high-level API for parsing SEC filings.
 *
 * KEY DIFFERENTIATORS from other XBRL parsers:
 * - Financial statement reconstruction (not just fact extraction)
 * - Concept standardization for cross-company comparison
 * - Multi-period time series analysis with trend detection
 * - SEC-specific metadata extraction
 * - Streaming parser for large filings
 * - Calculation validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XbrlService {

    private final XbrlParser xbrlParser;
    private final XbrlPackageHandler packageHandler;
    private final CalculationValidator calculationValidator;
    private final TaxonomyResolver taxonomyResolver;
    private final StatementReconstructor statementReconstructor;
    private final ConceptStandardizer conceptStandardizer;
    private final MultiPeriodAnalyzer multiPeriodAnalyzer;
    private final SecFilingExtractor secFilingExtractor;
    private final StreamingXbrlParser streamingParser;
    private final WebClient.Builder webClientBuilder;

    /**
     * Parse XBRL from raw content.
     */
    public XbrlInstance parse(byte[] content, String uri) {
        return parse(content, uri, null);
    }

    /**
     * Parse XBRL with specified content type.
     */
    public XbrlInstance parse(byte[] content, String uri, String contentType) {
        // Check if it's a ZIP package
        if (isZipContent(content) || (uri != null && uri.toLowerCase().endsWith(".zip"))) {
            XbrlPackageHandler.PackageResult result = packageHandler.parsePackage(content, uri);
            return result.getPrimaryInstance();
        }

        return xbrlParser.parse(content, uri, contentType);
    }

    /**
     * Parse XBRL from URL.
     */
    public Mono<XbrlInstance> parseFromUrl(String url) {
        log.info("Fetching XBRL from: {}", url);

        WebClient webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofMinutes(2))
                .flatMap(content -> {
                    XbrlInstance instance = parse(content, url);
                    if (instance == null) {
                        return Mono.error(new IllegalStateException("No XBRL instance parsed from " + url));
                    }
                    return Mono.just(instance);
                });
    }

    /**
     * Parse XBRL package from URL.
     */
    public Mono<XbrlPackageHandler.PackageResult> parsePackageFromUrl(String url) {
        log.info("Fetching XBRL package from: {}", url);

        WebClient webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                .build();

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofMinutes(5))
                .flatMap(content -> {
                    XbrlPackageHandler.PackageResult result = packageHandler.parsePackage(content, url);
                    if (result == null) {
                        return Mono.error(new IllegalStateException("No XBRL package parsed from " + url));
                    }
                    return Mono.just(result);
                });
    }

    /**
     * Validate calculations in an XBRL instance.
     */
    public CalculationValidator.ValidationResult validateCalculations(XbrlInstance instance) {
        return calculationValidator.validate(instance);
    }

    /**
     * Get key financial facts from an XBRL instance.
     */
    public Map<String, BigDecimal> getKeyFinancials(XbrlInstance instance) {
        Map<String, BigDecimal> financials = new LinkedHashMap<>();

        // Define key concepts to extract
        List<String> keyConcepts = Arrays.asList(
                // Balance Sheet
                "Assets",
                "AssetsCurrent",
                "AssetsNoncurrent",
                "Liabilities",
                "LiabilitiesCurrent",
                "LiabilitiesNoncurrent",
                "StockholdersEquity",
                "LiabilitiesAndStockholdersEquity",
                "CashAndCashEquivalentsAtCarryingValue",

                // Income Statement
                "Revenues",
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "CostOfGoodsAndServicesSold",
                "GrossProfit",
                "OperatingIncomeLoss",
                "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest",
                "NetIncomeLoss",
                "EarningsPerShareBasic",
                "EarningsPerShareDiluted",

                // Cash Flow
                "NetCashProvidedByUsedInOperatingActivities",
                "NetCashProvidedByUsedInInvestingActivities",
                "NetCashProvidedByUsedInFinancingActivities"
        );

        // Get primary context (most recent period)
        var primaryContext = instance.getPrimaryContext();
        if (primaryContext == null && !instance.getContexts().isEmpty()) {
            primaryContext = instance.getContexts().values().iterator().next();
        }

        if (primaryContext != null) {
            String contextId = primaryContext.getId();
            List<XbrlFact> contextFacts = instance.getFactsByContextId(contextId);

            Map<String, XbrlFact> factLookup = contextFacts.stream()
                    .collect(Collectors.toMap(
                            XbrlFact::getConceptLocalName,
                            f -> f,
                            (a, b) -> a
                    ));

            for (String concept : keyConcepts) {
                XbrlFact fact = factLookup.get(concept);
                if (fact != null && fact.getNormalizedValue() != null) {
                    financials.put(concept, fact.getNormalizedValue());
                }
            }
        }

        return financials;
    }

    /**
     * Get all facts for a specific concept.
     */
    public List<XbrlFact> getFactsByConcept(XbrlInstance instance, String conceptName) {
        return instance.getFactsByConceptName(conceptName);
    }

    /**
     * Search facts by partial concept name.
     */
    public List<XbrlFact> searchFacts(XbrlInstance instance, String searchTerm) {
        String lowerSearch = searchTerm.toLowerCase();
        return instance.getFacts().stream()
                .filter(f -> f.getConceptLocalName().toLowerCase().contains(lowerSearch)
                        || (f.getConceptPrefix() != null &&
                        f.getConceptPrefix().toLowerCase().contains(lowerSearch)))
                .collect(Collectors.toList());
    }

    /**
     * Get a human-readable label for a concept.
     */
    public String getConceptLabel(String namespace, String localName) {
        return taxonomyResolver.getLabel(namespace, localName, "en");
    }

    /**
     * Get summary statistics for an XBRL instance.
     */
    public Map<String, Object> getSummary(XbrlInstance instance) {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("documentUri", instance.getDocumentUri());
        summary.put("format", instance.getFormat());
        summary.put("parseTime", instance.getParseTime());
        summary.put("entityIdentifier", instance.getEntityIdentifier());

        // Counts
        summary.put("totalFacts", instance.getFacts().size());
        summary.put("totalContexts", instance.getContexts().size());
        summary.put("totalUnits", instance.getUnits().size());

        // Fact type breakdown
        Map<XbrlFact.FactType, Long> factsByType = instance.getFacts().stream()
                .collect(Collectors.groupingBy(XbrlFact::getFactType, Collectors.counting()));
        summary.put("factsByType", factsByType);

        // Namespace breakdown
        Map<String, Long> factsByNamespace = instance.getFacts().stream()
                .filter(f -> f.getConceptPrefix() != null)
                .collect(Collectors.groupingBy(XbrlFact::getConceptPrefix, Collectors.counting()));
        summary.put("factsByNamespace", factsByNamespace);

        // Parse result
        var parseResult = instance.getParseResult();
        if (parseResult != null) {
            summary.put("parseTimeMs", parseResult.getParseTimeMs());
            summary.put("successRate", parseResult.getSuccessRate());
            summary.put("nestedFactsExtracted", parseResult.getNestedFactsExtracted());
            summary.put("warnings", parseResult.getWarnings().size());
            summary.put("errors", parseResult.getErrors().size());
        }

        return summary;
    }

    /**
     * Export facts to a flat list suitable for analysis.
     */
    public List<Map<String, Object>> exportFacts(XbrlInstance instance) {
        List<Map<String, Object>> exported = new ArrayList<>();

        for (XbrlFact fact : instance.getFacts()) {
            Map<String, Object> row = new LinkedHashMap<>();

            // Concept info
            row.put("concept", fact.getConceptLocalName());
            row.put("namespace", fact.getConceptPrefix());
            row.put("fullNamespace", fact.getConceptNamespace());

            // Value
            if (fact.isNumeric()) {
                row.put("value", fact.getNormalizedValue());
                row.put("rawValue", fact.getRawValue());
            } else {
                row.put("value", fact.getStringValue());
            }

            row.put("factType", fact.getFactType());
            row.put("isNil", fact.isNil());

            // Context info
            row.put("contextRef", fact.getContextRef());
            var context = instance.getContexts().get(fact.getContextRef());
            if (context != null) {
                row.put("contextDescription", context.getDescription());
                if (context.getPeriod() != null) {
                    row.put("periodEnd", context.getPeriod().getEndDate());
                }
            }

            // Unit info
            row.put("unitRef", fact.getUnitRef());
            var unit = instance.getUnits().get(fact.getUnitRef());
            if (unit != null) {
                row.put("unitDisplay", unit.getDisplayName());
            }

            // Precision
            row.put("decimals", fact.getDecimals());
            row.put("scale", fact.getScale());

            exported.add(row);
        }

        return exported;
    }

    /**
     * Get taxonomy cache statistics.
     */
    public Map<String, Long> getCacheStats() {
        return taxonomyResolver.getCacheStats();
    }

    /**
     * Clear all caches.
     */
    public void clearCaches() {
        taxonomyResolver.clearCaches();
    }

    /**
     * Check if content is a ZIP file.
     */
    private boolean isZipContent(byte[] content) {
        if (content == null || content.length < 4) return false;
        // ZIP magic number: PK\x03\x04
        return content[0] == 0x50 && content[1] == 0x4B
                && content[2] == 0x03 && content[3] == 0x04;
    }

    // ========================================================================
    // DIFFERENTIATING FEATURES - What sets this parser apart
    // ========================================================================

    /**
     * Reconstruct financial statements from XBRL data.
     * This produces actual formatted statements, not just raw facts.
     */
    public StatementReconstructor.FinancialStatements reconstructStatements(XbrlInstance instance) {
        return statementReconstructor.reconstruct(instance);
    }

    /**
     * Get a specific financial statement.
     */
    public StatementReconstructor.FinancialStatement getBalanceSheet(XbrlInstance instance) {
        var statements = statementReconstructor.reconstruct(instance);
        return statements.getBalanceSheet();
    }

    public StatementReconstructor.FinancialStatement getIncomeStatement(XbrlInstance instance) {
        var statements = statementReconstructor.reconstruct(instance);
        return statements.getIncomeStatement();
    }

    public StatementReconstructor.FinancialStatement getCashFlowStatement(XbrlInstance instance) {
        var statements = statementReconstructor.reconstruct(instance);
        return statements.getCashFlowStatement();
    }

    /**
     * Standardize facts for cross-company comparison.
     * Maps company-specific extensions to standard concepts.
     */
    public ConceptStandardizer.StandardizedData standardize(XbrlInstance instance) {
        return conceptStandardizer.standardize(instance);
    }

    /**
     * Compare standardized data across multiple companies.
     */
    public ConceptStandardizer.ComparisonResult compareCompanies(List<XbrlInstance> instances) {
        List<ConceptStandardizer.StandardizedData> standardizedList = instances.stream()
                .map(conceptStandardizer::standardize)
                .collect(Collectors.toList());
        return conceptStandardizer.compare(standardizedList);
    }

    /**
     * Stitch multiple filings together for time series analysis.
     */
    public MultiPeriodAnalyzer.StitchedTimeSeries stitchFilings(List<XbrlInstance> instances) {
        return multiPeriodAnalyzer.stitch(instances);
    }

    /**
     * Analyze growth for a specific concept across periods.
     */
    public MultiPeriodAnalyzer.GrowthAnalysis analyzeGrowth(
            MultiPeriodAnalyzer.StitchedTimeSeries timeSeries, String concept) {
        return multiPeriodAnalyzer.analyzeGrowth(timeSeries, concept);
    }

    /**
     * Detect anomalies in time series data.
     */
    public List<MultiPeriodAnalyzer.Anomaly> detectAnomalies(
            MultiPeriodAnalyzer.StitchedTimeSeries timeSeries) {
        return multiPeriodAnalyzer.detectAnomalies(timeSeries);
    }

    /**
     * Calculate financial ratios over time.
     */
    public Map<String, Map<LocalDate, BigDecimal>> calculateRatios(
            MultiPeriodAnalyzer.StitchedTimeSeries timeSeries) {
        return multiPeriodAnalyzer.calculateRatios(timeSeries);
    }

    /**
     * Extract SEC-specific filing metadata.
     */
    public SecFilingExtractor.SecFilingMetadata extractSecMetadata(XbrlInstance instance) {
        return secFilingExtractor.extract(instance);
    }

    /**
     * Parse large XBRL files as a stream (memory efficient).
     */
    public Flux<XbrlFact> parseAsStream(InputStream inputStream) {
        return streamingParser.parseAsStream(inputStream);
    }

    /**
     * Parse with callback for each fact (most memory efficient).
     */
    public StreamingXbrlParser.StreamingParseResult parseWithCallback(
            byte[] content, java.util.function.Consumer<XbrlFact> factConsumer) {
        return streamingParser.parseWithCallback(
                new ByteArrayInputStream(content), factConsumer);
    }

    /**
     * Count facts without full parsing (very fast).
     */
    public Mono<Long> countFacts(byte[] content) {
        return streamingParser.countFacts(new ByteArrayInputStream(content));
    }

    /**
     * Get comprehensive filing analysis including all features.
     */
    public Map<String, Object> getComprehensiveAnalysis(XbrlInstance instance) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        // Basic summary
        analysis.put("summary", getSummary(instance));

        // SEC metadata
        analysis.put("secMetadata", extractSecMetadata(instance));

        // Key financials
        analysis.put("keyFinancials", getKeyFinancials(instance));

        // Standardized data
        var standardized = standardize(instance);
        analysis.put("standardizedValues", standardized.getLatestValues());
        analysis.put("unmappedConcepts", standardized.getUnmappedConcepts().size());

        // Calculation validation
        var validation = validateCalculations(instance);
        analysis.put("calculationValidation", Map.of(
                "totalChecks", validation.getTotalChecks(),
                "validCalculations", validation.getValidCalculations(),
                "errors", validation.getErrors().size(),
                "isValid", validation.isValid()
        ));

        return analysis;
    }
}
