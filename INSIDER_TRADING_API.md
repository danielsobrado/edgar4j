# Insider Trading API Documentation

## Overview

The Insider Trading API provides endpoints to query and analyze SEC Form 4 insider trading data. It offers two main types of reports:

1. **Cluster Buys** - Groups of multiple insiders buying the same stock on the same day
2. **Individual Insider Buys** - Individual insider purchase transactions

## Base URL

```
http://localhost:8080/api/insider-trading
```

## Endpoints

### 1. Latest Cluster Buys

Get the most recent cluster buys across all stocks.

**Endpoint:** `GET /cluster-buys/latest`

**Parameters:**
- `days` (optional, default: 30) - Number of days to look back
- `minInsiders` (optional, default: 2) - Minimum number of insiders required to form a cluster
- `page` (optional, default: 0) - Page number
- `size` (optional, default: 50) - Page size

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/latest?days=30&minInsiders=3&page=0&size=10"
```

**Response:**
```json
{
  "content": [
    {
      "filingDate": "2025-11-04T17:26:41",
      "tradeDate": "2025-11-04",
      "ticker": "OBK",
      "companyName": "Origin Bancorp, Inc.",
      "industry": "State Commercial Banks",
      "insiderCount": 9,
      "tradeType": "P - Purchase",
      "averagePrice": 34.50,
      "totalQuantity": 29168,
      "totalSharesOwned": 540125,
      "averageOwnershipChange": 6.0,
      "totalValue": 1006278.00,
      "insiderRoles": "D,O",
      "hasDirectorBuys": true,
      "hasOfficerBuys": true
    }
  ],
  "page number": 0,
  "pageSize": 10,
  "totalElements": 25,
  "totalPages": 3
}
```

---

### 2. Latest Insider Buys

Get the most recent individual insider buys across all stocks.

**Endpoint:** `GET /insider-buys/latest`

**Parameters:**
- `days` (optional, default: 30) - Number of days to look back
- `page` (optional, default: 0) - Page number
- `size` (optional, default: 50) - Page size

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/insider-buys/latest?days=7&page=0&size=20"
```

**Response:**
```json
{
  "content": [
    {
      "accessionNumber": "0001626431-16-000118",
      "filingDate": "2025-11-04T20:30:13",
      "tradeDate": "2025-10-31",
      "ticker": "ETN",
      "companyName": "Eaton Corp Plc",
      "insiderName": "Gerald Johnson",
      "insiderTitle": "Director",
      "tradeType": "P - Purchase",
      "pricePerShare": 384.34,
      "quantity": 100,
      "sharesOwnedAfter": 200,
      "ownershipChangePercent": 100.00,
      "transactionValue": 38434.00,
      "ownershipType": "D",
      "securityTitle": "Common Stock"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 150,
  "totalPages": 8
}
```

---

### 3. Cluster Buys by Ticker

Get cluster buys for a specific stock ticker.

**Endpoint:** `GET /cluster-buys/ticker/{ticker}`

**Path Parameters:**
- `ticker` (required) - Stock ticker symbol (e.g., MSFT, AAPL)

**Query Parameters:**
- `days` (optional, default: 90) - Number of days to look back
- `minInsiders` (optional, default: 2) - Minimum insiders per cluster

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/ticker/MSFT?days=180&minInsiders=2"
```

---

### 4. Insider Buys by Ticker

Get individual insider buys for a specific stock ticker.

**Endpoint:** `GET /insider-buys/ticker/{ticker}`

**Path Parameters:**
- `ticker` (required) - Stock ticker symbol

**Query Parameters:**
- `days` (optional, default: 90) - Number of days to look back

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/insider-buys/ticker/AAPL?days=365"
```

---

### 5. Insider Buys by Insider

Get all buys by a specific insider (identified by CIK).

**Endpoint:** `GET /insider-buys/insider/{insiderCik}`

**Path Parameters:**
- `insiderCik` (required) - Insider's CIK number

**Query Parameters:**
- `days` (optional, default: 180) - Number of days to look back

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/insider-buys/insider/0001529370?days=365"
```

---

### 6. Cluster Buys by Date Range

Get cluster buys within a specific date range.

**Endpoint:** `GET /cluster-buys/date-range`

**Query Parameters:**
- `startDate` (required) - Start date (format: yyyy-MM-dd)
- `endDate` (required) - End date (format: yyyy-MM-dd)
- `minInsiders` (optional, default: 2) - Minimum insiders per cluster
- `page` (optional, default: 0) - Page number
- `size` (optional, default: 50) - Page size

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/date-range?startDate=2025-01-01&endDate=2025-11-05&minInsiders=3"
```

---

### 7. Top Cluster Buys by Value

Get the highest-value cluster buys.

**Endpoint:** `GET /cluster-buys/top-by-value`

**Query Parameters:**
- `days` (optional, default: 30) - Number of days to look back
- `limit` (optional, default: 10) - Number of results to return

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/top-by-value?days=30&limit=25"
```

---

### 8. Top Insider Buys by Value

Get the highest-value individual insider buys.

**Endpoint:** `GET /insider-buys/top-by-value`

**Query Parameters:**
- `days` (optional, default: 30) - Number of days to look back
- `limit` (optional, default: 10) - Number of results to return

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/insider-buys/top-by-value?days=7&limit=50"
```

---

### 9. High-Significance Cluster Buys

Get cluster buys with high significance scores.

Significance is calculated based on:
- Number of insiders (more = higher score)
- Total transaction value (higher = higher score)
- Insider types (Directors, Officers, 10% owners increase score)

**Endpoint:** `GET /cluster-buys/high-significance`

**Query Parameters:**
- `days` (optional, default: 30) - Number of days to look back
- `minScore` (optional, default: 70) - Minimum significance score (0-100)
- `limit` (optional, default: 20) - Number of results to return

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/high-significance?days=30&minScore=80&limit=15"
```

---

### 10. Health Check

Check if the API is running.

**Endpoint:** `GET /health`

**Example:**
```bash
curl "http://localhost:8080/api/insider-trading/health"
```

**Response:**
```
Insider Trading API is running
```

---

## Data Models

### ClusterBuy

| Field | Type | Description |
|-------|------|-------------|
| filingDate | DateTime | Most recent filing date in the cluster |
| tradeDate | Date | Transaction date (all buys in cluster) |
| ticker | String | Stock ticker symbol |
| companyName | String | Company name |
| industry | String | Industry classification |
| insiderCount | Integer | Number of distinct insiders |
| tradeType | String | Transaction type (e.g., "P - Purchase") |
| averagePrice | Decimal | Average price per share |
| totalQuantity | Decimal | Total shares purchased |
| totalSharesOwned | Decimal | Total shares owned after transactions |
| averageOwnershipChange | Decimal | Average ownership change percentage |
| totalValue | Decimal | Total transaction value |
| insiderRoles | String | Insider roles (e.g., "D,O" for Director & Officer) |
| hasDirectorBuys | Boolean | Whether cluster includes director buys |
| hasOfficerBuys | Boolean | Whether cluster includes officer buys |
| hasTenPercentOwnerBuys | Boolean | Whether cluster includes 10% owner buys |
| insiderBuys | Array | List of individual InsiderBuy objects in this cluster |

### InsiderBuy

| Field | Type | Description |
|-------|------|-------------|
| accessionNumber | String | SEC Form 4 accession number |
| filingDate | DateTime | Date Form 4 was filed |
| tradeDate | Date | Transaction date |
| ticker | String | Stock ticker symbol |
| companyName | String | Company name |
| insiderName | String | Insider's full name |
| insiderCik | String | Insider's CIK |
| insiderTitle | String | Insider's role (e.g., "CEO", "Director") |
| tradeType | String | Transaction code |
| pricePerShare | Decimal | Price per share |
| quantity | Decimal | Number of shares purchased |
| sharesOwnedAfter | Decimal | Shares owned after transaction |
| sharesOwnedBefore | Decimal | Shares owned before transaction |
| ownershipChangePercent | Decimal | Ownership change percentage |
| transactionValue | Decimal | Total transaction value |
| ownershipType | String | "D" (Direct) or "I" (Indirect) |
| securityTitle | String | Security type (usually "Common Stock") |

---

## Usage Examples

### Get today's high-value cluster buys
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/top-by-value?days=1&limit=10"
```

### Get this week's insider buys for Tesla
```bash
curl "http://localhost:8080/api/insider-trading/insider-buys/ticker/TSLA?days=7"
```

### Find clusters with 5+ insiders in the last 30 days
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/latest?days=30&minInsiders=5&size=100"
```

### Get only the most significant cluster buys
```bash
curl "http://localhost:8080/api/insider-trading/cluster-buys/high-significance?days=30&minScore=85&limit=20"
```

---

## Notes

- All monetary values are in USD
- Dates use ISO 8601 format (yyyy-MM-dd for dates, yyyy-MM-ddTHH:mm:ss for datetimes)
- The API uses pagination for large result sets
- Results are ordered by filing date (most recent first) unless otherwise specified
- All ticker symbols are case-insensitive and will be converted to uppercase

---

## Future Enhancements

Planned features:
- Stock performance metrics (1d, 1w, 1m, 6m price changes)
- Industry classification integration
- Email/webhook alerts for significant cluster buys
- Export to CSV/Excel
- Filtering by insider role (Directors only, Officers only, etc.)
- Clustering by insider relationship networks
