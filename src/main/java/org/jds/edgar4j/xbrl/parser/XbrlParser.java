package org.jds.edgar4j.xbrl.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.*;
import org.jds.edgar4j.xbrl.taxonomy.TaxonomyResolver;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Main XBRL parser that handles both traditional XBRL and inline XBRL documents.
 *
 * Implements the high-performance parsing techniques for 99%+ success rate:
 * - Namespace fallback resolution
 * - Recursive nested fact extraction
 * - Error recovery parsing
 * - Encoding detection
 * - Multi-format support
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XbrlParser {

    private final ErrorRecoveryParser errorRecoveryParser;
    private final NamespaceResolver namespaceResolver;
    private final TaxonomyResolver taxonomyResolver;
    private final InlineXbrlFactExtractor inlineFactExtractor;
    private final ValueTransformer valueTransformer;

    /**
     * Parse XBRL content from bytes.
     */
    public XbrlInstance parse(byte[] content, String uri, String contentType) {
        long startTime = System.currentTimeMillis();

        // Reset namespace resolver for new document
        namespaceResolver.reset();

        ParseResult result = new ParseResult();
        XbrlInstance.XbrlInstanceBuilder instanceBuilder = XbrlInstance.builder()
                .documentUri(uri)
                .parseTime(LocalDateTime.now())
                .parseResult(result);

        try {
            // Parse document with error recovery
            ErrorRecoveryParser.ParsedDocument parsed = errorRecoveryParser.parse(content, contentType);
            Document doc = parsed.document();

            if (parsed.recoveryUsed()) {
                result.setMalformedXmlRecoveries(result.getMalformedXmlRecoveries() + 1);
            }

            // Determine format
            XbrlInstance.XbrlFormat format = detectFormat(parsed.documentType());
            instanceBuilder.format(format);

            // Extract namespace declarations from root
            extractNamespaces(doc, instanceBuilder);

            // Extract schema and linkbase references
            extractReferences(doc, instanceBuilder);

            // Parse based on format
            if (format == XbrlInstance.XbrlFormat.INLINE_XBRL) {
                parseInlineXbrl(doc, instanceBuilder, result);
            } else {
                parseTraditionalXbrl(doc, instanceBuilder, result);
            }

            result.setNamespaceRecoveries(namespaceResolver.getFallbacksUsed());

        } catch (Exception e) {
            log.error("Failed to parse XBRL document: {}", uri, e);
            result.addError("PARSE_FAILED", "Failed to parse document: " + e.getMessage(), e);
        }

        result.setParseTimeMs(System.currentTimeMillis() - startTime);
        XbrlInstance instance = instanceBuilder.build();
        instance.setParseResult(result);

        log.info("Parsed XBRL: {}", result.getSummary());
        return instance;
    }

    /**
     * Parse inline XBRL document.
     */
    private void parseInlineXbrl(Document doc, XbrlInstance.XbrlInstanceBuilder builder,
                                  ParseResult result) {
        // Extract contexts (may be in header or hidden div)
        Map<String, XbrlContext> contexts = extractContexts(doc);
        builder.contexts(contexts);
        result.setTotalContextsFound(contexts.size());

        // Extract units
        Map<String, XbrlUnit> units = extractUnits(doc);
        builder.units(units);
        result.setTotalUnitsFound(units.size());

        // Extract facts using specialized inline extractor
        List<XbrlFact> facts = inlineFactExtractor.extractFacts(doc, result);
        builder.facts(facts);

        // Extract entity info
        extractEntityInfo(contexts, builder);
    }

    /**
     * Parse traditional XBRL XML document.
     */
    private void parseTraditionalXbrl(Document doc, XbrlInstance.XbrlInstanceBuilder builder,
                                       ParseResult result) {
        // Find the xbrl root element
        Element xbrlRoot = findXbrlRoot(doc);
        if (xbrlRoot == null) {
            result.addError("NO_XBRL_ROOT", "Could not find XBRL root element", 0);
            return;
        }

        // Extract contexts
        Map<String, XbrlContext> contexts = extractContextsFromXbrl(xbrlRoot);
        builder.contexts(contexts);
        result.setTotalContextsFound(contexts.size());

        // Extract units
        Map<String, XbrlUnit> units = extractUnitsFromXbrl(xbrlRoot);
        builder.units(units);
        result.setTotalUnitsFound(units.size());

        // Extract facts
        List<XbrlFact> facts = extractFactsFromXbrl(xbrlRoot, result);
        builder.facts(facts);

        // Extract entity info
        extractEntityInfo(contexts, builder);
    }

    /**
     * Extract namespace declarations from document.
     */
    private void extractNamespaces(Document doc, XbrlInstance.XbrlInstanceBuilder builder) {
        Element root = doc.body();
        if (root == null) {
            root = doc.getElementsByTag("html").first();
        }
        if (root == null && !doc.children().isEmpty()) {
            root = doc.child(0);
        }

        Map<String, String> namespaces = new HashMap<>();

        if (root != null) {
            for (org.jsoup.nodes.Attribute attr : root.attributes()) {
                String key = attr.getKey();
                if (key.startsWith("xmlns:")) {
                    String prefix = key.substring(6);
                    namespaces.put(prefix, attr.getValue());
                    namespaceResolver.registerNamespace(prefix, attr.getValue());
                } else if (key.equals("xmlns")) {
                    namespaces.put("", attr.getValue());
                    namespaceResolver.registerNamespace("", attr.getValue());
                }
            }
        }

        // Also check for namespace declarations on xbrl element
        Elements xbrlElements = doc.select("xbrl, xbrli\\:xbrl");
        for (Element xbrl : xbrlElements) {
            for (org.jsoup.nodes.Attribute attr : xbrl.attributes()) {
                String key = attr.getKey();
                if (key.startsWith("xmlns:")) {
                    String prefix = key.substring(6);
                    namespaces.put(prefix, attr.getValue());
                    namespaceResolver.registerNamespace(prefix, attr.getValue());
                } else if (key.equals("xmlns")) {
                    namespaces.put("", attr.getValue());
                    namespaceResolver.registerNamespace("", attr.getValue());
                }
            }
        }

        builder.namespaces(namespaces);
    }

    /**
     * Extract schema and linkbase references.
     */
    private void extractReferences(Document doc, XbrlInstance.XbrlInstanceBuilder builder) {
        List<String> schemaRefs = new ArrayList<>();
        List<String> linkbaseRefs = new ArrayList<>();

        // Find schemaRef elements
        Elements schemaElements = doc.select(
                "link\\:schemaRef, schemaRef, ix\\:references schemaRef"
        );
        for (Element elem : schemaElements) {
            String href = elem.attr("xlink:href");
            if (href.isEmpty()) {
                href = elem.attr("href");
            }
            if (!href.isEmpty()) {
                schemaRefs.add(href);
            }
        }

        // Find linkbaseRef elements
        Elements linkbaseElements = doc.select(
                "link\\:linkbaseRef, linkbaseRef"
        );
        for (Element elem : linkbaseElements) {
            String href = elem.attr("xlink:href");
            if (href.isEmpty()) {
                href = elem.attr("href");
            }
            if (!href.isEmpty()) {
                linkbaseRefs.add(href);
            }
        }

        builder.schemaRefs(schemaRefs);
        builder.linkbaseRefs(linkbaseRefs);
    }

    /**
     * Extract contexts from iXBRL document.
     */
    private Map<String, XbrlContext> extractContexts(Document doc) {
        Map<String, XbrlContext> contexts = new HashMap<>();

        // iXBRL contexts can be in hidden div or ix:header
        Elements contextElements = doc.select(
                "xbrli\\:context, context, ix\\:resources context"
        );

        for (Element elem : contextElements) {
            try {
                XbrlContext context = parseContext(elem);
                if (context != null && context.getId() != null) {
                    contexts.put(context.getId(), context);
                }
            } catch (Exception e) {
                log.trace("Failed to parse context: {}", e.getMessage());
            }
        }

        return contexts;
    }

    /**
     * Extract contexts from traditional XBRL.
     */
    private Map<String, XbrlContext> extractContextsFromXbrl(Element xbrlRoot) {
        Map<String, XbrlContext> contexts = new HashMap<>();

        Elements contextElements = xbrlRoot.select("context, xbrli\\:context");
        for (Element elem : contextElements) {
            try {
                XbrlContext context = parseContext(elem);
                if (context != null && context.getId() != null) {
                    contexts.put(context.getId(), context);
                }
            } catch (Exception e) {
                log.trace("Failed to parse context: {}", e.getMessage());
            }
        }

        return contexts;
    }

    /**
     * Parse a single context element.
     */
    private XbrlContext parseContext(Element elem) {
        String id = elem.attr("id");
        if (id.isEmpty()) return null;

        XbrlContext.XbrlContextBuilder builder = XbrlContext.builder().id(id);

        // Parse entity
        Element entity = elem.selectFirst("entity, xbrli\\:entity");
        if (entity != null) {
            Element identifier = entity.selectFirst("identifier, xbrli\\:identifier");
            if (identifier != null) {
                builder.entityIdentifier(identifier.text());
                builder.entityScheme(identifier.attr("scheme"));
            }

            // Parse segment dimensions
            Element segment = entity.selectFirst("segment, xbrli\\:segment");
            if (segment != null) {
                List<XbrlContext.XbrlDimension> dimensions = parseDimensions(segment);
                builder.dimensions(dimensions);
            }
        }

        // Parse period
        Element period = elem.selectFirst("period, xbrli\\:period");
        if (period != null) {
            builder.period(parsePeriod(period));
        }

        // Parse scenario dimensions
        Element scenario = elem.selectFirst("scenario, xbrli\\:scenario");
        if (scenario != null) {
            List<XbrlContext.XbrlDimension> dimensions = parseDimensions(scenario);
            XbrlContext context = builder.build();
            context.getDimensions().addAll(dimensions);
            return context;
        }

        return builder.build();
    }

    /**
     * Parse period element.
     */
    private XbrlContext.XbrlPeriod parsePeriod(Element period) {
        XbrlContext.XbrlPeriod.XbrlPeriodBuilder builder = XbrlContext.XbrlPeriod.builder();

        Element instant = period.selectFirst("instant, xbrli\\:instant");
        if (instant != null) {
            builder.instant(parseDate(instant.text()));
            return builder.build();
        }

        Element forever = period.selectFirst("forever, xbrli\\:forever");
        if (forever != null) {
            builder.isForever(true);
            return builder.build();
        }

        Element startDate = period.selectFirst("startDate, xbrli\\:startDate");
        Element endDate = period.selectFirst("endDate, xbrli\\:endDate");

        if (startDate != null) {
            builder.startDate(parseDate(startDate.text()));
        }
        if (endDate != null) {
            builder.endDate(parseDate(endDate.text()));
        }

        return builder.build();
    }

    /**
     * Parse dimensions from segment or scenario.
     */
    private List<XbrlContext.XbrlDimension> parseDimensions(Element container) {
        List<XbrlContext.XbrlDimension> dimensions = new ArrayList<>();

        // Explicit dimensions
        Elements explicits = container.select(
                "xbrldi\\:explicitMember, explicitMember"
        );
        for (Element explicit : explicits) {
            String dimension = explicit.attr("dimension");
            String member = explicit.text();

            NamespaceResolver.QNameParts dimParts = namespaceResolver.parseQName(dimension);
            NamespaceResolver.QNameParts memParts = namespaceResolver.parseQName(member);

            if (dimParts != null && memParts != null) {
                dimensions.add(XbrlContext.XbrlDimension.builder()
                        .axisNamespace(dimParts.namespaceUri())
                        .axisLocalName(dimParts.localName())
                        .memberNamespace(memParts.namespaceUri())
                        .memberLocalName(memParts.localName())
                        .isTyped(false)
                        .build());
            }
        }

        // Typed dimensions
        Elements typeds = container.select(
                "xbrldi\\:typedMember, typedMember"
        );
        for (Element typed : typeds) {
            String dimension = typed.attr("dimension");
            NamespaceResolver.QNameParts dimParts = namespaceResolver.parseQName(dimension);

            if (dimParts != null) {
                dimensions.add(XbrlContext.XbrlDimension.builder()
                        .axisNamespace(dimParts.namespaceUri())
                        .axisLocalName(dimParts.localName())
                        .isTyped(true)
                        .typedValue(typed.text())
                        .build());
            }
        }

        return dimensions;
    }

    /**
     * Extract units from iXBRL document.
     */
    private Map<String, XbrlUnit> extractUnits(Document doc) {
        Map<String, XbrlUnit> units = new HashMap<>();

        Elements unitElements = doc.select(
                "xbrli\\:unit, unit, ix\\:resources unit"
        );

        for (Element elem : unitElements) {
            try {
                XbrlUnit unit = parseUnit(elem);
                if (unit != null && unit.getId() != null) {
                    units.put(unit.getId(), unit);
                }
            } catch (Exception e) {
                log.trace("Failed to parse unit: {}", e.getMessage());
            }
        }

        return units;
    }

    /**
     * Extract units from traditional XBRL.
     */
    private Map<String, XbrlUnit> extractUnitsFromXbrl(Element xbrlRoot) {
        Map<String, XbrlUnit> units = new HashMap<>();

        Elements unitElements = xbrlRoot.select("unit, xbrli\\:unit");
        for (Element elem : unitElements) {
            try {
                XbrlUnit unit = parseUnit(elem);
                if (unit != null && unit.getId() != null) {
                    units.put(unit.getId(), unit);
                }
            } catch (Exception e) {
                log.trace("Failed to parse unit: {}", e.getMessage());
            }
        }

        return units;
    }

    /**
     * Parse a single unit element.
     */
    private XbrlUnit parseUnit(Element elem) {
        String id = elem.attr("id");
        if (id.isEmpty()) return null;

        XbrlUnit.XbrlUnitBuilder builder = XbrlUnit.builder().id(id);

        // Check for divide
        Element divide = elem.selectFirst("divide, xbrli\\:divide");
        if (divide != null) {
            builder.type(XbrlUnit.UnitType.DIVIDE);

            Element numerator = divide.selectFirst(
                    "unitNumerator, xbrli\\:unitNumerator"
            );
            Element denominator = divide.selectFirst(
                    "unitDenominator, xbrli\\:unitDenominator"
            );

            if (numerator != null) {
                builder.numeratorMeasures(extractMeasures(numerator));
            }
            if (denominator != null) {
                builder.denominatorMeasures(extractMeasures(denominator));
            }

            return builder.build();
        }

        // Simple measure
        Elements measures = elem.select("measure, xbrli\\:measure");
        if (measures.size() == 1) {
            builder.type(XbrlUnit.UnitType.SIMPLE);
            String measure = measures.first().text();
            builder.measure(measure);

            NamespaceResolver.QNameParts parts = namespaceResolver.parseQName(measure);
            if (parts != null) {
                builder.measureNamespace(parts.namespaceUri());
                builder.measureLocalName(parts.localName());
            }
        } else if (measures.size() > 1) {
            builder.type(XbrlUnit.UnitType.MULTIPLY);
            builder.numeratorMeasures(extractMeasures(elem));
        }

        return builder.build();
    }

    /**
     * Extract measure values from container.
     */
    private List<String> extractMeasures(Element container) {
        List<String> measures = new ArrayList<>();
        Elements measureElements = container.select("measure, xbrli\\:measure");
        for (Element m : measureElements) {
            measures.add(m.text());
        }
        return measures;
    }

    /**
     * Extract facts from traditional XBRL.
     */
    private List<XbrlFact> extractFactsFromXbrl(Element xbrlRoot, ParseResult result) {
        List<XbrlFact> facts = new ArrayList<>();

        // Get all direct children that are not context, unit, or schema elements
        Set<String> excludedTags = Set.of(
                "context", "xbrli:context", "unit", "xbrli:unit",
                "schemaref", "link:schemaref", "linkbaseref", "link:linkbaseref"
        );

        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        extractFactCandidates(xbrlRoot.children(), excludedTags, facts, result, visited);

        if (facts.isEmpty()) {
            Elements candidates = xbrlRoot.getElementsByAttribute("contextRef");
            if (candidates.isEmpty()) {
                candidates = xbrlRoot.getElementsByAttribute("contextref");
            }
            extractFactCandidates(candidates, excludedTags, facts, result, visited);
        }

        result.setTotalFactsFound(facts.size() + result.getSkippedFacts());
        return facts;
    }

    private void extractFactCandidates(Iterable<Element> elements,
                                       Set<String> excludedTags,
                                       List<XbrlFact> facts,
                                       ParseResult result,
                                       Set<Element> visited) {
        for (Element element : elements) {
            if (!visited.add(element)) {
                continue;
            }
            String tagName = element.tagName().toLowerCase();
            if (excludedTags.contains(tagName)) {
                continue;
            }

            try {
                XbrlFact fact = parseXbrlFact(element);
                if (fact != null) {
                    facts.add(fact);
                    result.setSuccessfullyParsedFacts(
                            result.getSuccessfullyParsedFacts() + 1
                    );
                }
            } catch (Exception e) {
                log.trace("Failed to parse fact: {}", e.getMessage());
                result.setSkippedFacts(result.getSkippedFacts() + 1);
            }
        }
    }

    /**
     * Parse a traditional XBRL fact element.
     */
    private XbrlFact parseXbrlFact(Element elem) {
        String tagName = elem.tagName();
        NamespaceResolver.QNameParts qname = namespaceResolver.parseQName(tagName);

        XbrlFact.XbrlFactBuilder builder = XbrlFact.builder()
                .conceptPrefix(qname != null ? qname.prefix() : null)
                .conceptNamespace(qname != null ? qname.namespaceUri() : null)
                .conceptLocalName(qname != null ? qname.localName() : tagName)
                .contextRef(elem.attr("contextRef"))
                .unitRef(elem.attr("unitRef"))
                .sourceElement(tagName);

        String rawValue = elem.text();
        builder.rawValue(rawValue);

        // Check for nil
        if ("true".equalsIgnoreCase(elem.attr("xsi:nil"))) {
            builder.isNil(true);
        }

        // Parse decimals/precision
        String decimals = elem.attr("decimals");
        if (!decimals.isEmpty() && !"INF".equalsIgnoreCase(decimals)) {
            try {
                builder.decimals(Integer.parseInt(decimals));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Determine if numeric
        String unitRef = elem.attr("unitRef");
        if (!unitRef.isEmpty()) {
            BigDecimal numericValue = valueTransformer.parseGenericNumber(rawValue);
            builder.numericValue(numericValue);
            builder.factType(XbrlFact.FactType.DECIMAL);
        } else {
            builder.stringValue(rawValue);
            builder.factType(XbrlFact.FactType.STRING);
        }

        // Get fact type from taxonomy
        if (qname != null) {
            XbrlFact.FactType factType = taxonomyResolver.getFactType(
                    qname.namespaceUri(), qname.localName()
            );
            if (factType != XbrlFact.FactType.UNKNOWN) {
                builder.factType(factType);
            }
        }

        return builder.build();
    }

    /**
     * Find the XBRL root element.
     */
    private Element findXbrlRoot(Document doc) {
        // Try direct xbrl element
        Element xbrl = doc.selectFirst("xbrl, xbrli\\:xbrl");
        if (xbrl != null) return xbrl;

        // For some documents, it might be the document element
        if (!doc.children().isEmpty()) {
            Element root = doc.child(0);
            String tagName = root.tagName().toLowerCase();
            if (tagName.contains("xbrl")) {
                return root;
            }
        }

        return null;
    }

    /**
     * Extract entity information from contexts.
     */
    private void extractEntityInfo(Map<String, XbrlContext> contexts,
                                    XbrlInstance.XbrlInstanceBuilder builder) {
        // Get entity from first context
        if (!contexts.isEmpty()) {
            XbrlContext firstContext = contexts.values().iterator().next();
            builder.entityIdentifier(firstContext.getEntityIdentifier());
            builder.entityScheme(firstContext.getEntityScheme());
        }
    }

    /**
     * Parse a date string.
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return null;
        value = value.trim();

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            // Try removing time component
            if (value.contains("T")) {
                value = value.substring(0, value.indexOf("T"));
                try {
                    return LocalDate.parse(value);
                } catch (DateTimeParseException e2) {
                    // Fall through
                }
            }
        }

        return null;
    }

    /**
     * Detect XBRL format from document type.
     */
    private XbrlInstance.XbrlFormat detectFormat(ErrorRecoveryParser.DocumentType docType) {
        switch (docType) {
            case INLINE_XBRL:
                return XbrlInstance.XbrlFormat.INLINE_XBRL;
            case XBRL_XML:
                return XbrlInstance.XbrlFormat.XBRL;
            default:
                return XbrlInstance.XbrlFormat.UNKNOWN;
        }
    }
}
