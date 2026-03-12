package org.jds.edgar4j.xbrl.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.xml.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Memory-efficient streaming XBRL parser for large filings.
 *
 * KEY DIFFERENTIATOR:
 * - Processes files without loading entirely into memory
 * - Emits facts as they are parsed (reactive streams)
 * - Handles multi-GB filings that would crash other parsers
 * - Supports progress callbacks for long-running parses
 *
 * Use this when:
 * - Processing very large filings (>100MB)
 * - Memory is constrained
 * - You need incremental results
 * - Processing many filings in parallel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingXbrlParser {

    private final NamespaceResolver namespaceResolver;
    private final ValueTransformer valueTransformer;

    private final XMLInputFactory xmlInputFactory;

    public StreamingXbrlParser() {
        this.namespaceResolver = new NamespaceResolver();
        this.valueTransformer = new ValueTransformer();
        this.xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /**
     * Parse XBRL as a reactive stream of facts.
     * Facts are emitted as they are parsed, enabling memory-efficient processing.
     */
    public Flux<XbrlFact> parseAsStream(InputStream inputStream) {
        return Flux.create(sink -> {
            try {
                streamParse(inputStream, fact -> {
                    if (!sink.isCancelled()) {
                        sink.next(fact);
                    }
                });
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * Parse with a callback for each fact.
     * Most memory-efficient option for large files.
     */
    public StreamingParseResult parseWithCallback(InputStream inputStream,
                                                   Consumer<XbrlFact> factConsumer) {
        return streamParse(inputStream, factConsumer);
    }

    /**
     * Parse with progress tracking.
     */
    public StreamingParseResult parseWithProgress(InputStream inputStream,
                                                   Consumer<XbrlFact> factConsumer,
                                                   Consumer<ParseProgress> progressConsumer) {
        return streamParseWithProgress(inputStream, factConsumer, progressConsumer);
    }

    /**
     * Stream parse only contexts and units (metadata).
     * Useful when you only need structural information.
     */
    public Mono<XbrlMetadata> parseMetadataOnly(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            XbrlMetadata.XbrlMetadataBuilder builder = XbrlMetadata.builder();
            Map<String, XbrlContext> contexts = new HashMap<>();
            Map<String, XbrlUnit> units = new HashMap<>();
            Map<String, String> namespaces = new HashMap<>();

            try {
                XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);

                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName().toLowerCase();

                        // Collect namespace declarations
                        for (int i = 0; i < reader.getNamespaceCount(); i++) {
                            String prefix = reader.getNamespacePrefix(i);
                            String uri = reader.getNamespaceURI(i);
                            if (prefix != null && uri != null) {
                                namespaces.put(prefix, uri);
                            }
                        }

                        // Parse context
                        if (localName.equals("context")) {
                            XbrlContext context = parseContext(reader);
                            if (context != null) {
                                contexts.put(context.getId(), context);
                            }
                        }

                        // Parse unit
                        if (localName.equals("unit")) {
                            XbrlUnit unit = parseUnit(reader);
                            if (unit != null) {
                                units.put(unit.getId(), unit);
                            }
                        }

                        // Parse schemaRef
                        if (localName.equals("schemaref")) {
                            String href = getAttributeValue(reader, "href", "xlink:href");
                            if (href != null) {
                                builder.schemaRef(href);
                            }
                        }
                    }
                }

                reader.close();
            } catch (XMLStreamException e) {
                log.error("Error parsing metadata: {}", e.getMessage());
            }

            return builder
                    .contexts(contexts)
                    .units(units)
                    .namespaces(namespaces)
                    .build();
        });
    }

    /**
     * Count facts without fully parsing them.
     * Very fast for large files.
     */
    public Mono<Long> countFacts(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            AtomicInteger count = new AtomicInteger(0);

            try {
                XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);

                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String contextRef = reader.getAttributeValue(null, "contextRef");
                        if (contextRef != null) {
                            count.incrementAndGet();
                        }
                    }
                }

                reader.close();
            } catch (XMLStreamException e) {
                log.error("Error counting facts: {}", e.getMessage());
            }

            return (long) count.get();
        });
    }

    // Core streaming implementation

    private StreamingParseResult streamParse(InputStream inputStream,
                                              Consumer<XbrlFact> factConsumer) {
        return streamParseWithProgress(inputStream, factConsumer, null);
    }

    private StreamingParseResult streamParseWithProgress(InputStream inputStream,
                                                          Consumer<XbrlFact> factConsumer,
                                                          Consumer<ParseProgress> progressConsumer) {
        StreamingParseResult result = new StreamingParseResult();
        long startTime = System.currentTimeMillis();

        // Track contexts and units for reference
        Map<String, XbrlContext> contexts = new HashMap<>();
        Map<String, XbrlUnit> units = new HashMap<>();

        AtomicInteger factCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName().toLowerCase();

                    // Collect namespace declarations
                    for (int i = 0; i < reader.getNamespaceCount(); i++) {
                        String prefix = reader.getNamespacePrefix(i);
                        String uri = reader.getNamespaceURI(i);
                        namespaceResolver.registerNamespace(prefix, uri);
                    }

                    // Parse context
                    if (localName.equals("context")) {
                        try {
                            XbrlContext context = parseContext(reader);
                            if (context != null) {
                                contexts.put(context.getId(), context);
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                        continue;
                    }

                    // Parse unit
                    if (localName.equals("unit")) {
                        try {
                            XbrlUnit unit = parseUnit(reader);
                            if (unit != null) {
                                units.put(unit.getId(), unit);
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                        continue;
                    }

                    // Check if this is a fact element (has contextRef)
                    String contextRef = reader.getAttributeValue(null, "contextRef");
                    if (contextRef != null) {
                        try {
                            XbrlFact fact = parseFact(reader, localName);
                            if (fact != null) {
                                factConsumer.accept(fact);
                                int count = factCount.incrementAndGet();

                                // Report progress every 1000 facts
                                if (progressConsumer != null && count % 1000 == 0) {
                                    progressConsumer.accept(new ParseProgress(
                                            count, errorCount.get(),
                                            System.currentTimeMillis() - startTime
                                    ));
                                }
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.trace("Error parsing fact: {}", e.getMessage());
                        }
                    }
                }
            }

            reader.close();

        } catch (XMLStreamException e) {
            log.error("Stream parsing error: {}", e.getMessage());
            result.setError(e.getMessage());
        }

        result.setFactCount(factCount.get());
        result.setErrorCount(errorCount.get());
        result.setContextCount(contexts.size());
        result.setUnitCount(units.size());
        result.setParseTimeMs(System.currentTimeMillis() - startTime);

        return result;
    }

    private XbrlFact parseFact(XMLStreamReader reader, String elementName) throws XMLStreamException {
        String contextRef = reader.getAttributeValue(null, "contextRef");
        String unitRef = reader.getAttributeValue(null, "unitRef");
        String decimals = reader.getAttributeValue(null, "decimals");
        String nilAttr = reader.getAttributeValue(null, "nil");

        // Get the namespace
        String namespaceUri = reader.getNamespaceURI();
        String prefix = reader.getPrefix();

        // Read the text content
        StringBuilder content = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                content.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }

        String rawValue = content.toString().trim();

        XbrlFact.XbrlFactBuilder builder = XbrlFact.builder()
                .conceptNamespace(namespaceUri)
                .conceptLocalName(elementName)
                .conceptPrefix(prefix)
                .contextRef(contextRef)
                .unitRef(unitRef)
                .rawValue(rawValue)
                .sourceElement(elementName);

        // Parse decimals
        if (decimals != null && !decimals.equalsIgnoreCase("INF")) {
            try {
                builder.decimals(Integer.parseInt(decimals));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Check nil
        if ("true".equalsIgnoreCase(nilAttr)) {
            builder.isNil(true);
        }

        // Parse numeric value if has unit
        if (unitRef != null && !rawValue.isEmpty()) {
            BigDecimal numericValue = valueTransformer.parseGenericNumber(rawValue);
            builder.numericValue(numericValue);
            builder.factType(XbrlFact.FactType.DECIMAL);
        } else {
            builder.stringValue(rawValue);
            builder.factType(XbrlFact.FactType.STRING);
        }

        return builder.build();
    }

    private XbrlContext parseContext(XMLStreamReader reader) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        if (id == null) return null;

        XbrlContext.XbrlContextBuilder builder = XbrlContext.builder().id(id);

        int depth = 1;
        String currentElement = null;
        String entityId = null;
        String scheme = null;
        LocalDate instant = null;
        LocalDate startDate = null;
        LocalDate endDate = null;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                currentElement = reader.getLocalName().toLowerCase();

                if (currentElement.equals("identifier")) {
                    scheme = reader.getAttributeValue(null, "scheme");
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = reader.getText().trim();
                if (!text.isEmpty() && currentElement != null) {
                    switch (currentElement) {
                        case "identifier":
                            entityId = text;
                            break;
                        case "instant":
                            instant = parseDate(text);
                            break;
                        case "startdate":
                            startDate = parseDate(text);
                            break;
                        case "enddate":
                            endDate = parseDate(text);
                            break;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                currentElement = null;
            }
        }

        builder.entityIdentifier(entityId);
        builder.entityScheme(scheme);

        XbrlContext.XbrlPeriod.XbrlPeriodBuilder periodBuilder = XbrlContext.XbrlPeriod.builder();
        if (instant != null) {
            periodBuilder.instant(instant);
        } else {
            periodBuilder.startDate(startDate);
            periodBuilder.endDate(endDate);
        }
        builder.period(periodBuilder.build());

        return builder.build();
    }

    private XbrlUnit parseUnit(XMLStreamReader reader) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        if (id == null) return null;

        XbrlUnit.XbrlUnitBuilder builder = XbrlUnit.builder().id(id);
        builder.type(XbrlUnit.UnitType.SIMPLE);

        int depth = 1;
        String currentElement = null;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                currentElement = reader.getLocalName().toLowerCase();

                if (currentElement.equals("divide")) {
                    builder.type(XbrlUnit.UnitType.DIVIDE);
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = reader.getText().trim();
                if (!text.isEmpty() && "measure".equals(currentElement)) {
                    builder.measure(text);
                    // Parse measure QName
                    int colonIdx = text.indexOf(':');
                    if (colonIdx > 0) {
                        builder.measureLocalName(text.substring(colonIdx + 1));
                    } else {
                        builder.measureLocalName(text);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                currentElement = null;
            }
        }

        return builder.build();
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            if (text.contains("T")) {
                text = text.substring(0, text.indexOf("T"));
            }
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String getAttributeValue(XMLStreamReader reader, String... names) {
        for (String name : names) {
            String value = reader.getAttributeValue(null, name);
            if (value != null) return value;

            // Try with namespace
            int colonIdx = name.indexOf(':');
            if (colonIdx > 0) {
                String prefix = name.substring(0, colonIdx);
                String localName = name.substring(colonIdx + 1);
                String nsUri = reader.getNamespaceURI(prefix);
                if (nsUri != null) {
                    value = reader.getAttributeValue(nsUri, localName);
                    if (value != null) return value;
                }
            }
        }
        return null;
    }

    // Result classes

    @lombok.Data
    public static class StreamingParseResult {
        private int factCount;
        private int errorCount;
        private int contextCount;
        private int unitCount;
        private long parseTimeMs;
        private String error;

        public boolean isSuccess() {
            return error == null;
        }

        public double getFactsPerSecond() {
            if (parseTimeMs == 0) return 0;
            return (double) factCount / parseTimeMs * 1000;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ParseProgress {
        private int factsProcessed;
        private int errorsEncountered;
        private long elapsedMs;
    }

    @lombok.Data
    @lombok.Builder
    public static class XbrlMetadata {
        private Map<String, XbrlContext> contexts;
        private Map<String, XbrlUnit> units;
        private Map<String, String> namespaces;
        private String schemaRef;
    }
}
