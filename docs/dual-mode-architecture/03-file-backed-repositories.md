# Phase 3: File-Backed Repository Implementations

## Objective

Implement all 27 port interfaces (from Phase 1) using the file storage engine (from Phase 2), providing a fully functional data access layer that requires no external databases.

## Design Pattern: Adapter Pattern

Each file adapter implements a port interface and delegates to a `FileCollection<T>`. Complex queries are handled by in-memory filtering and the index system.

## Package Structure

```
org.jds.edgar4j.adapter.file/
├── Form4FileAdapter.java
├── Form3FileAdapter.java
├── Form5FileAdapter.java
├── Form6KFileAdapter.java
├── Form8KFileAdapter.java
├── Form13DGFileAdapter.java
├── Form13FFileAdapter.java
├── Form20FFileAdapter.java
├── FillingFileAdapter.java
├── CompanyFileAdapter.java
├── CompanyTickerFileAdapter.java
├── CompanyMarketDataFileAdapter.java
├── TickerFileAdapter.java
├── ExchangeFileAdapter.java
├── SubmissionsFileAdapter.java
├── DownloadJobFileAdapter.java
├── SearchHistoryFileAdapter.java
├── AppSettingsFileAdapter.java
├── Sp500ConstituentFileAdapter.java
├── FormTypeFileAdapter.java
├── DailyMasterIndexFileAdapter.java
├── MasterIndexEntryFileAdapter.java
├── insider/
│   ├── InsiderTransactionFileAdapter.java
│   ├── InsiderCompanyFileAdapter.java
│   ├── InsiderFileAdapter.java
│   ├── InsiderCompanyRelationshipFileAdapter.java
│   └── TransactionTypeFileAdapter.java
└── config/
    └── FileAdapterConfiguration.java
```

## Implementation Strategy

### Tier 1: Simple CRUD Adapters (10 repositories)

These repositories have minimal query methods and can be implemented almost entirely by delegating to `FileCollection`.

| Adapter | Format | Index Fields | Estimated LOC |
|---------|--------|-------------|--------------|
| `AppSettingsFileAdapter` | JSON | `key` | ~50 |
| `FormTypeFileAdapter` | JSON | `number` | ~50 |
| `ExchangeFileAdapter` | JSON | `code` | ~50 |
| `SearchHistoryFileAdapter` | JSON | `query`, `timestamp` | ~60 |
| `DownloadJobFileAdapter` | JSON | `status`, `type` | ~80 |
| `Sp500ConstituentFileAdapter` | JSON | `symbol` | ~60 |
| `TransactionTypeFileAdapter` | CSV | `code` | ~50 |
| `CompanyMarketDataFileAdapter` | JSON | `symbol` | ~70 |
| `DailyMasterIndexFileAdapter` | JSONL | `date` | ~70 |
| `MasterIndexEntryFileAdapter` | JSONL | `cik`, `formType` | ~80 |

### Tier 2: Reference Data Adapters (5 repositories)

These have more query methods, require multi-field lookups.

| Adapter | Format | Index Fields | Estimated LOC |
|---------|--------|-------------|--------------|
| `TickerFileAdapter` | JSON | `code`, `cik` | ~100 |
| `CompanyTickerFileAdapter` | JSON | `ticker`, `cikStr` | ~100 |
| `CompanyFileAdapter` | JSON | `cik`, `name`, `ticker` | ~120 |
| `SubmissionsFileAdapter` | JSONL | `cik` | ~100 |
| `ExchangeFileAdapter` | JSON | `code` | ~60 |

### Tier 3: Filing Adapters (8 repositories)

Form-specific adapters that share a common pattern. Use an abstract base class.

```java
/**
 * Base adapter for all SEC form types stored as JSONL files.
 */
public abstract class AbstractFormFileAdapter<T> implements FormDataPort<T> {

    protected final FileCollection<T> collection;

    protected AbstractFormFileAdapter(FileCollection<T> collection) {
        this.collection = collection;
    }

    @Override
    public T save(T form) {
        return collection.save(form);
    }

    @Override
    public Optional<T> findByAccessionNumber(String accessionNumber) {
        return collection.findByIndex("accessionNumber", accessionNumber)
                .stream().findFirst();
    }

    @Override
    public Page<T> findByCik(String cik, Pageable pageable) {
        return collection.query(
                record -> cik.equals(getCik(record)),
                pageable);
    }

    protected abstract String getCik(T record);
    protected abstract String getAccessionNumber(T record);
    // ... template methods for form-specific field access
}
```

| Adapter | Format | Index Fields | Estimated LOC |
|---------|--------|-------------|--------------|
| `Form4FileAdapter` | JSONL | `accessionNumber`, `tradingSymbol`, `cik`, `transactionDate`, `rptOwnerCik` | ~300 |
| `Form3FileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form5FileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form6KFileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form8KFileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form13DGFileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form13FFileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |
| `Form20FFileAdapter` | JSONL | `accessionNumber`, `cik` | ~100 |

### Tier 4: Insider Analytics Adapters (5 repositories)

These replace JPA repositories and handle relational data in flat files.

| Adapter | Format | Index Fields | Estimated LOC |
|---------|--------|-------------|--------------|
| `InsiderTransactionFileAdapter` | Parquet/JSONL | `companyId`, `insiderId`, `transactionDate`, `filingDate` | ~350 |
| `InsiderCompanyFileAdapter` | Parquet/JSONL | `cik`, `ticker` | ~150 |
| `InsiderFileAdapter` | Parquet/JSONL | `cik`, `name` | ~120 |
| `InsiderCompanyRelationshipFileAdapter` | JSONL | `companyId`, `insiderId` | ~100 |
| `TransactionTypeFileAdapter` | CSV | `code` | ~50 |

## Detailed Example: Form4FileAdapter

```java
@Component
@Profile("resource-low")
public class Form4FileAdapter implements Form4DataPort {

    private final FileCollection<Form4> collection;

    public Form4FileAdapter(FileStorageEngine engine) {
        this.collection = engine.registerCollection(
            "form4",
            Form4.class,
            new JsonlFileStrategy<>(),
            List.of(
                IndexDefinition.of("accessionNumber", Form4::getAccessionNumber),
                IndexDefinition.of("tradingSymbol", Form4::getTradingSymbol),
                IndexDefinition.of("cik", Form4::getCik),
                IndexDefinition.of("rptOwnerCik", Form4::getRptOwnerCik),
                IndexDefinition.unique("accessionNumber", Form4::getAccessionNumber)
            )
        );
    }

    @Override
    public Form4 save(Form4 form4) {
        if (form4.getId() == null) {
            form4.setId(IdGenerator.generate());
        }
        return collection.save(form4);
    }

    @Override
    public Page<Form4> findBySymbolAndDateRange(
            String tradingSymbol, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return collection.query(
            form -> tradingSymbol.equalsIgnoreCase(form.getTradingSymbol())
                && form.getTransactionDate() != null
                && !form.getTransactionDate().isBefore(startDate)
                && !form.getTransactionDate().isAfter(endDate),
            pageable
        );
    }

    @Override
    public List<TransactionSummaryProjection> getTransactionSummaryBySymbol(
            String tradingSymbol, LocalDate startDate, LocalDate endDate) {
        // In-memory aggregation replacing MongoDB $group pipeline
        Map<String, DoubleSummaryStatistics> grouped = collection.findAll().stream()
            .filter(f -> tradingSymbol.equalsIgnoreCase(f.getTradingSymbol()))
            .filter(f -> f.getTransactionDate() != null
                && !f.getTransactionDate().isBefore(startDate)
                && !f.getTransactionDate().isAfter(endDate))
            .collect(Collectors.groupingBy(
                f -> f.getAcquiredDisposedCode() != null ? f.getAcquiredDisposedCode() : "U",
                Collectors.summarizingDouble(f ->
                    f.getTransactionValue() != null ? f.getTransactionValue() : 0.0)
            ));

        return grouped.entrySet().stream()
            .map(e -> new FileTransactionSummary(e.getKey(), e.getValue().getSum(), e.getValue().getCount()))
            .collect(Collectors.toList());
    }

    // ... remaining method implementations
}
```

## Handling Relational Concepts in Flat Files

### JPA Relationships → In-Memory Joins

For the insider analytics domain, the JPA entities have `@ManyToOne` relationships. In file mode, these become manual lookups:

```java
@Component
@Profile("resource-low")
public class InsiderTransactionFileAdapter implements InsiderTransactionDataPort {

    private final FileCollection<InsiderTransactionRecord> collection;
    private final FileCollection<InsiderCompanyRecord> companies;
    private final FileCollection<InsiderRecord> insiders;

    @Override
    public Page<InsiderTransaction> findByCompanyTicker(String ticker, Pageable pageable) {
        // 1. Find company by ticker
        Optional<InsiderCompanyRecord> company = companies.findByIndex("ticker", ticker)
                .stream().findFirst();
        if (company.isEmpty()) return Page.empty(pageable);

        // 2. Find transactions by company ID
        String companyId = company.get().getId();
        return collection.query(
            tx -> companyId.equals(tx.getCompanyId()),
            pageable
        ).map(this::toEntity);  // Convert flat record to domain entity
    }
}
```

### Auto-Increment IDs → UUID

JPA entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)`. In file mode, use UUIDs:

```java
public class IdGenerator {
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
```

### Cascading Operations → Explicit

JPA `cascade = CascadeType.ALL` must be handled explicitly in file adapters by saving related entities in the correct order.

## Configuration

```java
@Configuration
@Profile("resource-low")
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileAdapterConfiguration {

    @Bean
    public FileStorageEngine fileStorageEngine(FileStorageProperties properties) {
        return new FileStorageEngine(properties);
    }

    @Bean
    public ObjectMapper fileStorageObjectMapper() {
        return new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

## Limitations in Low-Resource Mode

| Feature | High Mode | Low Mode | Graceful Degradation |
|---------|-----------|----------|---------------------|
| MongoDB aggregation pipelines | Full support | In-memory equivalent | Slower for large datasets |
| JPA @Query with JPQL | Full support | Predicate-based filter | Same results, different performance |
| Text search ($regex) | MongoDB regex | String.contains() | Case-insensitive supported |
| Compound sorting | DB-level | In-memory Comparator | Full support |
| Unique constraints | DB-enforced | Index-checked | Same behavior |
| Transactions (ACID) | Full support | Single-writer lock | No rollback on partial failure |
| Pagination | Cursor-based | Sublist-based | Same API, different internal |

## Validation Checklist

- [ ] Every port interface method has a working file-backed implementation
- [ ] All adapters annotated with `@Profile("resource-low")`
- [ ] All MongoDB adapters annotated with `@Profile("resource-high")`
- [ ] No port has zero implementations (compilation would succeed but runtime would fail)
- [ ] File formats are consistent with the storage directory structure from Phase 0
- [ ] In-memory indexes are rebuilt on startup from existing files

## Estimated Effort

- **10 Tier 1 adapters**: 1 day (simple delegation)
- **5 Tier 2 adapters**: 1 day (multi-field queries)
- **8 Tier 3 adapters**: 2 days (form base class + Form4 complexity)
- **5 Tier 4 adapters**: 2 days (relational mapping)
- **Total**: 6-7 days
