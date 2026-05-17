# Edgar4j Staging Smoke Checklist

Run this checklist after each release candidate and before promoting to production.

## 1. Configuration Gate

- Confirm `SEC_USER_AGENT` is set to a real SEC-declared contact string such as `Your Company Name sec-ops@your-company.com`.
- Confirm `SEC_USER_AGENT` does not use placeholder or non-monitored contacts such as `example.com`, `noreply`, or GitHub noreply addresses.
- Confirm `MONGO_URL` or `SPRING_MONGODB_URI` points to the staging MongoDB instance.
- Confirm `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` point to the staging PostgreSQL instance.
- Confirm `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT` point to the staging Redis instance.
- Confirm `VITE_API_URL` points to the public backend base URL used by the frontend build.
- If security is enabled, confirm `EDGAR4J_SECURITY_ENABLED=true` and both `EDGAR4J_SECURITY_USERNAME` and `EDGAR4J_SECURITY_PASSWORD` are set explicitly.
- If using market data, confirm at least one provider is configured in AppSettings or fallback env/config:
  - `TIINGO_API_TOKEN`
  - `FINNHUB_API_KEY`
  - `ALPHA_VANTAGE_API_KEY`

## 2. Container and Probe Gate

- Backend image/container starts without restart loops.
- Frontend image/container starts without restart loops.
- `GET /actuator/health/liveness` returns `200`.
- `GET /actuator/health/readiness` returns `200`.
- If security is enabled:
  - unauthenticated `GET /api/settings` returns `401` or `403`
  - unauthenticated `GET /actuator/health/readiness` still returns `200`

Example:

```bash
curl -i http://<backend-host>/actuator/health/liveness
curl -i http://<backend-host>/actuator/health/readiness
curl -i http://<backend-host>/api/settings
curl -u "$EDGAR4J_SECURITY_USERNAME:$EDGAR4J_SECURITY_PASSWORD" http://<backend-host>/api/settings
```

PowerShell automation:

```powershell
.\scripts\smoke-test.ps1 `
  -BackendBaseUrl http://localhost:8080 `
  -FrontendBaseUrl http://localhost:3000
```

For authenticated staging:

```powershell
.\scripts\smoke-test.ps1 `
  -BackendBaseUrl https://api.example.com `
  -FrontendBaseUrl https://app.example.com `
  -Username $env:EDGAR4J_SECURITY_USERNAME `
  -Password $env:EDGAR4J_SECURITY_PASSWORD
```

## 3. Backend API Smoke

- `GET /api/settings` succeeds with auth when security is enabled.
- Response includes insider dashboard and realtime sync fields.
- Response does not expose plaintext SMTP or market-data secrets.
- `PUT /api/settings` persists a non-secret change and can be read back.

Example payload:

```json
{
  "userAgent": "Your Company Name sec-ops@your-company.com",
  "autoRefresh": true,
  "refreshInterval": 60,
  "darkMode": false,
  "emailNotifications": false,
  "smtpPort": 587,
  "smtpStartTlsEnabled": true,
  "marketDataProvider": "YAHOOFINANCE",
  "insiderPurchaseLookbackDays": 30,
  "insiderPurchaseMinMarketCap": 1000000000,
  "insiderPurchaseSp500Only": true,
  "insiderPurchaseMinTransactionValue": 100000,
  "realtimeSyncEnabled": true,
  "realtimeSyncForms": "4,3,5",
  "realtimeSyncLookbackHours": 1,
  "realtimeSyncMaxPages": 10,
  "realtimeSyncPageSize": 100
}
```

- `GET /api/insider-purchases/top?limit=5` returns JSON.
- `GET /api/insider-purchases/summary?lookbackDays=30` returns JSON.
- `GET /api/insider-purchases?sp500Only=true&minMarketCap=1000000000&size=5` returns JSON.
- `GET /api/market-data/prices/AAPL` returns data from the configured provider path.
- `POST /api/market-data/backfill/market-cap?maxTickers=250&lookbackDays=30` returns a bounded summary.
- `POST /api/settings/sync/tickers` returns success and logs a manual sync trigger.

## 4. Data Pipeline Smoke

Run these after startup and again after the jobs have had time to execute.

- `sp500_constituents` contains roughly 500 rows/documents.
- `company_market_data` is non-empty after market-data sync.
- If insider rows exist but `marketCap` is still sparse, run the dedicated backfill pass from [market-cap-backfill.md](market-cap-backfill.md) until `deferred_tickers=0`.
- Recent Form 4 data exists in MongoDB.
- Realtime filing sync logs show polling and completion without tight retry loops.
- Changing `realtimeSyncForms` in Settings changes the next poll behavior without restart.
- Disabling realtime sync in Settings stops further polling on the next cycle.

Suggested Mongo checks:

```javascript
db.sp500_constituents.countDocuments()
db.company_market_data.countDocuments()
db.form4.countDocuments({ filedDate: { $gte: ISODate("2026-01-01T00:00:00Z") } })
```

## 5. Frontend Smoke

- `/` loads and shows the dashboard widget `Top Insider Purchases`.
- `/insider-purchases` loads without console errors.
- Changing lookback, market cap, transaction value, sort, and S&P 500 filters changes the table results.
- Pagination works on `/insider-purchases`.
- `View All` from the dashboard navigates to `/insider-purchases`.
- Clicking a company drill-down from the dashboard widget or insider page opens Form 4 search results.
- `/settings` loads, saves, and reflects:
  - market-data provider settings
  - realtime filing sync settings
  - insider purchase default filters

## 6. Operational Log Review

- No repeated authentication failures.
- No repeated SEC throttling failures.
- No `Undeclared Automated Tool` blocks from SEC in backend logs.
- No repeated Redis, MongoDB, or PostgreSQL connection churn.
- No provider-disable loops for market-data sources.
- No large stack traces during normal dashboard/page loads.

## 7. Promotion Gate

Promote only if all are true:

- Backend tests passed on JDK 25.
- Frontend tests passed.
- Frontend production build passed.
- Probe endpoints are healthy.
- Settings round-trip works.
- Insider purchases API and UI both render live data.
- Scheduled jobs run cleanly in staging for at least one cycle.
