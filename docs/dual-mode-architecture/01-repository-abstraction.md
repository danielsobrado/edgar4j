# Phase 1: Repository Abstraction Layer

## Objective

Decouple all service classes from Spring Data MongoDB / JPA repository interfaces by introducing custom domain repository interfaces in a new `port` package. This is the foundational phase that enables all subsequent work.

## Design Pattern: Ports & Adapters (Hexagonal Architecture)

```
┌─────────────────────────────────────────────────────┐
│  Service Layer (unchanged)                          │
│  Form4ServiceImpl, CompanyServiceImpl, etc.         │
│         │                                           │
│         ▼                                           │
│  ┌─────────────────────────────┐                    │
│  │  Port Interfaces            │                    │
│  │  (org.jds.edgar4j.port)     │                    │
│  │  Form4DataPort              │                    │
│  │  CompanyDataPort            │                    │
│  │  InsiderTransactionDataPort │                    │
│  │  ...                        │                    │
│  └──────────┬──────────────────┘                    │
│             │                                       │
│      ┌──────┴──────┐                                │
│      ▼             ▼                                │
│  ┌────────┐  ┌──────────┐                           │
│  │ Mongo  │  │  File    │                           │
│  │Adapter │  │ Adapter  │                           │
│  └────────┘  └──────────┘                           │
└─────────────────────────────────────────────────────┘
```

## Package Structure

```
org.jds.edgar4j.port/
├── Form4DataPort.java
├── Form3DataPort.java
├── Form5DataPort.java
├── Form6KDataPort.java
├── Form8KDataPort.java
├── Form13DGDataPort.java
├── Form13FDataPort.java
├── Form20FDataPort.java
├── FillingDataPort.java
├── CompanyDataPort.java
├── CompanyTickerDataPort.java
├── CompanyMarketDataPort.java
├── TickerDataPort.java
├── ExchangeDataPort.java
├── SubmissionsDataPort.java
├── DownloadJobDataPort.java
├── SearchHistoryDataPort.java
├── AppSettingsDataPort.java
├── Sp500ConstituentDataPort.java
├── FormTypeDataPort.java
├── DailyMasterIndexDataPort.java
├── MasterIndexEntryDataPort.java
├── insider/
│   ├── InsiderTransactionDataPort.java
│   ├── InsiderCompanyDataPort.java
│   ├── InsiderDataPort.java
│   ├── InsiderCompanyRelationshipDataPort.java
│   └── TransactionTypeDataPort.java
```

## Step-by-Step Implementation

### Step 1.1: Define Port Interfaces

Each port interface exposes only the methods that services actually call. Do **not** expose Spring Data internals like `flush()`, `getReferenceById()`, etc.

**Example: `Form4DataPort.java`**

```java
package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form4;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data access port for Form 4 filings.
 * Implementations may be backed by MongoDB, file storage, or any other store.
 */
public interface Form4DataPort {

    Form4 save(Form4 form4);
    List<Form4> saveAll(List<Form4> form4List);

    Optional<Form4> findById(String id);
    Optional<Form4> findByAccessionNumber(String accessionNumber);
    List<Form4> findByAccessionNumberIn(List<String> accessionNumbers);
    boolean existsByAccessionNumber(String accessionNumber);

    List<Form4> findByTradingSymbol(String tradingSymbol);
    Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable);

    List<Form4> findByCik(String cik);
    Page<Form4> findByCik(String cik, Pageable pageable);

    List<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName);
    Page<Form4> findByRptOwnerNameContainingIgnoreCase(String ownerName, Pageable pageable);

    List<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);
    Page<Form4> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Form4> findBySymbolAndDateRange(String tradingSymbol, LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<Form4> findByTradingSymbolAndTransactionDateBetween(String tradingSymbol, LocalDate startDate, LocalDate endDate);

    Page<Form4> findByIsDirectorTrue(Pageable pageable);
    Page<Form4> findByIsOfficerTrue(Pageable pageable);
    Page<Form4> findByIsTenPercentOwnerTrue(Pageable pageable);

    Page<Form4> findByMinTransactionValue(Float minValue, Pageable pageable);
    List<Form4> findLargeTransactionsBySymbol(String tradingSymbol, Float minValue);

    List<Form4> findRecentAcquisitions(LocalDate since);

    long countBuys();
    long countSells();
    long countBuysBySymbol(String tradingSymbol);
    long countSellsBySymbol(String tradingSymbol);

    Page<Form4> findAll(Pageable pageable);
    void deleteById(String id);

    List<TransactionSummaryProjection> getTransactionSummaryBySymbol(
            String tradingSymbol, LocalDate startDate, LocalDate endDate);

    /**
     * Projection interface for aggregated transaction stats.
     */
    interface TransactionSummaryProjection {
        String getId();
        Double getTotalValue();
        Long getCount();
    }
}
```

### Step 1.2: Create MongoDB Adapter (Wrap Existing Repos)

Each MongoDB repository becomes an internal detail of its adapter. The adapter implements the port and delegates.

```java
package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.repository.Form4Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
// ... imports

@Component
@Profile("resource-high")
public class Form4MongoAdapter implements Form4DataPort {

    private final Form4Repository form4Repository;

    public Form4MongoAdapter(Form4Repository form4Repository) {
        this.form4Repository = form4Repository;
    }

    @Override
    public Form4 save(Form4 form4) {
        return form4Repository.save(form4);
    }

    @Override
    public Optional<Form4> findByAccessionNumber(String accessionNumber) {
        return form4Repository.findByAccessionNumber(accessionNumber);
    }

    // ... delegate all methods 1:1
}
```

### Step 1.3: Refactor Services to Use Ports

Replace all `*Repository` injections with `*DataPort` injections.

**Before:**
```java
@RequiredArgsConstructor
public class Form4ServiceImpl implements Form4Service {
    private final Form4Repository form4Repository;
    private final FillingRepository fillingRepository;
    private final TickerRepository tickerRepository;
    // ...
}
```

**After:**
```java
@RequiredArgsConstructor
public class Form4ServiceImpl implements Form4Service {
    private final Form4DataPort form4DataPort;
    private final FillingDataPort fillingDataPort;
    private final TickerDataPort tickerDataPort;
    // ...
}
```

### Step 1.4: Refactor Batch Components

Update `InsiderTransactionWriter`, `EdgarFilingReader`, and `Form4DocumentProcessor` to use ports instead of repositories.

### Step 1.5: Move Spring Data Repositories

Move all `@Repository` interfaces into adapter sub-packages so they are not accidentally imported by services:

```
org.jds.edgar4j.adapter.mongo/
├── Form4MongoAdapter.java
├── internal/
│   └── Form4MongoRepository.java  (was: Form4Repository)
```

## Inventory of All Repositories to Abstract

### MongoDB Repositories (22)

| # | Current Repository | New Port Interface | Used By |
|---|-------------------|-------------------|---------|
| 1 | `Form4Repository` | `Form4DataPort` | Form4ServiceImpl, DashboardServiceImpl |
| 2 | `Form3Repository` | `Form3DataPort` | Form3ServiceImpl |
| 3 | `Form5Repository` | `Form5DataPort` | Form5ServiceImpl |
| 4 | `Form6KRepository` | `Form6KDataPort` | Form6KServiceImpl |
| 5 | `Form8KRepository` | `Form8KDataPort` | Form8KServiceImpl |
| 6 | `Form13DGRepository` | `Form13DGDataPort` | Form13DGServiceImpl |
| 7 | `Form13FRepository` | `Form13FDataPort` | Form13FServiceImpl |
| 8 | `Form20FRepository` | `Form20FDataPort` | Form20FServiceImpl |
| 9 | `FillingRepository` | `FillingDataPort` | ExportServiceImpl, Form4ServiceImpl, FilingServiceImpl |
| 10 | `CompanyRepository` | `CompanyDataPort` | CompanyServiceImpl, enrichment |
| 11 | `CompanyTickerRepository` | `CompanyTickerDataPort` | DownloadTickersServiceImpl |
| 12 | `CompanyMarketDataRepository` | `CompanyMarketDataPort` | CompanyMarketDataServiceImpl |
| 13 | `TickerRepository` | `TickerDataPort` | Form4ServiceImpl, DownloadTickersServiceImpl |
| 14 | `ExchangeRepository` | `ExchangeDataPort` | ExchangeServiceImpl |
| 15 | `SubmissionsRepository` | `SubmissionsDataPort` | DownloadSubmissionsServiceImpl |
| 16 | `DownloadJobRepository` | `DownloadJobDataPort` | DownloadJobServiceImpl |
| 17 | `SearchHistoryRepository` | `SearchHistoryDataPort` | SearchHistoryServiceImpl |
| 18 | `AppSettingsRepository` | `AppSettingsDataPort` | SettingsServiceImpl |
| 19 | `Sp500ConstituentRepository` | `Sp500ConstituentDataPort` | Sp500ServiceImpl |
| 20 | `FormTypeRepository` | `FormTypeDataPort` | FormTypeServiceImpl |
| 21 | `DailyMasterIndexRepository` | `DailyMasterIndexDataPort` | MasterIndexServiceImpl |
| 22 | `MasterIndexEntryRepository` | `MasterIndexEntryDataPort` | MasterIndexServiceImpl |

### JPA Repositories (5)

| # | Current Repository | New Port Interface | Used By |
|---|-------------------|-------------------|---------|
| 23 | `insider.CompanyRepository` | `InsiderCompanyDataPort` | InsiderCompanyServiceImpl |
| 24 | `insider.InsiderRepository` | `InsiderDataPort` | InsiderServiceImpl |
| 25 | `insider.InsiderTransactionRepository` | `InsiderTransactionDataPort` | InsiderTransactionServiceImpl, Writer |
| 26 | `insider.InsiderCompanyRelationshipRepository` | `InsiderCompanyRelationshipDataPort` | InsiderCompanyServiceImpl |
| 27 | `insider.TransactionTypeRepository` | `TransactionTypeDataPort` | TransactionTypeServiceImpl |

## Migration Order

Refactor in order of coupling complexity (least coupled first):

1. **Standalone lookups**: `AppSettingsRepository`, `FormTypeRepository`, `ExchangeRepository`, `SearchHistoryRepository`
2. **Reference data**: `TickerRepository`, `CompanyTickerRepository`, `Sp500ConstituentRepository`
3. **Core documents**: `Form3/5/6K/8K/13DG/13F/20F Repositories` (simpler than Form4)
4. **High-traffic**: `Form4Repository`, `FillingRepository`, `CompanyRepository`
5. **Insider (JPA)**: All 5 JPA repositories
6. **Batch**: `DownloadJobRepository`, `SubmissionsRepository`, `DailyMasterIndexRepository`, `MasterIndexEntryRepository`

## Validation Checklist

- [ ] All services compile with only port interfaces (no Spring Data imports)
- [ ] All existing tests pass with MongoDB adapters (behavioral equivalence)
- [ ] No repository interface is imported outside the `adapter` package
- [ ] Port interfaces contain no Spring Data-specific types (except `Page`/`Pageable` which are generic enough)

## Estimated Effort

- **27 port interfaces** to define
- **27 MongoDB/JPA adapter classes** to create (thin delegation)
- **~20 service classes** to refactor imports
- **~5 batch classes** to refactor
- **Estimated duration**: 3-4 days for an experienced developer
