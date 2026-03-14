# Phase 8: Data Migration & Portability

## Objective

Provide tooling to export data from high-resource mode (MongoDB/PostgreSQL) into low-resource format (JSON/CSV/Parquet files) and vice versa. This enables:

1. **Seed low-mode deployments** from an existing production database
2. **Migrate from low to high** when scaling up
3. **Backup and restore** in a portable format
4. **Development workflow**: pull production data into a local file-based setup

## Architecture

```
┌─────────────────────────────────────────┐
│         DataMigrationService            │
│                                         │
│  exportAll(targetDir)                   │
│  importAll(sourceDir)                   │
│  exportCollection(name, targetDir)      │
│  importCollection(name, sourceDir)      │
│  validate(sourceDir) → ValidationReport │
└────────────┬────────────────────────────┘
             │
      ┌──────┴──────┐
      ▼             ▼
 ┌─────────┐  ┌──────────┐
 │  Source  │  │  Target  │
 │  Ports   │  │  Ports   │
 │(DataPort)│  │(DataPort)│
 └─────────┘  └──────────┘
```

## CLI Commands

Implemented as Spring Boot `ApplicationRunner` triggered by a property:

```bash
# Export from MongoDB/PostgreSQL to files
java -jar edgar4j.jar \
  --edgar4j.resource-mode=high \
  --edgar4j.migration.action=export \
  --edgar4j.migration.target-path=./data-export

# Import from files to MongoDB/PostgreSQL
java -jar edgar4j.jar \
  --edgar4j.resource-mode=high \
  --edgar4j.migration.action=import \
  --edgar4j.migration.source-path=./data-export

# Validate export directory
java -jar edgar4j.jar \
  --edgar4j.migration.action=validate \
  --edgar4j.migration.source-path=./data-export

# Export specific collections only
java -jar edgar4j.jar \
  --edgar4j.resource-mode=high \
  --edgar4j.migration.action=export \
  --edgar4j.migration.collections=form4,companies,tickers \
  --edgar4j.migration.target-path=./data-export
```

## REST API Endpoints (Optional)

```java
@RestController
@RequestMapping("/api/admin/migration")
@Profile("resource-high")  // Only available when DB is accessible
public class DataMigrationController {

    @PostMapping("/export")
    public Mono<MigrationStatus> exportData(@RequestBody ExportRequest request) { ... }

    @PostMapping("/import")
    public Mono<MigrationStatus> importData(@RequestBody ImportRequest request) { ... }

    @GetMapping("/status/{jobId}")
    public Mono<MigrationStatus> getStatus(@PathVariable String jobId) { ... }
}
```

## Export Formats

| Collection | Export Format | Reasoning |
|-----------|-------------|-----------|
| All MongoDB collections | JSONL (one file per collection) | 1:1 document mapping |
| insider_transactions | Parquet | Columnar, efficient for large tables |
| companies (JPA) | Parquet | Typed, compressed |
| insiders (JPA) | Parquet | Typed, compressed |
| insider_company_relationships | JSONL | Small, simple |
| transaction_types | CSV | Human-editable reference data |

## Export Manifest

Each export produces a `manifest.json`:

```json
{
  "version": "1.0",
  "exportDate": "2026-03-14T10:30:00Z",
  "sourceMode": "high",
  "edgar4jVersion": "0.0.1-SNAPSHOT",
  "collections": {
    "form4": {
      "file": "collections/form4.jsonl",
      "format": "jsonl",
      "recordCount": 45230,
      "checksum": "sha256:abc123...",
      "exportedAt": "2026-03-14T10:30:15Z"
    },
    "insider_transactions": {
      "file": "tables/insider_transactions.parquet",
      "format": "parquet",
      "recordCount": 128450,
      "checksum": "sha256:def456...",
      "exportedAt": "2026-03-14T10:31:20Z"
    }
  }
}
```

## Data Transformation Rules

### MongoDB → File

- `_id` (ObjectId) → String `id` field
- `_class` discriminator field → stripped (not needed in file format)
- Dates stored as ISO-8601 strings
- Nested documents preserved as-is in JSON

### JPA Entity → File

- `@Id` with `GenerationType.IDENTITY` → UUID string
- `@ManyToOne` relationships → foreign key string (entity ID)
- `@Enumerated` → string value
- `@Temporal` → ISO-8601 string
- Transient fields → excluded

### File → MongoDB

- String `id` → new ObjectId (or preserve if valid ObjectId format)
- ISO-8601 dates → `ISODate()`
- All other fields → direct mapping

### File → JPA

- UUID string → auto-generated Long ID (new identity)
- Foreign key strings → resolved to entity references
- ID mapping table maintained during import for relationship resolution

## Implementation

```java
@Service
public class DataMigrationService {

    private final Map<String, DataPort<?>> allPorts;
    private final ObjectMapper objectMapper;

    /**
     * Export all collections to target directory.
     * Streams records to avoid memory pressure with large collections.
     */
    public MigrationReport exportAll(Path targetDir) {
        MigrationReport report = new MigrationReport();
        Files.createDirectories(targetDir.resolve("collections"));
        Files.createDirectories(targetDir.resolve("tables"));

        // Export MongoDB collections
        for (var entry : mongoCollections.entrySet()) {
            String name = entry.getKey();
            exportCollection(name, targetDir, report);
        }

        // Export JPA tables
        for (var entry : jpaTables.entrySet()) {
            String name = entry.getKey();
            exportTable(name, targetDir, report);
        }

        writeManifest(targetDir, report);
        return report;
    }

    private <T> void exportCollection(String name, Path targetDir, MigrationReport report) {
        Path outputFile = targetDir.resolve("collections").resolve(name + ".jsonl");

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            long count = 0;
            int page = 0;
            Page<T> pageResult;

            do {
                pageResult = dataPort.findAll(PageRequest.of(page, 1000));
                for (T record : pageResult.getContent()) {
                    writer.write(objectMapper.writeValueAsString(record));
                    writer.newLine();
                    count++;
                }
                page++;
            } while (pageResult.hasNext());

            report.addCollection(name, count, outputFile);
        }
    }
}
```

## Validation & Integrity Checks

```java
public record ValidationReport(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    Map<String, Long> recordCounts,
    Map<String, String> checksumMismatches
) {}
```

Validation checks:
- Manifest exists and is parseable
- All referenced files exist
- SHA-256 checksums match
- Record counts match manifest
- Required collections present (at minimum: `form4`, `companies`, `tickers`)
- JSON/JSONL lines are valid JSON
- Parquet files are readable

## Estimated Effort

- **Export service**: 2 days
- **Import service**: 2 days
- **Manifest + validation**: 1 day
- **CLI runner**: 0.5 day
- **REST endpoints (optional)**: 0.5 day
- **Total**: 5-6 days
