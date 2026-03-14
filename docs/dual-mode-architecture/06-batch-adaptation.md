# Phase 6: Batch Processing Adaptation

## Objective

Adapt Spring Batch jobs to work in both resource modes. Spring Batch requires a JDBC `JobRepository` for metadata — in low mode, use embedded H2 instead of PostgreSQL. Batch writers must use the port interfaces instead of JPA repositories.

## Current Batch Architecture

```
BatchConfiguration
├── processForm4FilingsJob (chunk size 10)
│   └── processForm4Step
│       ├── EdgarFilingReader        → reads accession numbers from SEC API
│       ├── Form4DocumentProcessor   → fetches XML, parses to InsiderTransaction list
│       └── InsiderTransactionWriter → saves to PostgreSQL via JPA
│
└── bulkHistoricalDataJob (chunk size 50)
    └── Similar pipeline with larger chunks
```

### Current Dependencies

| Component | Current Dependency | Needs Change? |
|-----------|-------------------|--------------|
| `BatchConfiguration` | `DataSource` (PostgreSQL) | Yes - H2 in low mode |
| `EdgarFilingReader` | `SecApiClient` | No - mode independent |
| `Form4DocumentProcessor` | `EdgarApiService`, `Form4ParserService` | No - mode independent |
| `InsiderTransactionWriter` | `InsiderTransactionRepository` (JPA) | Yes - use port |

## Implementation

### 6.1 Batch DataSource Configuration

Spring Batch needs a JDBC DataSource for its metadata tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, etc.). In low mode, use H2 embedded.

```java
@Configuration
@Profile("resource-low")
public class LowResourceBatchConfig {

    @Bean
    @BatchDataSource
    public DataSource batchDataSource(FileStorageProperties properties) {
        String dbPath = properties.getBasePath() + "/batch/batch_metadata";
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:file:" + dbPath + ";AUTO_SERVER=TRUE")
                .username("sa")
                .password("")
                .build();
    }

    @Bean
    public BatchConfigurer batchConfigurer(@BatchDataSource DataSource dataSource) {
        return new DefaultBatchConfigurer(dataSource);
    }
}
```

```java
@Configuration
@Profile("resource-high")
public class HighResourceBatchConfig {
    // Uses the main PostgreSQL DataSource (existing behavior)
    // No @BatchDataSource needed - uses primary DataSource
}
```

### 6.2 Refactor InsiderTransactionWriter

Replace direct JPA repository usage with the port interface:

**Before:**
```java
@Component
public class InsiderTransactionWriter implements ItemWriter<List<InsiderTransaction>> {
    private final InsiderTransactionRepository repository;  // JPA
}
```

**After:**
```java
@Component
public class InsiderTransactionWriter implements ItemWriter<List<InsiderTransaction>> {
    private final InsiderTransactionDataPort transactionDataPort;  // Port interface

    @Override
    public void write(Chunk<? extends List<InsiderTransaction>> chunk) {
        for (List<InsiderTransaction> transactions : chunk) {
            try {
                transactionDataPort.saveAll(transactions);
            } catch (Exception e) {
                // Fallback: save individually
                for (InsiderTransaction tx : transactions) {
                    try {
                        transactionDataPort.save(tx);
                    } catch (Exception ex) {
                        log.error("Failed to save transaction: {}", tx, ex);
                    }
                }
            }
        }
    }
}
```

### 6.3 Batch Performance in Low Mode

File-based writes are slower than database batch inserts. Add configuration to adjust chunk sizes:

```yaml
# application-resource-low.yml
edgar4j:
  batch:
    chunk-size: ${EDGAR4J_BATCH_CHUNK_SIZE:5}       # Smaller chunks, more frequent flushes
    max-concurrent-steps: 1                           # Single-threaded to avoid file contention
    flush-after-chunk: true                           # Flush file storage after each chunk
```

```yaml
# application-resource-high.yml
edgar4j:
  batch:
    chunk-size: ${EDGAR4J_BATCH_CHUNK_SIZE:50}       # Larger chunks for DB batch inserts
    max-concurrent-steps: 4
    flush-after-chunk: false
```

### 6.4 Batch Job Post-Chunk Hook (Low Mode)

Ensure file storage flushes after each chunk to prevent data loss:

```java
@Component
@Profile("resource-low")
public class FileFlushChunkListener implements ChunkListener {

    private final FileStorageEngine storageEngine;

    @Override
    public void afterChunk(ChunkContext context) {
        storageEngine.flushAll();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        storageEngine.flushAll();  // Persist whatever was written before the error
    }
}
```

### 6.5 Job Scheduling Adjustments

In low mode, reduce job frequency to avoid I/O pressure:

```yaml
# application-resource-low.yml
edgar4j:
  jobs:
    realtime-filing-sync:
      cron: "0 */30 * * * *"    # Every 30 min instead of 15
      max-pages: 5               # Fewer pages per sync
    market-data-sync:
      cron: "0 0 */4 * * MON-FRI"  # Every 4 hours instead of 2
```

## H2 Dependency (Already Present)

H2 is already in `pom.xml` as a test dependency. Move it to compile scope with optional flag:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

## Validation Checklist

- [ ] Spring Batch metadata tables created in H2 (low mode)
- [ ] Spring Batch metadata tables use PostgreSQL (high mode)
- [ ] `InsiderTransactionWriter` saves via port interface in both modes
- [ ] File storage flushed after each batch chunk (low mode)
- [ ] Batch job history survives application restart (H2 file-based)
- [ ] Chunk size is configurable per mode
- [ ] No PostgreSQL connection attempts during batch in low mode

## Estimated Effort

- **Batch DataSource config**: 0.5 day
- **Writer refactoring**: 0.5 day
- **Chunk listener + flush hooks**: 0.5 day
- **Job scheduling adjustments**: 0.5 day
- **Total**: 2 days
