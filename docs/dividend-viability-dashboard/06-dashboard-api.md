# Phase 6: Dashboard REST API & UX

## Objective

Expose all dividend viability metrics, alerts, and filing evidence through a clean REST API. Design the response structure to directly support the dashboard wireframe.

## API Endpoints

### Base Path: `/api/dividend`

| Method | Path | Description |
|---|---|---|
| GET | `/{tickerOrCik}` | Full dividend viability overview |
| GET | `/{tickerOrCik}/history` | Historical metric time series |
| GET | `/{tickerOrCik}/alerts` | Active and historical alerts |
| GET | `/{tickerOrCik}/events` | Dividend event timeline |
| GET | `/{tickerOrCik}/evidence/{accession}` | Filing text evidence viewer |
| GET | `/compare` | Peer comparison across companies |
| POST | `/screen` | Screen companies by dividend criteria |
| GET | `/metrics` | Self-documenting metric definitions |

### 6.1 Company Overview Response

```json
{
  "company": {
    "cik": "0000320193", "ticker": "AAPL", "name": "Apple Inc",
    "sector": "Technology", "fiscalYearEnd": "0930",
    "lastFilingDate": "2025-11-01", "dataFreshness": "2025-11-02T14:30:00Z"
  },
  "viability": { "rating": "SAFE", "activeAlerts": 0, "score": 92 },
  "snapshot": {
    "dpsLatest": 0.96, "dpsCagr5y": 5.8, "fcfPayoutRatio": 0.15,
    "uninterruptedYears": 12, "consecutiveRaises": 12,
    "netDebtToEbitda": 0.4, "interestCoverage": 42.5,
    "currentRatio": 1.07, "fcfMargin": 0.27
  },
  "confidence": {
    "dpsLatest": "HIGH", "fcfPayoutRatio": "MEDIUM", "netDebtToEbitda": "LOW_MEDIUM"
  }
}
```

### 6.2 History Response

```
GET /api/dividend/AAPL/history?metrics=dps_declared,fcf_payout&periods=FY&years=15
```

Returns time series with growth analysis (CAGR, trend, volatility) per metric.

### 6.3 Screener

```json
POST /api/dividend/screen
{
  "filters": {
    "uninterrupted_years": { "min": 10 },
    "fcf_payout": { "max": 0.60 },
    "dps_cagr_5y": { "min": 5.0 },
    "viability_rating": ["SAFE", "STABLE"],
    "sector": ["Technology", "Healthcare"]
  },
  "sort": "dps_cagr_5y", "direction": "DESC",
  "page": 0, "size": 20
}
```

## Controller Implementation

```java
@RestController
@RequestMapping("/api/dividend")
@RequiredArgsConstructor
@Tag(name = "Dividend Viability", description = "Long-term dividend safety analysis")
public class DividendController {

    private final DividendDashboardService dashboardService;
    private final MetricRegistry metricRegistry;

    @GetMapping("/{tickerOrCik}")
    public Mono<DividendOverviewResponse> getOverview(@PathVariable String tickerOrCik) {
        return dashboardService.getOverview(resolveIdentifier(tickerOrCik));
    }

    @GetMapping("/{tickerOrCik}/history")
    public Mono<MetricHistoryResponse> getHistory(
            @PathVariable String tickerOrCik,
            @RequestParam(defaultValue = "dps_declared,fcf_payout,earnings_payout") List<String> metrics,
            @RequestParam(defaultValue = "FY") String periods,
            @RequestParam(defaultValue = "15") int years) {
        return dashboardService.getHistory(resolveIdentifier(tickerOrCik), metrics, periods, years);
    }

    @GetMapping("/{tickerOrCik}/alerts")
    public Mono<AlertsResponse> getAlerts(@PathVariable String tickerOrCik,
            @RequestParam(defaultValue = "true") boolean active) {
        return dashboardService.getAlerts(resolveIdentifier(tickerOrCik), active);
    }

    @GetMapping("/{tickerOrCik}/events")
    public Mono<EventsResponse> getEvents(@PathVariable String tickerOrCik,
            @RequestParam(required = false) LocalDate since) {
        return dashboardService.getEvents(resolveIdentifier(tickerOrCik), since);
    }

    @GetMapping("/compare")
    public Mono<ComparisonResponse> compare(@RequestParam List<String> tickers,
            @RequestParam(defaultValue = "fcf_payout,dps_cagr_5y,net_debt_to_ebitda") List<String> metrics) {
        return dashboardService.compare(tickers, metrics);
    }

    @PostMapping("/screen")
    public Mono<Page<ScreenResult>> screen(@RequestBody ScreenRequest request) {
        return dashboardService.screen(request);
    }

    @GetMapping("/metrics")
    public List<MetricDefinitionResponse> getMetricDefinitions() {
        return metricRegistry.getAllDefinitions().stream().map(this::toResponse).toList();
    }
}
```

## Dashboard UX Wireframe

```
┌────────────────────────────────────────────────────────────────┐
│ [Company: AAPL ▼]  Apple Inc  │ FY End: Sep  │ Fresh: 2h ago  │
├────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐  │
│ │ [SAFE]  Score: 92/100  │  0 Active Alerts                │  │
│ └──────────────────────────────────────────────────────────┘  │
│                                                                │
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐      │
│ │ DPS    │ │ CAGR   │ │ FCF    │ │ Years  │ │ ND/    │      │
│ │ $0.96  │ │ 5Y     │ │ Payout │ │ Unint. │ │ EBITDA │      │
│ │        │ │ 5.8%   │ │ 15%    │ │ 12     │ │ 0.4x   │      │
│ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘      │
│                                                                │
│ ┌──────────────────────────────────────────────────────────┐  │
│ │ DIVIDEND TIMELINE (combo chart — DPS by year + markers)  │  │
│ │  $1.00 ┤                                    ●──●         │  │
│ │  $0.50 ┤          ●──●──●──●──●──●──●──●──●              │  │
│ │  $0.00 ┼──────────────────────────────────────           │  │
│ │        2012  2014  2016  2018  2020  2022  2024          │  │
│ └──────────────────────────────────────────────────────────┘  │
│                                                                │
│ ┌───────────────────────┐  ┌────────────────────────────┐    │
│ │ COVERAGE PANEL        │  │ BALANCE SHEET PANEL         │    │
│ │ OCF vs Capex vs Divs  │  │ Net Debt trend              │    │
│ │ FCF vs Dividends      │  │ Interest Coverage trend     │    │
│ │ Retained Cash         │  │ Current / Quick / Cash      │    │
│ └───────────────────────┘  └────────────────────────────┘    │
│                                                                │
│ ┌───────────────────────┐  ┌────────────────────────────┐    │
│ │ PROFITABILITY/GROWTH  │  │ ALERTS                      │    │
│ │ CAGR table            │  │ No active alerts            │    │
│ │ Margins trend         │  │ History: 1 resolved         │    │
│ │ ROE / ROA / ROIC      │  │                             │    │
│ └───────────────────────┘  └────────────────────────────┘    │
│                                                                │
│ ┌──────────────────────────────────────────────────────────┐  │
│ │ FILING EVIDENCE — Latest 8-K: "The Board declared..."   │  │
│ │ [View Filing →]                                          │  │
│ └──────────────────────────────────────────────────────────┘  │
│                                                                │
│ ┌──────────────────────────────────────────────────────────┐  │
│ │ 15-YEAR TABLE                                            │  │
│ │ Year │ DPS │ EPS │ Payout │ FCF/sh │ FCFPay │ D/E │ CR  │  │
│ │ 2024 │ .96 │ 6.42│  14.9% │  7.02  │ 13.7%  │ 1.8│ 1.07│  │
│ │ ...  │ ... │ ... │  ...   │  ...   │  ...   │ ...│ ... │  │
│ └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

## Validation Checklist

- [ ] `/api/dividend/AAPL` returns complete overview
- [ ] `/api/dividend/AAPL/history` returns 15 years of annual metrics
- [ ] `/api/dividend/compare?tickers=AAPL,MSFT,JNJ` works
- [ ] `/api/dividend/screen` filters correctly
- [ ] Swagger UI shows all endpoints
- [ ] Response times < 200ms for single company (cached)

## Estimated Effort: 5-6 days
