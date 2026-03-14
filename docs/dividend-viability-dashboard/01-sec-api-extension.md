# Phase 1: SEC Company Facts API Extension

## Objective

Extend `SecApiClient` to support the three XBRL-specific SEC endpoints that provide the numerical backbone for the dividend dashboard: **Company Facts**, **Company Concept**, and **Frames**.

## Current State

`SecApiClient` already supports:
- `fetchSubmissions(cik)` — company metadata and filing history
- `fetchCompanyTickers()` — CIK/ticker mapping
- `fetchForm4(cik, accession, document)` — individual filing documents
- `fetchEftsSearch(...)` — full-text filing search
- Rate limiting via `SecRateLimiter` (10 rps)
- Persistent disk caching via `DownloadedResourceStore`
- Gzip/deflate decompression
- Async variants with `CompletableFuture`

The `bulkCompanyFactsFileUrl` is configured in `application.yml` but not yet used.

## New Endpoints to Add

### 1.1 Company Facts Endpoint

```
GET https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json
```

Returns **all** XBRL facts for a company across all filings, organized by taxonomy and concept. This is the single most valuable endpoint for the dividend dashboard — one call gives the full historical time series.

**Response structure:**
```json
{
  "cik": 320193,
  "entityName": "Apple Inc",
  "facts": {
    "dei": {
      "EntityCommonStockSharesOutstanding": {
        "label": "Entity Common Stock, Shares Outstanding",
        "description": "...",
        "units": {
          "shares": [
            {
              "end": "2023-09-30",
              "val": 15552752000,
              "accn": "0000320193-23-000106",
              "fy": 2023,
              "fp": "FY",
              "form": "10-K",
              "filed": "2023-11-02",
              "frame": "CY2023Q3I"
            }
          ]
        }
      }
    },
    "us-gaap": {
      "CommonStockDividendsPerShareDeclared": { ... },
      "PaymentsOfDividendsCommonStock": { ... },
      "NetIncomeLoss": { ... }
    }
  }
}
```

**Implementation:**

```java
// In SecApiClient.java

/**
 * Fetch all XBRL company facts for a CIK.
 * Returns the complete fact time-series across all filings.
 */
public String fetchCompanyFacts(String cik) {
    String normalizedCik = normalizeCik(cik);
    String url = baseDataSecUrl + "/api/xbrl/companyfacts/CIK" + normalizedCik + ".json";
    return fetchWithRateLimitAndCache("company-facts", url);
}

public CompletableFuture<String> fetchCompanyFactsAsync(String cik) {
    return CompletableFuture.supplyAsync(() -> fetchCompanyFacts(cik), executor);
}
```

### 1.2 Company Concept Endpoint

```
GET https://data.sec.gov/api/xbrl/companyconcept/CIK{cik}/{taxonomy}/{tag}.json
```

Returns a **single concept** time series for a company. Use tactically when you need one metric without downloading the full companyfacts payload.

```java
/**
 * Fetch a single XBRL concept time series for a CIK.
 */
public String fetchCompanyConcept(String cik, String taxonomy, String tag) {
    String normalizedCik = normalizeCik(cik);
    String url = String.format("%s/api/xbrl/companyconcept/CIK%s/%s/%s.json",
            baseDataSecUrl, normalizedCik, taxonomy, tag);
    return fetchWithRateLimitAndCache("company-concept", url);
}
```

### 1.3 Frames Endpoint

```
GET https://data.sec.gov/api/xbrl/frames/{taxonomy}/{tag}/{unit}/{period}.json
```

Returns a **cross-sectional** view: one concept for all companies in a calendar period. Useful for peer comparison and sector benchmarking.

```java
/**
 * Fetch cross-sectional XBRL frame data.
 */
public String fetchFrame(String taxonomy, String tag, String unit, String period) {
    String url = String.format("%s/api/xbrl/frames/%s/%s/%s/%s.json",
            baseDataSecUrl, taxonomy, tag, unit, period);
    return fetchWithRateLimitAndCache("frames", url);
}
```

## 1.4 Configuration Updates

Add new URL templates to `application.yml`:

```yaml
edgar4j:
  urls:
    companyFactsUrl: https://data.sec.gov/api/xbrl/companyfacts
    companyConceptUrl: https://data.sec.gov/api/xbrl/companyconcept
    framesUrl: https://data.sec.gov/api/xbrl/frames
```

## 1.5 Response Parsing Models

```java
package org.jds.edgar4j.integration.model;

@Data
public class CompanyFactsResponse {
    private int cik;
    private String entityName;
    private Map<String, Map<String, ConceptFacts>> facts;  // taxonomy -> tag -> facts

    @Data
    public static class ConceptFacts {
        private String label;
        private String description;
        private Map<String, List<FactEntry>> units;  // unit -> fact entries
    }

    @Data
    public static class FactEntry {
        private String end;        // period end date
        private String start;      // period start date (duration facts)
        private double val;        // numeric value
        private String accn;       // accession number
        private int fy;            // fiscal year
        private String fp;         // fiscal period (FY, Q1, Q2, Q3, Q4)
        private String form;       // form type (10-K, 10-Q, 8-K)
        private String filed;      // filing date
        private String frame;      // calendar frame (CY2023Q4I, etc.)
    }
}

@Data
public class FrameResponse {
    private String taxonomy;
    private String tag;
    private String ccp;       // calendar/company period
    private String uom;       // unit of measure
    private String label;
    private String description;
    private List<FrameEntry> data;

    @Data
    public static class FrameEntry {
        private String accn;
        private int cik;
        private String entityName;
        private String loc;
        private String end;
        private double val;
    }
}
```

## 1.6 Caching Strategy

| Endpoint | Cache Namespace | Cache TTL | Rationale |
|---|---|---|---|
| `companyfacts` | `company-facts` | 6 hours | Updated near real-time but payload is large (~1-5MB per company) |
| `companyconcept` | `company-concept` | 1 hour | Small payload, use for point updates |
| `frames` | `frames` | 24 hours | Cross-sectional, changes slowly |

Use `DownloadedResourceStore` (existing) for persistent disk caching. Add TTL-based invalidation:

```java
private String fetchWithRateLimitAndCache(String namespace, String url) {
    Optional<String> cached = downloadedResourceStore.readText(namespace, url, StandardCharsets.UTF_8);
    if (cached.isPresent() && !isCacheExpired(namespace, url)) {
        return cached.get();
    }
    rateLimiter.acquire();
    String response = httpFetch(url);
    downloadedResourceStore.writeText(namespace, url, response, StandardCharsets.UTF_8);
    return response;
}
```

## 1.7 Error Handling

| Error | Handling |
|---|---|
| 404 (CIK not found) | Return empty `CompanyFactsResponse`; log warning |
| 403/429 (rate limited) | Back off 10 minutes (SEC documented cooldown); retry |
| Timeout | Retry once with extended timeout (companyfacts can be large) |
| Malformed JSON | Log error with CIK; return empty response |
| CIK has no XBRL filings | Valid empty response; not an error |

## 1.8 CIK Normalization Helper

```java
/**
 * Normalize CIK to 10-digit zero-padded format.
 * "320193" → "0000320193"
 */
private String normalizeCik(String cik) {
    if (cik == null || cik.isBlank()) throw new IllegalArgumentException("CIK cannot be null/blank");
    String digits = cik.replaceAll("[^0-9]", "");
    return String.format("%010d", Long.parseLong(digits));
}
```

## Validation Checklist

- [ ] `fetchCompanyFacts("320193")` returns Apple's complete fact set
- [ ] `fetchCompanyConcept("320193", "us-gaap", "CommonStockDividendsPerShareDeclared")` returns DPS history
- [ ] `fetchFrame("us-gaap", "CommonStockDividendsPerShareDeclared", "USD-per-shares", "CY2023Q4I")` returns cross-sectional data
- [ ] Rate limiter prevents exceeding 10 rps across all endpoint types
- [ ] Disk cache works for all three endpoints
- [ ] 404 for non-existent CIK returns graceful empty response
- [ ] Async variants work correctly

## Estimated Effort: 3-4 days
