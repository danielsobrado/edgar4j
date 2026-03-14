# Dual-Mode Architecture: Low-Resource & High-Resource Profiles

## Executive Summary

This plan introduces a **profile-based runtime mode** for edgar4j, allowing the application to run in two configurations:

| Aspect | `low` (Lite Mode) | `high` (Full Mode) |
|--------|-------------------|---------------------|
| **Document Store** | JSON files on disk | MongoDB |
| **Relational Store** | CSV / Parquet files (via embedded H2 for Spring Batch metadata) | PostgreSQL |
| **Cache** | Caffeine (in-memory) | Redis |
| **Search** | In-memory linear scan / Lucene embedded | Elasticsearch |
| **Batch Job Metadata** | H2 embedded | PostgreSQL |
| **Target Machine** | Laptop, Raspberry Pi, CI runner | Server, Docker Compose, Kubernetes |

The mode is selected via a single property:

```yaml
edgar4j:
  resource-mode: low   # or "high"
```

Which activates the corresponding Spring profile (`resource-low` / `resource-high`).

---

## Architecture Principles

1. **Repository Abstraction (Strategy Pattern)** - Every data access point goes through a custom interface. Two implementations exist per interface: one file-backed, one database-backed. Spring `@Profile` selects which bean is wired.

2. **Zero Code Changes in Services** - Service classes depend only on the abstraction interfaces. They never import MongoDB, JPA, or file-specific classes.

3. **Profile-Driven Auto-Configuration** - Each mode brings its own `@Configuration` class that registers the correct beans, data sources, and cache managers.

4. **Graceful Degradation** - In `low` mode, features that require full infrastructure (e.g., real-time Elasticsearch queries) degrade gracefully with clear log messages, never throw.

5. **Data Portability** - An export/import utility allows migrating data between modes (e.g., JSON files -> MongoDB).

---

## Current State Analysis

### Data Stores in Use

| Store | Collections / Tables | Repository Count | Coupled Services |
|-------|---------------------|-----------------|-----------------|
| MongoDB | 22 collections | 22 repositories (`MongoRepository`) | ~15 services |
| PostgreSQL | 5 tables (insider schema) | 5 repositories (`JpaRepository`) | ~5 services |
| Redis | Cache keys | 0 (via `@Cacheable`) | ~4 services |
| Elasticsearch | Disabled | 0 | 0 |

### Key Coupling Points

- `Form4Repository` extends `MongoRepository<Form4, String>` - 40+ query methods
- `InsiderTransactionRepository` extends `JpaRepository` - 30+ query methods
- `FillingRepository` extends `MongoRepository` - used by ExportService, Form4Service, FilingService
- `CompanyRepository` (MongoDB) - used by CompanyService, enrichment
- `insider.CompanyRepository` (JPA) - used by insider analytics
- Spring Batch requires a relational `JobRepository` (currently PostgreSQL)
- Redis `@Cacheable` annotations on multiple services

### Dependency Graph

```
Controllers
    └── Services (Form4Service, CompanyService, ExportService, etc.)
           ├── MongoDB Repositories (22)   ← NEEDS ABSTRACTION
           ├── JPA Repositories (5)        ← NEEDS ABSTRACTION
           ├── Redis Cache                 ← NEEDS PROFILE SWITCH
           └── Integration Clients (SEC API) ← NO CHANGE NEEDED
```

---

## Phase Overview

| Phase | Title | Scope | Dependencies |
|-------|-------|-------|-------------|
| [Phase 1](01-repository-abstraction.md) | Repository Abstraction Layer | Define custom interfaces, refactor services | None |
| [Phase 2](02-file-storage-engine.md) | File-Based Storage Engine | JSON/CSV/Parquet read/write engine | Phase 1 |
| [Phase 3](03-file-backed-repositories.md) | File-Backed Repository Implementations | Implement all 27 repos with file storage | Phase 1, 2 |
| [Phase 4](04-profile-configuration.md) | Profile & Auto-Configuration | Spring profiles, property-driven bean selection | Phase 1, 3 |
| [Phase 5](05-cache-abstraction.md) | Cache Abstraction | Caffeine vs Redis profile switching | Phase 4 |
| [Phase 6](06-batch-adaptation.md) | Batch Processing Adaptation | H2-based batch metadata, file-based writers | Phase 4 |
| [Phase 7](07-search-abstraction.md) | Search Abstraction | In-memory search vs Elasticsearch | Phase 4 |
| [Phase 8](08-data-migration-tools.md) | Data Migration & Portability | Export/import between modes | Phase 3 |
| [Phase 9](09-testing-strategy.md) | Testing Strategy | Dual-mode test suites, integration tests | Phase 4 |
| [Phase 10](10-documentation-and-devops.md) | Documentation & DevOps | Docker profiles, README, CI matrix | Phase 9 |

---

## File Format Decisions

| Current Store | Low-Resource Format | Rationale |
|--------------|-------------------|-----------|
| MongoDB documents (Forms, Companies, Tickers) | **JSON files** (one file per collection, JSONL for large collections) | Natural 1:1 mapping from BSON; human-readable; easy debugging |
| PostgreSQL tables (InsiderTransaction, Company, Insider) | **Parquet files** | Columnar format; efficient for analytics queries; compressed; type-safe |
| PostgreSQL tables (TransactionType, small lookups) | **CSV files** | Simple, editable, small datasets |
| Redis cache entries | **Caffeine in-memory cache** | No persistence needed for cache |
| Spring Batch metadata | **H2 embedded database** | Spring Batch requires JDBC; H2 is zero-install |

### Storage Directory Structure (Low Mode)

```
${edgar4j.storage.base-path:./data}/
├── collections/              # MongoDB replacement
│   ├── form4.jsonl
│   ├── form3.jsonl
│   ├── form5.jsonl
│   ├── form6k.jsonl
│   ├── form8k.jsonl
│   ├── form13dg.jsonl
│   ├── form13f.jsonl
│   ├── form20f.jsonl
│   ├── fillings.jsonl
│   ├── companies.json
│   ├── company_tickers.json
│   ├── company_market_data.json
│   ├── submissions.jsonl
│   ├── tickers.json
│   ├── exchanges.json
│   ├── download_jobs.json
│   ├── search_history.json
│   ├── app_settings.json
│   ├── sp500_constituents.json
│   ├── form_types.json
│   ├── daily_master_index.jsonl
│   └── master_index_entry.jsonl
├── tables/                   # PostgreSQL replacement
│   ├── insider_transactions.parquet
│   ├── companies.parquet
│   ├── insiders.parquet
│   ├── insider_company_relationships.parquet
│   └── transaction_types.csv
├── indexes/                  # In-memory index snapshots
│   ├── form4_by_symbol.idx
│   └── form4_by_date.idx
├── cache/                    # Disk-backed cache (optional)
│   └── market_data/
└── batch/                    # H2 Spring Batch metadata
    └── batch_metadata.mv.db
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| File-based queries are slower than DB indexes | Medium | In-memory indexes, lazy loading, pagination limits |
| Large datasets exceed memory in low mode | High | JSONL streaming, Parquet columnar reads, configurable max records |
| Concurrent file writes cause corruption | Medium | File locks, write-ahead log, single-writer pattern |
| Feature parity gaps between modes | Medium | Graceful degradation, clear documentation of limitations |
| Spring Batch requires JDBC | Low | H2 embedded is well-tested with Spring Batch |

---

## Success Criteria

1. `mvn spring-boot:run -Dedgar4j.resource-mode=low` starts with **zero external dependencies**
2. All REST endpoints return valid responses in both modes
3. All existing tests pass in both modes (CI matrix)
4. Data can be exported from `high` mode and imported into `low` mode
5. Memory footprint in `low` mode stays under 512MB for typical datasets
6. No service-layer code contains `if (mode == low)` conditionals
