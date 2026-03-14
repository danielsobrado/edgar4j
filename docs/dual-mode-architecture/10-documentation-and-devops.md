# Phase 10: Documentation & DevOps

## Objective

Provide user-facing documentation, Docker Compose profiles, and CI/CD pipelines that support both resource modes seamlessly.

## Docker Compose Profiles

### docker-compose.yml (Unified)

```yaml
version: '3.9'

services:
  # ── Application ──────────────────────────────────
  edgar4j:
    build: .
    ports:
      - "8080:8080"
    environment:
      - EDGAR4J_RESOURCE_MODE=${EDGAR4J_RESOURCE_MODE:-high}
      - SEC_USER_AGENT=${SEC_USER_AGENT}
    volumes:
      - edgar4j-data:/app/data    # Used in low mode
    profiles: ["low", "high"]
    depends_on:
      mongodb:
        condition: service_healthy
        required: false
      postgres:
        condition: service_healthy
        required: false

  # ── High-Mode Infrastructure ─────────────────────
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
    profiles: ["high"]

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: edgar4j
      POSTGRES_USER: edgar4j
      POSTGRES_PASSWORD: edgar4j
    ports:
      - "5432:5432"
    volumes:
      - pg-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U edgar4j"]
      interval: 10s
      timeout: 5s
      retries: 5
    profiles: ["high"]

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    profiles: ["high"]

volumes:
  edgar4j-data:
  mongo-data:
  pg-data:
  redis-data:
```

### Usage

```bash
# Low-resource mode (app only, no external services)
docker compose --profile low up

# High-resource mode (app + MongoDB + PostgreSQL + Redis)
docker compose --profile high up

# Low-resource mode with custom data directory
docker compose --profile low up -e EDGAR4J_DATA_PATH=/custom/data/path
```

### Dockerfile (Multi-Stage)

```dockerfile
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app
COPY target/edgar4j-*.jar app.jar

# Default data directory for low-resource mode
RUN mkdir -p /app/data
VOLUME /app/data

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Application Startup Guide

### Quick Start (Low-Resource Mode)

```bash
# Clone and build
git clone https://github.com/jds/edgar4j.git
cd edgar4j
mvn clean package -DskipTests

# Run with zero external dependencies
java -jar target/edgar4j-*.jar --edgar4j.resource-mode=low

# Or with Docker
docker compose --profile low up
```

### Production Setup (High-Resource Mode)

```bash
# Start infrastructure
docker compose --profile high up -d mongodb postgres redis

# Run application
java -jar target/edgar4j-*.jar \
  --edgar4j.resource-mode=high \
  --spring.data.mongodb.uri=mongodb://localhost:27017/edgar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/edgar4j

# Or all-in-one with Docker
docker compose --profile high up
```

## Configuration Reference

### Shared Properties (Both Modes)

| Property | Default | Description |
|----------|---------|-------------|
| `edgar4j.resource-mode` | `high` | `low` or `high` |
| `edgar4j.sec.user-agent` | (required) | SEC API User-Agent header |
| `edgar4j.export.max-records` | `10000` | Max export records |
| `edgar4j.jobs.realtime-filing-sync.enabled` | `true` | Enable real-time sync |
| `edgar4j.jobs.realtime-filing-sync.cron` | `0 */15 * * * *` | Sync frequency |

### Low-Resource Mode Properties

| Property | Default | Description |
|----------|---------|-------------|
| `edgar4j.storage.file.base-path` | `./data` | Data directory |
| `edgar4j.storage.file.max-in-memory-records` | `100000` | Memory limit per collection |
| `edgar4j.storage.file.index-on-startup` | `true` | Build indexes at boot |
| `edgar4j.storage.file.flush-interval` | `PT30S` | Background flush interval |
| `edgar4j.search.engine` | `simple` | `simple` or `lucene` |
| `edgar4j.batch.chunk-size` | `5` | Batch processing chunk size |

### High-Resource Mode Properties

| Property | Default | Description |
|----------|---------|-------------|
| `spring.data.mongodb.uri` | (required) | MongoDB connection URI |
| `spring.datasource.url` | (required) | PostgreSQL JDBC URL |
| `spring.data.redis.host` | `localhost` | Redis host |
| `edgar4j.batch.chunk-size` | `50` | Batch processing chunk size |

## Feature Parity Matrix

| Feature | Low Mode | High Mode | Notes |
|---------|----------|-----------|-------|
| Form 4 CRUD | Yes | Yes | |
| Form 3/5/6K/8K/13DG/13F/20F | Yes | Yes | |
| Insider transaction analytics | Yes | Yes | Slower aggregations in low mode |
| Full-text search | Basic | Elasticsearch | Low uses string matching |
| Real-time filing sync | Yes (slower) | Yes | Reduced frequency in low mode |
| Market data providers | Yes | Yes | Caffeine cache in low mode |
| CSV/JSON export | Yes | Yes | |
| Pagination | Yes | Yes | In-memory in low mode |
| Batch processing | Yes | Yes | H2 metadata, smaller chunks |
| Dashboard statistics | Yes | Yes | |
| OpenAPI / Swagger | Yes | Yes | |
| Health checks | Yes | Yes | Shows resource mode |
| Concurrent users | ~5 | ~100+ | File locking limits in low mode |
| Dataset size | < 500K records | Millions | Memory-bound in low mode |

## Actuator Endpoints

The health endpoint reports mode and storage details:

```json
GET /actuator/health

{
  "status": "UP",
  "components": {
    "resourceMode": {
      "status": "UP",
      "details": {
        "resource-mode": "low",
        "storage": "File-based storage (JSON/CSV/Parquet)",
        "data-path": "./data",
        "collections-loaded": 22,
        "total-records-in-memory": 12450
      }
    }
  }
}
```

## Migration Workflow Documentation

```
┌─────────────────┐     Export      ┌──────────────┐     Import      ┌─────────────────┐
│  Production     │ ──────────────► │  File Export  │ ──────────────► │  Dev Laptop     │
│  (high mode)    │                 │  (portable)   │                 │  (low mode)     │
│  MongoDB + PG   │                 │  JSON/Parquet │                 │  File storage   │
└─────────────────┘                 └──────────────┘                 └─────────────────┘

# Export from production
java -jar edgar4j.jar --edgar4j.migration.action=export --edgar4j.migration.target-path=./export

# Copy to dev machine
scp -r ./export dev-machine:~/edgar4j-data/

# Run locally with exported data
java -jar edgar4j.jar --edgar4j.resource-mode=low --edgar4j.storage.file.base-path=~/edgar4j-data/export
```

## CI/CD Pipeline

### Build Matrix

```
┌──────────────┬─────────────┬──────────────┐
│   Stage      │  Low Mode   │  High Mode   │
├──────────────┼─────────────┼──────────────┤
│ Compile      │     ✓       │      ✓       │
│ Unit Tests   │     ✓       │      ✓       │
│ Integration  │  ✓ (no DB)  │ ✓ (services) │
│ Contract     │     ✓       │      ✓       │
│ E2E          │     ✓       │      ✓       │
│ Docker Build │     ✓       │      ✓       │
│ Smoke Test   │  ✓ (boot)   │ ✓ (compose)  │
└──────────────┴─────────────┴──────────────┘
```

## Estimated Effort

- **Docker Compose profiles**: 0.5 day
- **Dockerfile updates**: 0.5 day
- **Configuration reference docs**: 1 day
- **CI pipeline configuration**: 1 day
- **Smoke test scripts**: 0.5 day
- **Total**: 3-4 days

---

## Total Project Summary

| Phase | Title | Est. Days | Dependencies |
|-------|-------|-----------|-------------|
| 1 | Repository Abstraction Layer | 3-4 | None |
| 2 | File-Based Storage Engine | 4-5 | Phase 1 |
| 3 | File-Backed Repository Implementations | 6-7 | Phase 1, 2 |
| 4 | Profile & Auto-Configuration | 3-4 | Phase 1, 3 |
| 5 | Cache Abstraction | 1-1.5 | Phase 4 |
| 6 | Batch Processing Adaptation | 2 | Phase 4 |
| 7 | Search Abstraction | 3-4 | Phase 4 |
| 8 | Data Migration & Portability | 5-6 | Phase 3 |
| 9 | Testing Strategy | 6-7 | Phase 4 |
| 10 | Documentation & DevOps | 3-4 | Phase 9 |
| **Total** | | **37-46 days** | |

### Recommended Execution Order (Parallelization)

```
Week 1-2:  Phase 1 (Repository Abstraction)
Week 2-3:  Phase 2 (File Storage Engine)
Week 3-5:  Phase 3 (File-Backed Repos) + Phase 8 (Migration) in parallel
Week 5-6:  Phase 4 (Profile Config) + Phase 5 (Cache)
Week 6-7:  Phase 6 (Batch) + Phase 7 (Search) in parallel
Week 7-9:  Phase 9 (Testing)
Week 9-10: Phase 10 (Docs & DevOps)
```

**Critical path**: Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 9

With parallelization, the project can be completed in **8-10 weeks** by a single developer, or **5-6 weeks** with two developers.
