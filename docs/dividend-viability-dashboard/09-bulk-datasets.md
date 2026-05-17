# Phase 9: SEC Bulk Notes Datasets (Optional Enhancement)

## Objective

Ingest the SEC's "Financial Statement and Notes" bulk datasets for broader coverage of dimensioned facts, custom tags, and note-level disclosures. This is optional but highly valuable at scale — the bulk datasets cover cases that the real-time XBRL APIs miss.

## Current Implementation Status

Implemented local coverage:

- NUM table parser handles tab-separated rows, blank optional fields, dates, numeric values, dimensions, and `iprx`.
- Custom dividend-like tags are detected from TAG table rows via the custom/version metadata.
- Dividend dimensions are classified as consolidated, common, preferred, special, other, or unknown.
- `iprx` de-duplication selects the preferred priority-1 fact for identical accession/tag/period/unit/dimension keys.
- Normalized fact records now carry source/tag-version/custom-tag/quarter-count/inline-priority metadata for API/BULK merging.

Remaining implementation/operational validation:

- Full quarterly/monthly notes ZIP download and extraction is not wired to normalized fact ingestion yet.
- API + BULK merge/current-best recomputation is prepared at the model level but not fully ingested from SEC notes files.

## When to Use Bulk Datasets vs Company Facts API

| Scenario | Company Facts API | Bulk Datasets |
|---|---|---|
| Small universe (< 500 companies) | Preferred — lower latency | Overkill |
| Medium universe (500-5000) | Works but rate-limited at scale | Good for nightly batch refresh |
| Large universe (5000+) | Too many API calls | Required for efficiency |
| Custom taxonomy extensions | Not included in API | Included with version field |
| Dimensional disclosures (preferred dividends by class) | Limited to consolidated | Full dimensional coverage |
| Note-level text facts | Not available | Available via TXT table |

## SEC Bulk Dataset Structure

The SEC publishes quarterly ZIP files containing flattened XBRL data in tab-separated files. Updated monthly with a rolling year of monthlies.

### Key Tables

| File | Purpose | Key Fields | Relevance to Dividends |
|---|---|---|---|
| `sub.txt` | Submissions metadata | cik, name, form, period, filed, accepted | Filing dimension |
| `tag.txt` | Tag definitions (standard + custom) | tag, version, custom, datatype, iord | Detect custom dividend tags |
| `num.txt` | Numeric facts | adsh, tag, version, ddate, qtrs, uom, value, dimh | Primary dividend data source |
| `txt.txt` | Text facts (notes) | adsh, tag, version, ddate, dimh, value | Dividend policy footnotes |
| `dim.txt` | Dimension details | dimh, dimn, dima | DividendsAxis members |
| `ren.txt` | Rendering metadata | Report, rfile | Statement presentation |
| `pre.txt` | Presentation line items | adsh, report, line, stmt, tag, plabel | Display order |
| `cal.txt` | Calculation arcs | adsh, grp, arc, negative, ptag, pversion, ctag, cversion | Validation |

### NUM Table Key Fields (Most Important)

```
adsh      accession number (submission key)
tag       XBRL tag name
version   taxonomy version (custom tags use accession as version)
coreg     co-registrant (NULL = consolidated entity)
ddate     period end date
qtrs      number of quarters (0 = instant, 1 = Q, 4 = FY)
uom       unit of measure
value     numeric value
dimh      dimension hash (0x00000000 = no dimensions)
iprx      in-line XBRL priority (1 = preferred; >1 generally not needed)
```

## Data Model Extension

The existing `fact_xbrl` table handles bulk dataset facts — add source tracking:

```java
// Additional fields for bulk dataset facts
public class XbrlFactEntity {
    // ... existing fields ...

    private String source;          // "API" or "BULK"
    private String tagVersion;      // taxonomy version (for custom tag detection)
    private boolean isCustomTag;    // true if version = accession (custom extension)
    private int quartersCount;      // 0 = instant, 1 = quarter, 4 = annual
    private int iprx;              // inline priority (1 = preferred)
}
```

## Ingestion Pipeline

### 9.1 Bulk Download Service

```java
@Service
@RequiredArgsConstructor
public class BulkDatasetIngestionService {

    private final DownloadedResourceStore cache;

    /**
     * Download and extract a quarterly bulk dataset.
     * URL pattern: https://www.sec.gov/files/dera/data/financial-statement-and-notes-data-sets/{year}q{quarter}.zip
     */
    public BulkDatasetFiles download(int year, int quarter) {
        String url = String.format(
            "https://www.sec.gov/files/dera/data/financial-statement-and-notes-data-sets/%dq%d.zip",
            year, quarter);

        byte[] zipBytes = fetchLargeFile(url);
        return extractZip(zipBytes);
    }

    /**
     * Download monthly companion dataset.
     * URL pattern: https://www.sec.gov/files/dera/data/financial-statement-and-notes-data-sets/{year}_{month}_notes.zip
     */
    public BulkDatasetFiles downloadMonthly(int year, int month) {
        String url = String.format(
            "https://www.sec.gov/files/dera/data/financial-statement-and-notes-data-sets/%d_%02d_notes.zip",
            year, month);
        byte[] zipBytes = fetchLargeFile(url);
        return extractZip(zipBytes);
    }
}
```

### 9.2 NUM Parser

```java
@Component
public class NumFileParser {

    /**
     * Parse num.txt tab-separated file.
     * Filter to relevant tags and tracked CIKs.
     */
    public Stream<NumRecord> parse(Path numFile, Set<String> trackedCiks, Set<String> relevantTags) {
        return Files.lines(numFile, StandardCharsets.UTF_8)
                .skip(1)  // header
                .map(line -> line.split("\t"))
                .filter(fields -> fields.length >= 10)
                .map(this::toNumRecord)
                .filter(record -> trackedCiks.isEmpty() || trackedCiks.contains(record.getCik()))
                .filter(record -> relevantTags.contains(record.getTag()));
    }

    private NumRecord toNumRecord(String[] fields) {
        NumRecord record = new NumRecord();
        record.setAccession(fields[0]);
        record.setTag(fields[1]);
        record.setVersion(fields[2]);
        record.setCoreg(fields[3].isEmpty() ? null : fields[3]);
        record.setDdate(LocalDate.parse(fields[4], DateTimeFormatter.BASIC_ISO_DATE));
        record.setQtrs(Integer.parseInt(fields[5]));
        record.setUom(fields[6]);
        record.setValue(new BigDecimal(fields[7]));
        // dimh, iprx fields...
        return record;
    }
}
```

### 9.3 Custom Tag Detection

The bulk dataset's TAG table identifies custom vs standard tags:

```java
@Component
public class CustomTagDetector {

    /**
     * Detect custom dividend-related tags from the TAG table.
     * Custom tags have version = accession number (not a taxonomy like "us-gaap/2023").
     */
    public List<CustomDividendTag> findCustomDividendTags(Path tagFile) {
        return Files.lines(tagFile, StandardCharsets.UTF_8)
                .skip(1)
                .map(line -> line.split("\t"))
                .filter(f -> f[2].equals("1"))  // custom = 1
                .filter(f -> isDividendRelated(f[0]))  // tag name contains dividend keywords
                .map(f -> new CustomDividendTag(f[0], f[1]))  // tag, version
                .toList();
    }

    private boolean isDividendRelated(String tagName) {
        String lower = tagName.toLowerCase();
        return lower.contains("dividend") || lower.contains("distribution")
                || lower.contains("payout") || lower.contains("declared");
    }
}
```

### 9.4 Dimension Handling for Preferred Dividends

The DividendsAxis in the DIM table distinguishes common vs preferred dividends:

```java
/**
 * Parse dimension details to separate common vs preferred dividends.
 * DividendsAxis members like "CommonStockMember", "PreferredStockMember",
 * "SeriesAPreferredStockMember", etc.
 */
public DividendClassification classifyByDimension(String dimHash, Map<String, DimRecord> dimLookup) {
    if ("0x00000000".equals(dimHash)) {
        return DividendClassification.CONSOLIDATED;  // no dimensions
    }

    DimRecord dim = dimLookup.get(dimHash);
    if (dim == null) return DividendClassification.UNKNOWN;

    String member = dim.getDimensionMember().toLowerCase();
    if (member.contains("preferred")) return DividendClassification.PREFERRED;
    if (member.contains("common")) return DividendClassification.COMMON;
    if (member.contains("special")) return DividendClassification.SPECIAL;
    return DividendClassification.OTHER;
}
```

### 9.5 iprx De-duplication

The SEC documents that `iprx` (inline XBRL priority) resolves fact duplicates:

```java
/**
 * Apply iprx-based de-duplication.
 * Facts with iprx = 1 are preferred. Facts with iprx > 1 are generally redundant
 * (lower precision or less well-matched period).
 */
public List<NumRecord> deduplicateByIprx(List<NumRecord> facts) {
    return facts.stream()
            .collect(Collectors.groupingBy(
                    f -> f.getAccession() + "|" + f.getTag() + "|" + f.getDdate() + "|" + f.getUom() + "|" + f.getDimHash()))
            .values().stream()
            .map(group -> group.stream()
                    .min(Comparator.comparingInt(NumRecord::getIprx))
                    .orElseThrow())
            .toList();
}
```

## Batch Ingestion Job

```java
@Component
@RequiredArgsConstructor
public class BulkDatasetSyncJob {

    /**
     * Run monthly after SEC publishes the new monthly notes dataset.
     * Typically available by the 15th of the following month.
     */
    @Scheduled(cron = "0 0 3 16 * *")  // 3 AM on the 16th of each month
    public void ingestMonthlyBulkDataset() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int year = lastMonth.getYear();
        int month = lastMonth.getMonthValue();

        BulkDatasetFiles files = bulkDownloadService.downloadMonthly(year, month);

        // Parse and ingest only for tracked CIKs
        Set<String> trackedCiks = syncStateRepository.getAllTrackedCiks();

        numFileParser.parse(files.getNumFile(), trackedCiks, RELEVANT_TAGS)
                .forEach(record -> {
                    XbrlFactEntity fact = convertToEntity(record);
                    fact.setSource("BULK");
                    xbrlFactRepository.upsert(fact);
                });

        // Recompute current-best for affected CIKs
        trackedCiks.forEach(cik -> xbrlFactRepository.markCurrentBest(cik));
    }
}
```

## Storage Sizing

| Dataset | Compressed Size | Extracted Size | Facts per Quarter |
|---|---|---|---|
| Quarterly ZIP | ~200-400 MB | ~2-4 GB | ~15-20M rows |
| Monthly ZIP | ~50-100 MB | ~500MB-1GB | ~3-5M rows |
| Filtered (tracked CIKs only) | << 1% of full | Depends on universe | Proportional |

## Validation Checklist

- [ ] Quarterly bulk dataset downloads and extracts correctly
- [x] NUM parser handles tab-separated format with edge cases
- [x] Custom tags detected via version field
- [x] Dimensional facts correctly classified (common vs preferred dividends)
- [x] iprx de-duplication selects priority 1 facts
- [ ] Bulk-ingested facts merge correctly with API-ingested facts
- [ ] Current-best selection handles mixed API + BULK sources
- [ ] Monthly job runs without blocking daily sync jobs

## Estimated Effort: 4-5 days
