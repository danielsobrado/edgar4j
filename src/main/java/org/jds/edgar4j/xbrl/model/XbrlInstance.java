package org.jds.edgar4j.xbrl.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a parsed XBRL instance document.
 * Contains all extracted facts, contexts, units, and metadata.
 */
@Data
@Builder
public class XbrlInstance {

    private String documentUri;
    private XbrlFormat format;
    private LocalDateTime parseTime;

    // Core XBRL data
    @Builder.Default
    private Map<String, XbrlContext> contexts = new HashMap<>();

    @Builder.Default
    private Map<String, XbrlUnit> units = new HashMap<>();

    @Builder.Default
    private List<XbrlFact> facts = new ArrayList<>();

    // Namespace mappings discovered during parsing
    @Builder.Default
    private Map<String, String> namespaces = new HashMap<>();

    // Schema references for DTS traversal
    @Builder.Default
    private List<String> schemaRefs = new ArrayList<>();

    // Linkbase references
    @Builder.Default
    private List<String> linkbaseRefs = new ArrayList<>();

    // Parsing metadata
    @Builder.Default
    private ParseResult parseResult = new ParseResult();

    // Entity information
    private String entityIdentifier;
    private String entityScheme;

    /**
     * Get facts by concept name (local name without namespace).
     */
    public List<XbrlFact> getFactsByConceptName(String conceptName) {
        return facts.stream()
                .filter(f -> f.getConceptLocalName().equals(conceptName))
                .toList();
    }

    /**
     * Get facts by namespace and local name.
     */
    public List<XbrlFact> getFactsByQName(String namespace, String localName) {
        return facts.stream()
                .filter(f -> f.getConceptNamespace().equals(namespace)
                        && f.getConceptLocalName().equals(localName))
                .toList();
    }

    /**
     * Get facts for a specific context.
     */
    public List<XbrlFact> getFactsByContextId(String contextId) {
        return facts.stream()
                .filter(f -> contextId.equals(f.getContextRef()))
                .toList();
    }

    /**
     * Get all monetary facts with their calculated values.
     */
    public List<XbrlFact> getMonetaryFacts() {
        return facts.stream()
                .filter(f -> f.getUnitRef() != null)
                .filter(f -> {
                    XbrlUnit unit = units.get(f.getUnitRef());
                    return unit != null && unit.isMonetary();
                })
                .toList();
    }

    /**
     * Get the primary reporting period context (usually the current period).
     */
    public XbrlContext getPrimaryContext() {
        return contexts.values().stream()
                .filter(c -> c.getPeriod() != null && !c.hasDimensions())
                .max((a, b) -> {
                    if (a.getPeriod().getEndDate() == null) return -1;
                    if (b.getPeriod().getEndDate() == null) return 1;
                    return a.getPeriod().getEndDate().compareTo(b.getPeriod().getEndDate());
                })
                .orElse(null);
    }

    public enum XbrlFormat {
        XBRL,           // Traditional XBRL XML
        INLINE_XBRL,    // iXBRL (HTML with embedded XBRL)
        UNKNOWN
    }
}
