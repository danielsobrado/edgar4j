# Phase 2: File-Based Storage Engine

## Objective

Build a reusable, type-safe file storage engine that serves as the persistence layer for all file-backed repository adapters. This engine handles JSON/JSONL/CSV/Parquet read/write operations, indexing, concurrency, and pagination.

## Design Pattern: Template Method + Strategy

```
┌──────────────────────────────────────────┐
│          FileStorageEngine               │
│  (orchestration, caching, locking)       │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │       FileFormatStrategy           │  │
│  │  (interface)                       │  │
│  │  - read(Path) → List<T>           │  │
│  │  - write(Path, List<T>)           │  │
│  │  - append(Path, T)                │  │
│  │  - stream(Path) → Stream<T>      │  │
│  └────────┬───────────┬──────────────┘  │
│           │           │                  │
│    ┌──────┴──┐  ┌─────┴────┐             │
│    │  JSON   │  │  JSONL   │             │
│    │Strategy │  │ Strategy │             │
│    └─────────┘  └──────────┘             │
│    ┌──────────┐ ┌──────────┐             │
│    │  CSV     │ │ Parquet  │             │
│    │Strategy  │ │ Strategy │             │
│    └──────────┘ └──────────┘             │
└──────────────────────────────────────────┘
```

## Package Structure

```
org.jds.edgar4j.storage.file/
├── FileStorageEngine.java              # Main engine facade
├── FileStorageProperties.java          # Configuration properties
├── format/
│   ├── FileFormatStrategy.java         # Strategy interface
│   ├── JsonFileStrategy.java           # Single JSON array file
│   ├── JsonlFileStrategy.java          # Line-delimited JSON (large collections)
│   ├── CsvFileStrategy.java            # CSV for flat tables
│   └── ParquetFileStrategy.java        # Columnar format for analytics
├── index/
│   ├── InMemoryIndex.java              # Hash index for fast lookups
│   ├── IndexDefinition.java            # Index configuration
│   └── IndexManager.java              # Build/rebuild/persist indexes
├── query/
│   ├── FileQuery.java                  # Query builder
│   ├── FileQueryExecutor.java          # Execute queries against in-memory data
│   ├── FilePaginator.java              # Page/Pageable support
│   └── FileSorter.java                 # Sort support
├── lock/
│   └── FileWriteLock.java              # ReentrantReadWriteLock per collection
└── id/
    └── IdGenerator.java                # UUID-based ID generation
```

## Core Components

### 2.1 FileStorageProperties

```java
@ConfigurationProperties(prefix = "edgar4j.storage.file")
public class FileStorageProperties {
    /** Root directory for all file-based data */
    private String basePath = "./data";

    /** Maximum records to hold in memory per collection */
    private int maxInMemoryRecords = 100_000;

    /** Whether to build indexes on startup */
    private boolean indexOnStartup = true;

    /** Write-ahead log for crash recovery */
    private boolean walEnabled = true;

    /** Auto-flush interval for dirty collections */
    private Duration flushInterval = Duration.ofSeconds(30);
}
```

### 2.2 FileFormatStrategy Interface

```java
public interface FileFormatStrategy<T> {

    /** Read all records from the file */
    List<T> readAll(Path path, Class<T> type);

    /** Stream records lazily (for large files) */
    Stream<T> stream(Path path, Class<T> type);

    /** Write all records (full overwrite) */
    void writeAll(Path path, List<T> records, Class<T> type);

    /** Append a single record */
    void append(Path path, T record, Class<T> type);

    /** Append multiple records */
    void appendAll(Path path, List<T> records, Class<T> type);

    /** File extension for this format */
    String extension();
}
```

### 2.3 Format Implementations

#### JsonFileStrategy (Small Collections)
- Reads/writes a single JSON array: `[{...}, {...}]`
- Best for collections < 10,000 records (companies, tickers, settings)
- Uses Jackson `ObjectMapper` with snake_case naming (matching existing config)
- Full file rewrite on save

#### JsonlFileStrategy (Large Collections)
- One JSON object per line, newline-delimited
- Best for collections > 10,000 records (form4, fillings, submissions)
- Supports `append()` without reading entire file
- Streamable with `BufferedReader` line-by-line
- Each line independently parseable (crash-resilient)

#### CsvFileStrategy (Flat Lookup Tables)
- Standard CSV with headers
- Best for `transaction_types`, small reference data
- Uses Jackson CSV mapper for type-safe binding

#### ParquetFileStrategy (Analytics Tables)
- Apache Parquet format via `parquet-avro`
- Best for `insider_transactions`, `companies`, `insiders`
- Columnar reads: only load needed columns for aggregation queries
- Compressed (Snappy by default)
- Requires `parquet-avro` and `hadoop-common` dependencies (minimal, shaded)

### 2.4 InMemoryIndex

```java
public class InMemoryIndex<T> {
    private final String fieldName;
    private final Function<T, String> keyExtractor;
    private final Map<String, List<T>> index;  // ConcurrentHashMap

    /** Build index from collection */
    public void rebuild(List<T> records) { ... }

    /** Lookup by key */
    public List<T> get(String key) { ... }

    /** Range scan (for date-based indexes) */
    public List<T> range(String from, String to) { ... }

    /** Add record to index */
    public void add(T record) { ... }

    /** Remove record from index */
    public void remove(T record) { ... }
}
```

### 2.5 FileStorageEngine

The main facade that ties everything together:

```java
public class FileStorageEngine {

    private final FileStorageProperties properties;
    private final Map<String, FileCollection<?>> collections;

    /** Register a collection with its format and index definitions */
    public <T> FileCollection<T> registerCollection(
            String name,
            Class<T> type,
            FileFormatStrategy<T> format,
            List<IndexDefinition<T>> indexes) { ... }

    /** Get a registered collection */
    public <T> FileCollection<T> getCollection(String name, Class<T> type) { ... }

    /** Flush all dirty collections to disk */
    public void flushAll() { ... }

    /** Shutdown hook: flush + close */
    @PreDestroy
    public void shutdown() { ... }
}
```

### 2.6 FileCollection (Per-Collection Abstraction)

```java
public class FileCollection<T> {
    private final String name;
    private final Path filePath;
    private final Class<T> type;
    private final FileFormatStrategy<T> format;
    private final IndexManager<T> indexManager;
    private final FileWriteLock lock;
    private final Function<T, String> idExtractor;

    // State
    private List<T> records;       // In-memory copy
    private boolean dirty = false;  // Needs flush

    // CRUD
    public T save(T record) { ... }
    public List<T> saveAll(List<T> records) { ... }
    public Optional<T> findById(String id) { ... }
    public List<T> findAll() { ... }
    public void deleteById(String id) { ... }
    public boolean existsById(String id) { ... }

    // Query
    public List<T> findByIndex(String indexName, String key) { ... }
    public Page<T> findAll(Pageable pageable) { ... }
    public Page<T> query(Predicate<T> filter, Pageable pageable) { ... }

    // Lifecycle
    public void load() { ... }   // Read from disk into memory
    public void flush() { ... }  // Write dirty state to disk
}
```

## Concurrency Model

```
Read Operations:   ReadLock (multiple concurrent readers allowed)
Write Operations:  WriteLock (exclusive, blocks readers and writers)
Flush Operations:  WriteLock (snapshot state, write to temp file, atomic rename)
```

- Each `FileCollection` has its own `ReentrantReadWriteLock`
- Writes go to in-memory list first, then periodic background flush
- Flush uses atomic file operations: write to `.tmp`, then `Files.move(ATOMIC_MOVE)`

## New Maven Dependencies

```xml
<!-- Parquet support (only included when profile resource-low is active) -->
<dependency>
    <groupId>org.apache.parquet</groupId>
    <artifactId>parquet-avro</artifactId>
    <version>1.14.6</version>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.apache.hadoop</groupId>
    <artifactId>hadoop-common</artifactId>
    <version>3.4.1</version>
    <optional>true</optional>
    <exclusions>
        <!-- Exclude everything except what Parquet needs -->
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- CSV support (Jackson already in classpath) -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-csv</artifactId>
</dependency>
```

**Alternative to Parquet (if Hadoop dependency is too heavy):**
Consider using **DuckDB** (embedded OLAP database) as the analytical store in low mode instead of Parquet files. DuckDB can read/write Parquet natively and provides SQL query capability with zero external dependencies.

```xml
<dependency>
    <groupId>org.duckdb</groupId>
    <artifactId>duckdb_jdbc</artifactId>
    <version>1.2.2</version>
    <optional>true</optional>
</dependency>
```

## Performance Targets

| Operation | Target (10K records) | Target (100K records) |
|-----------|---------------------|----------------------|
| Load collection from disk | < 500ms | < 3s |
| Save single record | < 5ms | < 5ms |
| Find by indexed field | < 1ms | < 5ms |
| Find by non-indexed field (scan) | < 50ms | < 500ms |
| Paginated query (page of 20) | < 10ms | < 50ms |
| Full flush to disk | < 200ms | < 2s |

## Validation Checklist

- [ ] JSON/JSONL round-trip preserves all field types (dates, enums, nested objects)
- [ ] Concurrent reads and writes don't corrupt data
- [ ] Atomic flush survives process crash (no partial writes)
- [ ] In-memory indexes stay consistent with underlying data
- [ ] Memory usage stays within configured limits
- [ ] Parquet files are readable by external tools (DuckDB, pandas, Spark)
