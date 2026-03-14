# Phase 8: External Data — Yields & Market Data

## Objective

Integrate external data sources that EDGAR cannot provide: equity prices (for dividend yield), U.S. Treasury yield curve (for risk-free comparison), and corporate bond spreads (for credit context). These are **overlay metrics** — the EDGAR fundamentals remain the source of truth.

## What EDGAR Cannot Provide

| Data | Why EDGAR Lacks It | External Source |
|---|---|---|
| Current/historical stock prices | EDGAR is a filings system, not a market data feed | Yahoo/Alpha Vantage/Finnhub (existing) |
| Market capitalization | Derived from price × shares | Computed from external price + EDGAR shares |
| Dividend yield | Requires current price | Computed: DPS / price |
| Total return | Requires price history | External price + EDGAR dividends |
| Risk-free yield curve | Macro data, not company filings | U.S. Treasury published data |
| Corporate bond spreads | Not in XBRL filings | FRED (Federal Reserve) |

## 8.1 Market Data Integration (Extend Existing)

The existing `MarketDataProvider` interface already provides `getCurrentPrice()`, `getHistoricalPrices()`, and `getFinancialMetrics()` (which includes `dividendYield`). Extend with dividend-specific computations:

```java
@Service
@RequiredArgsConstructor
public class DividendMarketDataService {

    private final MarketDataService marketDataService;  // existing
    private final XbrlFactRepository factRepository;
    private final MetricValueRepository metricValueRepository;

    /**
     * Compute market-dependent dividend metrics.
     * Called after facts sync when price data is available.
     */
    public MarketDividendMetrics computeMarketMetrics(String ticker, String cik) {
        // Get current price from existing providers
        StockPrice price = marketDataService.getCurrentPrice(ticker).join();
        if (price == null || price.getPrice() == null) return null;

        // Get latest DPS from EDGAR facts
        BigDecimal annualDps = getTrailingAnnualDps(cik);
        BigDecimal marketCap = price.getMarketCap() != null
                ? BigDecimal.valueOf(price.getMarketCap())
                : computeMarketCap(price.getPrice(), cik);

        MarketDividendMetrics metrics = new MarketDividendMetrics();

        // Forward dividend yield = annualized DPS / current price
        if (annualDps != null && price.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            metrics.setDividendYield(annualDps.divide(price.getPrice(), 4, RoundingMode.HALF_UP));
        }

        // Shareholder yield = (dividends + net buybacks) / market cap
        BigDecimal totalDividends = factRepository.getLatestValue(cik, "DividendsPaid").orElse(null);
        BigDecimal netBuyback = metricValueRepository.getLatestValue(cik, "net_buyback").orElse(null);
        if (totalDividends != null && marketCap != null && marketCap.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalReturn = totalDividends.add(
                    netBuyback != null ? netBuyback : BigDecimal.ZERO);
            metrics.setShareholderYield(totalReturn.divide(marketCap, 4, RoundingMode.HALF_UP));
        }

        // Buyback yield = net buybacks / market cap
        if (netBuyback != null && marketCap != null && marketCap.compareTo(BigDecimal.ZERO) > 0) {
            metrics.setBuybackYield(netBuyback.divide(marketCap, 4, RoundingMode.HALF_UP));
        }

        return metrics;
    }

    @Data
    public static class MarketDividendMetrics {
        private BigDecimal dividendYield;
        private BigDecimal shareholderYield;
        private BigDecimal buybackYield;
        private BigDecimal trailingAnnualDps;
        private BigDecimal currentPrice;
        private BigDecimal marketCap;
        private LocalDate priceDate;
    }
}
```

## 8.2 U.S. Treasury Yield Curve

The Treasury publishes daily par yield curve rates. Use these for "dividend yield vs risk-free" comparisons.

### Data Source

The Treasury publishes yield curve data as XML/CSV at documented URLs, updated daily after ~3:30 PM ET.

```java
@Service
@RequiredArgsConstructor
public class TreasuryYieldService {

    private final DownloadedResourceStore cache;  // existing

    /**
     * Fetch Treasury par yield curve rates.
     * Maturities: 1M, 2M, 3M, 6M, 1Y, 2Y, 3Y, 5Y, 7Y, 10Y, 20Y, 30Y
     */
    public TreasuryYieldCurve getYieldCurve(LocalDate date) {
        String url = buildTreasuryUrl(date);
        String xml = fetchWithCache("treasury-yields", url);
        return parseTreasuryXml(xml);
    }

    /**
     * Get the 10-year Treasury yield (most common benchmark).
     */
    public BigDecimal get10YearYield(LocalDate date) {
        TreasuryYieldCurve curve = getYieldCurve(date);
        return curve.getRate("10Y");
    }

    /**
     * Compute dividend yield spread over risk-free.
     * Positive = dividend yield exceeds risk-free (typical for equities)
     * Negative = dividend yield below risk-free (unusual, may signal overvaluation)
     */
    public BigDecimal computeYieldSpread(BigDecimal dividendYield, LocalDate date) {
        BigDecimal riskFree = get10YearYield(date);
        if (riskFree == null) return null;
        return dividendYield.subtract(riskFree.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    @Data
    public static class TreasuryYieldCurve {
        private LocalDate date;
        private Map<String, BigDecimal> rates;  // "1M" → 5.23, "10Y" → 4.28, etc.

        public BigDecimal getRate(String maturity) {
            return rates.get(maturity);
        }
    }
}
```

### Caching Strategy

| Data | Cache TTL | Rationale |
|---|---|---|
| Current day yield curve | 4 hours | Updated once daily after market close |
| Historical yields | Permanent | Immutable once published |

## 8.3 Corporate Bond Spreads (FRED)

Federal Reserve Bank of St. Louis (FRED) provides free access to corporate yield indices. These provide credit risk context for dividend sustainability analysis.

### Key Series

| FRED Series | Description | Use Case |
|---|---|---|
| `AAA` | Moody's Seasoned Aaa Corporate Bond Yield | High-quality corporate baseline |
| `BAA` | Moody's Seasoned Baa Corporate Bond Yield | Investment-grade floor |
| `BAMLH0A0HYM2` | ICE BofA US High Yield OAS | High-yield spread indicator |

```java
@Service
@RequiredArgsConstructor
public class CorporateYieldService {

    /**
     * Fetch corporate bond yield from FRED API.
     * Requires a free FRED API key (configured in application.yml).
     */
    public BigDecimal getCorporateYield(String seriesId, LocalDate date) {
        String url = String.format(
            "https://api.stlouisfed.org/fred/series/observations?series_id=%s" +
            "&observation_start=%s&observation_end=%s&api_key=%s&file_type=json",
            seriesId, date.minusDays(7), date, fredApiKey);

        String json = fetchWithCache("fred", url);
        return parseLatestObservation(json);
    }

    /**
     * Compute dividend yield relative to corporate yields.
     * If dividend yield > Baa yield → historically attractive for income investors.
     */
    public YieldComparison compareToFixedIncome(BigDecimal dividendYield, LocalDate date) {
        YieldComparison comparison = new YieldComparison();
        comparison.setDividendYield(dividendYield);
        comparison.setTreasury10Y(treasuryYieldService.get10YearYield(date));
        comparison.setAaaCorporate(getCorporateYield("AAA", date));
        comparison.setBaaCorporate(getCorporateYield("BAA", date));
        comparison.setDate(date);

        // Spread calculations
        if (comparison.getTreasury10Y() != null) {
            comparison.setSpreadOverRiskFree(
                dividendYield.subtract(comparison.getTreasury10Y()));
        }
        if (comparison.getBaaCorporate() != null) {
            comparison.setSpreadOverBaa(
                dividendYield.subtract(comparison.getBaaCorporate()));
        }

        return comparison;
    }

    @Data
    public static class YieldComparison {
        private BigDecimal dividendYield;
        private BigDecimal treasury10Y;
        private BigDecimal aaaCorporate;
        private BigDecimal baaCorporate;
        private BigDecimal spreadOverRiskFree;
        private BigDecimal spreadOverBaa;
        private LocalDate date;
    }
}
```

### Configuration

```yaml
edgar4j:
  external:
    fred:
      api-key: ${FRED_API_KEY:}
      enabled: ${FRED_ENABLED:false}
    treasury:
      enabled: true
```

## 8.4 Dashboard Integration

Add these market-based metrics to the dividend overview API:

```json
{
  "marketMetrics": {
    "dividendYield": 0.0054,
    "shareholderYield": 0.042,
    "buybackYield": 0.037,
    "currentPrice": 178.50,
    "priceDate": "2025-11-14"
  },
  "yieldComparison": {
    "dividendYield": 0.54,
    "treasury10Y": 4.28,
    "aaaCorporate": 5.12,
    "baaCorporate": 5.89,
    "spreadOverRiskFree": -3.74,
    "note": "Dividend yield below risk-free; equity premium comes from capital appreciation and dividend growth"
  }
}
```

## 8.5 Historical Yield Comparison Chart

For the dashboard timeline, compute historical yield comparisons:

```java
/**
 * Build a time series comparing dividend yield vs Treasury 10Y.
 * Useful for identifying when dividend stocks become attractive relative to bonds.
 */
public Map<LocalDate, YieldComparison> getHistoricalYieldComparison(
        String cik, String ticker, LocalDate since) {
    Map<LocalDate, BigDecimal> dpsHistory = factRepository.getAnnualTimeSeries(cik, "DividendsPerShare");
    List<StockPrice> priceHistory = marketDataService.getHistoricalPrices(ticker, since, LocalDate.now()).join();

    Map<LocalDate, YieldComparison> result = new LinkedHashMap<>();

    for (StockPrice price : priceHistory) {
        BigDecimal annualDps = findNearestAnnualDps(dpsHistory, price.getDate());
        if (annualDps != null && price.getClose() != null && price.getClose().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal yield = annualDps.divide(price.getClose(), 4, RoundingMode.HALF_UP);
            result.put(price.getDate(), compareToFixedIncome(yield, price.getDate()));
        }
    }

    return result;
}
```

## Validation Checklist

- [ ] Dividend yield computed correctly: DPS / price
- [ ] Shareholder yield includes buybacks
- [ ] Treasury 10Y yield fetched and cached
- [ ] FRED corporate yields work (when API key configured)
- [ ] Yield comparison correctly identifies spread over risk-free
- [ ] Graceful degradation when external sources unavailable (EDGAR metrics still work)
- [ ] Historical yield comparison chart data correct

## Estimated Effort: 3-4 days
