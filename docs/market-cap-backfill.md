# Market-Cap Backfill

Use this pass when insider-purchase rows are present but some companies still have `marketCap=null`, or after changing market-data enrichment behavior and wanting to refresh only the tracked universe without running a full market-data sync.

## When To Run It

- After a deploy that changes market-data enrichment logic.
- After a provider outage or rate-limit window left partial market-cap coverage.
- During staging smoke tests when `/api/insider-purchases` returns rows with sparse market-cap values.

## What It Does

`POST /api/market-data/backfill/market-cap`:

- builds the tracked ticker universe from S&P 500 constituents and recent insider-purchase tickers
- normalizes tickers and skips placeholders such as `NONE` and `N/A`
- filters to stored market-data rows still missing `marketCap` or `marketCapSource`
- refreshes only those candidates through the configured market-data providers
- clears cached provider quote/profile lookups before a pass so retries use fresh upstream data
- falls back in this order when market cap is still missing:
  - provider-reported market cap
  - provider shares outstanding x resolved current price
  - SEC `companyfacts` shares outstanding x resolved current price
  - recent SEC XBRL filing shares outstanding x resolved current price
- persists `marketCapSource` on `company_market_data` and returns a bounded summary

This pass is manual and bounded. It does not depend on the scheduled market-data sync being enabled.

## Endpoint

`POST /api/market-data/backfill/market-cap`

Query parameters:

- `maxTickers`
  - optional
  - default `250`
  - maximum number of candidate tickers to process in one pass
- `lookbackDays`
  - optional
  - default `30`
  - window used to include insider-active tickers alongside S&P 500 symbols

Example:

```bash
curl -X POST \
  "http://localhost:8080/api/market-data/backfill/market-cap?maxTickers=250&lookbackDays=30"
```

If security is enabled:

```bash
curl -u "$EDGAR4J_SECURITY_USERNAME:$EDGAR4J_SECURITY_PASSWORD" \
  -X POST \
  "http://localhost:8080/api/market-data/backfill/market-cap?maxTickers=250&lookbackDays=30"
```

## Response Fields

- `tracked_tickers`: total tracked universe considered for the pass
- `candidate_tickers`: tickers that still needed market-cap enrichment
- `processed_tickers`: candidates actually attempted in this pass
- `updated_tickers`: candidates that finished with a valid market cap
- `unresolved_tickers_count`: candidates still missing market cap after the pass
- `up_to_date_tickers`: tracked tickers that were already fine and skipped
- `deferred_tickers`: remaining candidates not processed because of `maxTickers`
- `sample_unresolved_tickers`: up to 20 unresolved tickers to inspect manually

Example response:

```json
{
  "success": true,
  "message": "Market-cap backfill completed",
  "data": {
    "tracked_tickers": 541,
    "candidate_tickers": 36,
    "processed_tickers": 36,
    "updated_tickers": 31,
    "unresolved_tickers_count": 5,
    "up_to_date_tickers": 505,
    "deferred_tickers": 0,
    "batch_size": 25,
    "max_tickers": 250,
    "duration_ms": 18211,
    "sample_unresolved_tickers": ["IP", "XYZ"]
  }
}
```

## How To Run It Safely

1. Confirm Settings or fallback env/config has at least one working market-data provider.
2. Confirm the backend can reach that provider from the target environment.
3. Start with `maxTickers=100` or `250` in staging.
4. Repeat the pass until `deferred_tickers=0` and `unresolved_tickers_count` is zero or a known stable small set.

## When Unresolved Tickers Remain

Check these first:

- the ticker is still valid and normalized correctly
- the configured provider returns company profile or market-cap data for that symbol
- provider credentials are present either in AppSettings or fallback env/config
- the company is foreign, delisted, or otherwise lacks a usable market-cap feed from the active provider

If unresolved rows are still needed for the UI, the insider page already keeps confirmed S&P 500 rows visible even while market cap is still missing.

## Failure Modes

- `400 Bad Request` with `maxTickers must be greater than 0`: invalid request parameter.
- `400 Bad Request` with `lookbackDays must be greater than 0`: invalid request parameter.
- `400 Bad Request` with `Market data sync job is already running`: another scheduled or manual sync owns the market-data job lock.

Wait for the current run to finish, then retry.
