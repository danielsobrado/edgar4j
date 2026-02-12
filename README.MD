# Introduction to Edgar4J

The goal of this library is to provide a framework for collecting and organizing fillings from the SEC website.

The scope will initially be confined to stock ownership and insider transactions.

Based on: https://www.sec.gov/edgar/sec-api-documentation

## Configuration

Most settings are configured via environment variables (with sane defaults for local development). Key options:

### SEC access

- `SEC_USER_AGENT`: User-Agent sent to `sec.gov` / `data.sec.gov` (recommended to set to your app + contact info).
- `edgar4j.sec.rate-limit-per-second` (env: `EDGAR4J_SEC_RATE_LIMIT_PER_SECOND` if you map it yourself): Max requests/sec enforced by the internal rate limiter (default `10`).

### Storage (daily index downloads)

- `EDGAR4J_DAILY_INDEXES_PATH`: Directory for downloaded daily master index files (default `./data/daily-indexes`).

### MongoDB

- `MONGODB_URI`: Mongo connection string (default `mongodb://localhost:27017/edgar`).
- `MONGODB_DATABASE`: Mongo database name (default `edgar`).
- `MONGODB_AUTO_INDEX_CREATION`: Enables Spring Data Mongo index creation on startup (default `true`).

### Spring Cloud Config (optional)

- `SPRING_CLOUD_CONFIG_ENABLED`: Enables Spring Cloud Config client (default `false`).

### Security (optional, recommended for production)

By default, the app permits all requests (good for local dev). To protect `/api/**` and `/actuator/**` with HTTP Basic:

- `EDGAR4J_SECURITY_ENABLED=true`
- `EDGAR4J_SECURITY_USERNAME=...`
- `EDGAR4J_SECURITY_PASSWORD=...`

When enabled, `/actuator/health` and `/actuator/info` remain public, everything under `/api/**` and other actuator endpoints requires auth.

### Actuator

- `MANAGEMENT_HEALTH_SHOW_DETAILS`: Controls health endpoint details (default `never`).

### Elasticsearch (optional)

Elasticsearch repositories are disabled by default. To enable them, run with the `elasticsearch` Spring profile and provide endpoints/credentials:

- Profile: `elasticsearch`
- `ELASTICSEARCH_ENDPOINTS` (default `localhost:9200`)
- `ELASTICSEARCH_USERNAME` (default `elastic`)
- `ELASTICSEARCH_PASSWORD` (default `changeme`)

### Jobs

- `TICKER_SYNC_ENABLED` (default `true`)
- `FILING_SYNC_ENABLED` (default `true`)
- `DATA_INTEGRITY_ENABLED` (default `true`)

## Build

This repo uses the Maven Wrapper. On Windows, `mvnw.cmd` will fall back to the `java` found on `PATH` if `JAVA_HOME` is not set correctly.

### Compile and Start with Docker

Prerequisites:

- Docker Desktop (or Docker Engine) with Docker Compose v2

From the repository root:

1. Build the backend image (this compiles the Java project inside Docker):

```bash
docker build -t edgar4j-app-clean .
```

2. Start the full stack:

```bash
docker compose up -d
```

3. Verify containers are up:

```bash
docker compose ps
```

4. Open the application:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`

Useful Docker commands:

```bash
# Follow backend logs
docker compose logs -f edgar4j-app

# Follow frontend logs
docker compose logs -f frontend

# Rebuild backend after backend code changes
docker build -t edgar4j-app-clean . && docker compose up -d --force-recreate edgar4j-app

# Stop everything
docker compose down

# Stop and remove volumes (destructive)
docker compose down -v
```

## Submissions API

Each entity's current filing history is available at the following URL:

https://data.sec.gov/submissions/CIK##########.json
Where the ########## is the entity’s 10-digit Central Index Key (CIK), including leading zeros.

Example for Microsoft: https://data.sec.gov/submissions/CIK0000789019.json

### Get a Document

From the Submissions file above: https://data.sec.gov/submissions/CIK{CIK}.json

We can get each document by using the following End Point:

https://www.sec.gov/Archives/edgar/data/{CIK}/{AccessionNumber}/{PrimaryDocument}

Example from Microsoft: https://www.sec.gov/Archives/edgar/data/789019/000162643116000118/xslF345X03/edgar.xml

**Note**: Remove '-' from the Accession Number.

## Bulk

## Forms

### Ownership: 13D/13G and 13F

https://www.sec.gov/dera/data/form-13f

Schedule 13D and 13G are used to report a party's ownership of stock which exceeds 5% of a company's total stock issue.
Schedule 13D is for active/intent-to-influence ownership, while Schedule 13G is for passive ownership.

Form 13F is a quarterly report that is required to be filed by all institutional investment managers with at least $100 million in assets under management.

### Insider Transactions: 3, 4 and 5

**SEC Form 4**: Statement of Changes in Beneficial Ownership? SEC Form 4: Statement of Changes in Beneficial Ownership is a document that must be filed with the Securities and Exchange Commission (SEC) whenever there is a material change in the holdings of company insiders.

**SEC Form 3**: Is a document that a company insider or major shareholder must file with the SEC. The information provided on the form is meant to disclose the holdings of directors, officers, and beneficial owners of registered companies and becomes public record.

**SEC Form 5**: Annual Statement of Changes in Beneficial Ownership of Securities is a document that company insiders must file with the Securities and Exchange Commission (SEC) if they have conducted transactions in the company's securities during the year.

### Current Reports: 6-K and 8-K

**SEC Form 6-K**: Report of foreign private issuers on major events or updates between annual reports.

**SEC Form 8-K**: Current report used to announce major events that shareholders should know about.

### Annual Reports: 20-F

**SEC Form 20-F**: Annual report for foreign private issuers with comprehensive company and financial information.

## Terms

**Accession Number**: Unique for each filling
