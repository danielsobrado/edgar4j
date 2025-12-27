package org.jds.edgar4j.xbrl.taxonomy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches XBRL taxonomies (DTS - Discoverable Taxonomy Set).
 * Implements efficient caching and lazy loading of taxonomy elements.
 */
@Slf4j
@Component
public class TaxonomyResolver {

    private final WebClient webClient;

    // Cache for taxonomy schemas (schema URL -> TaxonomySchema)
    private final Cache<String, TaxonomySchema> schemaCache;

    // Cache for concept definitions (namespace#localName -> ConceptDefinition)
    private final Cache<String, ConceptDefinition> conceptCache;

    // Cache for label linkbases (namespace#localName#lang -> label)
    private final Cache<String, String> labelCache;

    // Track loaded schemas to avoid circular imports
    private final Set<String> loadedSchemas = ConcurrentHashMap.newKeySet();

    // Common SEC taxonomy base URLs
    private static final List<String> SEC_TAXONOMY_BASES = Arrays.asList(
            "https://xbrl.fasb.org/",
            "https://xbrl.sec.gov/",
            "https://www.sec.gov/",
            "http://xbrl.fasb.org/",
            "http://xbrl.sec.gov/"
    );

    public TaxonomyResolver(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        // Configure caches with appropriate settings
        this.schemaCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofHours(24))
                .build();

        this.conceptCache = Caffeine.newBuilder()
                .maximumSize(50000)
                .expireAfterAccess(Duration.ofHours(24))
                .build();

        this.labelCache = Caffeine.newBuilder()
                .maximumSize(100000)
                .expireAfterAccess(Duration.ofHours(24))
                .build();
    }

    /**
     * Resolve a concept definition from its namespace and local name.
     */
    public ConceptDefinition resolveConcept(String namespace, String localName) {
        String key = namespace + "#" + localName;
        return conceptCache.get(key, k -> lookupConcept(namespace, localName));
    }

    /**
     * Get the fact type for a concept.
     */
    public XbrlFact.FactType getFactType(String namespace, String localName) {
        ConceptDefinition def = resolveConcept(namespace, localName);
        if (def != null) {
            return def.getFactType();
        }

        // Infer type from local name patterns
        return inferFactType(localName);
    }

    /**
     * Get a human-readable label for a concept.
     */
    public String getLabel(String namespace, String localName, String lang) {
        String key = namespace + "#" + localName + "#" + (lang != null ? lang : "en");
        return labelCache.get(key, k -> lookupLabel(namespace, localName, lang));
    }

    /**
     * Load a taxonomy schema and its imports.
     */
    public Mono<TaxonomySchema> loadSchema(String schemaUrl) {
        // Check cache first
        TaxonomySchema cached = schemaCache.getIfPresent(schemaUrl);
        if (cached != null) {
            return Mono.just(cached);
        }

        // Avoid circular imports
        if (!loadedSchemas.add(schemaUrl)) {
            return Mono.empty();
        }

        String resolvedUrl = resolveSchemaUrl(schemaUrl);
        log.debug("Loading taxonomy schema: {}", resolvedUrl);

        return webClient.get()
                .uri(resolvedUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(content -> parseSchema(resolvedUrl, content))
                .doOnNext(schema -> schemaCache.put(schemaUrl, schema))
                .doOnError(e -> log.warn("Failed to load schema {}: {}", schemaUrl, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Resolve relative schema URL to absolute.
     */
    public String resolveSchemaUrl(String url) {
        if (url == null) return null;

        // Already absolute
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        // Try common SEC bases
        for (String base : SEC_TAXONOMY_BASES) {
            String candidate = base + url;
            // Could add validation here
            return candidate;
        }

        return url;
    }

    /**
     * Resolve relative URL against a base URL.
     */
    public String resolveRelativeUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl == null) return null;

        // Already absolute
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }

        try {
            URI base = URI.create(baseUrl);
            return base.resolve(relativeUrl).toString();
        } catch (Exception e) {
            log.trace("Failed to resolve relative URL: {} against {}", relativeUrl, baseUrl);
            return relativeUrl;
        }
    }

    /**
     * Clear all caches.
     */
    public void clearCaches() {
        schemaCache.invalidateAll();
        conceptCache.invalidateAll();
        labelCache.invalidateAll();
        loadedSchemas.clear();
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Long> getCacheStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("schemas", schemaCache.estimatedSize());
        stats.put("concepts", conceptCache.estimatedSize());
        stats.put("labels", labelCache.estimatedSize());
        return stats;
    }

    // Private helper methods

    private ConceptDefinition lookupConcept(String namespace, String localName) {
        // For now, return a basic definition
        // Full implementation would query loaded taxonomies
        ConceptDefinition def = new ConceptDefinition();
        def.setNamespace(namespace);
        def.setLocalName(localName);
        def.setFactType(inferFactType(localName));
        return def;
    }

    private String lookupLabel(String namespace, String localName, String lang) {
        // Convert camelCase/PascalCase to readable label
        return humanize(localName);
    }

    private XbrlFact.FactType inferFactType(String localName) {
        if (localName == null) return XbrlFact.FactType.UNKNOWN;

        String lower = localName.toLowerCase();

        // Text blocks
        if (lower.endsWith("textblock") || lower.contains("policytext")) {
            return XbrlFact.FactType.TEXT_BLOCK;
        }

        // Per share items
        if (lower.contains("pershare") || lower.contains("eps")) {
            return XbrlFact.FactType.PER_SHARE;
        }

        // Share counts
        if (lower.contains("shares") || lower.contains("stockissued")
                || lower.contains("sharesoutstanding")) {
            return XbrlFact.FactType.SHARES;
        }

        // Boolean/flag items
        if (lower.startsWith("is") || lower.startsWith("has")
                || lower.endsWith("flag") || lower.endsWith("indicator")) {
            return XbrlFact.FactType.BOOLEAN;
        }

        // Date items
        if (lower.endsWith("date") || lower.contains("dateofbirth")
                || lower.contains("incorporationdate")) {
            return XbrlFact.FactType.DATE;
        }

        // Pure/ratio items
        if (lower.contains("ratio") || lower.contains("percentage")
                || lower.contains("percent") || lower.contains("rate")) {
            return XbrlFact.FactType.PURE;
        }

        // Common monetary patterns
        if (lower.contains("revenue") || lower.contains("income")
                || lower.contains("expense") || lower.contains("asset")
                || lower.contains("liability") || lower.contains("equity")
                || lower.contains("cash") || lower.contains("debt")
                || lower.contains("profit") || lower.contains("loss")
                || lower.contains("cost") || lower.contains("amount")
                || lower.contains("value") || lower.contains("price")) {
            return XbrlFact.FactType.MONETARY;
        }

        // Integer items
        if (lower.contains("count") || lower.contains("number")
                || lower.endsWith("quantity")) {
            return XbrlFact.FactType.INTEGER;
        }

        // Default to string for description/name/text fields
        if (lower.contains("description") || lower.contains("name")
                || lower.contains("text") || lower.contains("address")
                || lower.contains("title")) {
            return XbrlFact.FactType.STRING;
        }

        return XbrlFact.FactType.UNKNOWN;
    }

    private TaxonomySchema parseSchema(String url, String content) {
        TaxonomySchema schema = new TaxonomySchema();
        schema.setLocation(url);
        // Basic parsing - would be expanded for full taxonomy support
        return schema;
    }

    private String humanize(String camelCase) {
        if (camelCase == null) return null;

        // Insert spaces before capitals
        String result = camelCase.replaceAll("([A-Z])", " $1").trim();

        // Capitalize first letter
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }

    /**
     * Represents a taxonomy schema document.
     */
    @Data
    public static class TaxonomySchema {
        private String location;
        private String targetNamespace;
        private List<String> imports = new ArrayList<>();
        private List<String> linkbaseRefs = new ArrayList<>();
        private Map<String, ConceptDefinition> concepts = new HashMap<>();
    }

    /**
     * Represents an XBRL concept definition.
     */
    @Data
    public static class ConceptDefinition {
        private String namespace;
        private String localName;
        private String name;  // Element name
        private String id;
        private XbrlFact.FactType factType;
        private String substitutionGroup;
        private String type;
        private boolean isAbstract;
        private boolean isNillable;
        private String periodType;  // instant, duration, forever
        private String balance;     // debit, credit (for monetary items)
        private Map<String, String> labels = new HashMap<>();  // lang -> label
    }
}
