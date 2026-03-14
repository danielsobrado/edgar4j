# Phase 7: Search Abstraction

## Objective

Provide full-text search capability in both modes: **Elasticsearch** in high-resource mode, **embedded Lucene** or **in-memory linear scan** in low-resource mode. Elasticsearch is currently disabled in the project, so this phase also establishes the search architecture for future enablement.

## Current State

- Elasticsearch dependency exists in `pom.xml` (`spring-boot-starter-data-elasticsearch`)
- Repositories are disabled: `spring.elasticsearch.repositories.enabled: false`
- No Elasticsearch document models or repositories exist yet
- Full-text search is done via MongoDB `$regex` queries

## Search Port Interface

```java
package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Abstraction for full-text and structured search across filings.
 */
public interface FilingSearchPort {

    /** Full-text search across all filing types */
    Page<SearchResult> search(String query, Pageable pageable);

    /** Filtered search */
    Page<SearchResult> search(SearchCriteria criteria, Pageable pageable);

    /** Suggest completions for partial queries */
    List<String> suggest(String prefix, int maxResults);

    /** Index a filing for search */
    void index(Indexable document);

    /** Remove a document from the index */
    void removeFromIndex(String documentId);

    /** Rebuild the entire search index */
    void rebuildIndex();

    record SearchResult(
        String id,
        String type,           // "form4", "form8k", etc.
        String title,
        String snippet,        // Highlighted match
        double score,
        LocalDate filingDate
    ) {}

    record SearchCriteria(
        String query,
        List<String> formTypes,
        String cik,
        String symbol,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    interface Indexable {
        String getSearchId();
        String getSearchType();
        String getSearchContent();
        LocalDate getSearchDate();
    }
}
```

## High-Resource Mode: Elasticsearch Adapter

```java
@Component
@Profile("resource-high")
@ConditionalOnProperty(name = "spring.elasticsearch.repositories.enabled", havingValue = "true")
public class ElasticsearchSearchAdapter implements FilingSearchPort {
    // Uses Spring Data Elasticsearch
    // NativeSearchQuery for full-text
    // Highlight support for snippets
}
```

When Elasticsearch is disabled in high mode, fall back to MongoDB text search:

```java
@Component
@Profile("resource-high")
@ConditionalOnProperty(name = "spring.elasticsearch.repositories.enabled", havingValue = "false", matchIfMissing = true)
public class MongoTextSearchAdapter implements FilingSearchPort {
    // Uses MongoDB $text indexes or $regex
    // Already the current behavior
}
```

## Low-Resource Mode: Embedded Lucene Adapter

Apache Lucene provides a powerful embedded search engine with zero external dependencies.

```java
@Component
@Profile("resource-low")
public class EmbeddedLuceneSearchAdapter implements FilingSearchPort {

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private SearcherManager searcherManager;

    public EmbeddedLuceneSearchAdapter(FileStorageProperties properties) throws IOException {
        Path indexPath = Path.of(properties.getBasePath(), "indexes", "lucene");
        Files.createDirectories(indexPath);
        this.directory = FSDirectory.open(indexPath);
        this.analyzer = new StandardAnalyzer();
        this.writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        this.searcherManager = new SearcherManager(writer, null);
    }

    @Override
    public Page<SearchResult> search(String query, Pageable pageable) {
        QueryParser parser = new QueryParser("content", analyzer);
        Query luceneQuery = parser.parse(query);
        // Execute search with pagination
        // Return results with highlights
    }

    @Override
    public void index(Indexable document) {
        Document doc = new Document();
        doc.add(new StringField("id", document.getSearchId(), Field.Store.YES));
        doc.add(new TextField("content", document.getSearchContent(), Field.Store.YES));
        doc.add(new StringField("type", document.getSearchType(), Field.Store.YES));
        writer.updateDocument(new Term("id", document.getSearchId()), doc);
        searcherManager.maybeRefresh();
    }

    @PreDestroy
    public void close() throws IOException {
        searcherManager.close();
        writer.close();
        directory.close();
    }
}
```

### Alternative: Simple In-Memory Search

For minimal deployments that don't need Lucene, provide a simpler option:

```java
@Component
@Profile("resource-low")
@ConditionalOnProperty(name = "edgar4j.search.engine", havingValue = "simple", matchIfMissing = true)
public class InMemorySearchAdapter implements FilingSearchPort {

    private final FileStorageEngine storageEngine;

    @Override
    public Page<SearchResult> search(String query, Pageable pageable) {
        String lowerQuery = query.toLowerCase();
        // Linear scan across all collections with string matching
        // Slower but zero additional dependencies
    }
}
```

### Configuration

```yaml
edgar4j:
  search:
    engine: ${EDGAR4J_SEARCH_ENGINE:simple}  # "simple" or "lucene"
```

## New Maven Dependencies

```xml
<!-- Lucene for embedded search (optional, low mode) -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>10.2.1</version>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-queryparser</artifactId>
    <version>10.2.1</version>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-highlighter</artifactId>
    <version>10.2.1</version>
    <optional>true</optional>
</dependency>
```

## Validation Checklist

- [ ] Full-text search returns results in both modes
- [ ] Search index is built on startup from existing data (low mode)
- [ ] New filings are indexed automatically when saved
- [ ] No Elasticsearch connection attempts in low mode
- [ ] Lucene index persists across restarts
- [ ] In-memory search fallback works without Lucene dependency

## Estimated Effort

- **Search port interface**: 0.5 day
- **In-memory search adapter**: 1 day
- **Embedded Lucene adapter**: 2 days
- **Elasticsearch adapter (optional)**: 1 day
- **Total**: 3-4 days
