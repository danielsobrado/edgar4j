import type { ComprehensiveAnalysis, MarketPriceHistory } from '../api';
import type { FilingSnapshot } from './fundamentals';
import { buildDividendViabilityOverview } from './dividendViability';

function makeAnalysis(
  standardizedValues: Record<string, number>,
  periodEnd: string,
): ComprehensiveAnalysis {
  return {
    summary: {
      documentUri: 'https://www.sec.gov/test.htm',
      format: 'INLINE_XBRL',
      parseTime: '2026-03-12T00:00:00Z',
      entityIdentifier: '0000789019',
      totalFacts: 100,
      totalContexts: 4,
      totalUnits: 2,
      factsByType: {},
      factsByNamespace: {},
      parseTimeMs: 100,
      successRate: 1,
      nestedFactsExtracted: 0,
      warnings: 0,
      errors: 0,
    },
    secMetadata: {
      entityName: 'MICROSOFT CORP',
      cik: '0000789019',
      tradingSymbol: 'MSFT',
      securityExchange: 'NASDAQ',
      formType: '10-K',
      isAmendment: false,
      documentPeriodEndDate: periodEnd,
      fiscalYear: Number(periodEnd.slice(0, 4)),
      fiscalPeriod: 'FY',
      fiscalYearEndDate: '06-30',
      sharesOutstanding: 100,
      filingCategory: 'ANNUAL_REPORT',
      deiData: {},
    },
    keyFinancials: {},
    standardizedValues,
    unmappedConcepts: 0,
    calculationValidation: {
      totalChecks: 10,
      validCalculations: 10,
      errors: 0,
      isValid: true,
    },
  };
}

function makeAnnualSnapshot(
  filingDate: string,
  periodEnd: string,
  dividendsPerShare: number,
  dividendsPaid: number = dividendsPerShare * 100,
): FilingSnapshot {
  return {
    kind: 'annual',
    filing: {
      id: `annual-${periodEnd}`,
      companyName: 'MICROSOFT CORP',
      ticker: 'MSFT',
      cik: '0000789019',
      formType: '10-K',
      filingDate,
      accessionNumber: `0000789019-${periodEnd.slice(2, 4)}-000001`,
      primaryDocument: 'msft10k.htm',
      isXBRL: true,
      isInlineXBRL: true,
      filingUrl: `https://www.sec.gov/Archives/edgar/data/789019/${periodEnd.replace(/-/g, '')}/msft10k.htm`,
    },
    analysis: makeAnalysis({
      Revenue: 1_000,
      OperatingCashFlow: 300,
      CapitalExpenditures: 80,
      DividendsPerShare: dividendsPerShare,
      DividendsPaid: dividendsPaid,
      TotalCurrentAssets: 700,
      TotalCurrentLiabilities: 350,
      Cash: 200,
      LongTermDebt: 100,
      OperatingIncome: 250,
      DepreciationAmortization: 40,
      InterestExpense: 10,
      SharesOutstanding: 100,
    }, periodEnd),
  };
}

describe('dividend viability utils', () => {
  it('builds a safe dividend overview from a growing annual history', () => {
    const annualSnapshots = [
      makeAnnualSnapshot('2021-08-01', '2021-06-30', 1.0),
      makeAnnualSnapshot('2022-08-01', '2022-06-30', 1.1),
      makeAnnualSnapshot('2023-08-01', '2023-06-30', 1.2),
      makeAnnualSnapshot('2024-08-01', '2024-06-30', 1.32),
      makeAnnualSnapshot('2025-08-01', '2025-06-30', 1.45),
      makeAnnualSnapshot('2026-08-01', '2026-06-30', 1.6),
    ];
    const latestQuarterly: FilingSnapshot = {
      kind: 'quarterly',
      filing: {
        id: 'quarterly-2026-09-30',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-Q',
        filingDate: '2026-11-01',
        accessionNumber: '0000789019-26-000100',
        primaryDocument: 'msft10q.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/20260930/msft10q.htm',
      },
      analysis: makeAnalysis({
        TotalCurrentAssets: 720,
        TotalCurrentLiabilities: 360,
        Cash: 220,
        LongTermDebt: 90,
      }, '2026-09-30'),
    };
    const priceHistory: MarketPriceHistory = {
      ticker: 'MSFT',
      provider: 'TIINGO',
      startDate: '2025-01-01',
      endDate: '2026-12-31',
      prices: [
        { date: '2026-12-29', open: 39, high: 40, low: 38, close: 39, volume: 1_100_000 },
        { date: '2026-12-30', open: 39, high: 40, low: 38, close: 39.5, volume: 1_050_000 },
        { date: '2026-12-31', open: 39.5, high: 40.5, low: 39, close: 40, volume: 1_000_000 },
      ],
    };

    const overview = buildDividendViabilityOverview(annualSnapshots, latestQuarterly, priceHistory);

    expect(overview.rating).toBe('SAFE');
    expect(overview.score).toBeGreaterThanOrEqual(80);
    expect(overview.alerts).toHaveLength(0);
    expect(overview.snapshot.dpsLatest).toBe(1.6);
    expect(overview.snapshot.dpsCagr5y).toBeCloseTo(0.0986, 3);
    expect(overview.snapshot.fcfPayoutRatio).toBeCloseTo(160 / 220, 4);
    expect(overview.snapshot.uninterruptedYears).toBe(6);
    expect(overview.snapshot.consecutiveRaises).toBe(5);
    expect(overview.snapshot.currentRatio).toBe(2);
    expect(overview.snapshot.interestCoverage).toBe(25);
    expect(overview.snapshot.dividendYield).toBeCloseTo(0.04, 4);
  });

  it('flags cuts and strained coverage when the latest annual dividend weakens', () => {
    const stressedAnnualSnapshot = {
      ...makeAnnualSnapshot('2026-08-01', '2026-06-30', 0.9, 220),
      analysis: makeAnalysis({
        Revenue: 700,
        OperatingCashFlow: 180,
        CapitalExpenditures: 90,
        DividendsPerShare: 0.9,
        DividendsPaid: 220,
        TotalCurrentAssets: 150,
        TotalCurrentLiabilities: 250,
        Cash: 20,
        LongTermDebt: 500,
        OperatingIncome: 30,
        DepreciationAmortization: 10,
        InterestExpense: 20,
        SharesOutstanding: 100,
      }, '2026-06-30'),
    };
    const annualSnapshots = [
      makeAnnualSnapshot('2021-08-01', '2021-06-30', 1.0),
      makeAnnualSnapshot('2022-08-01', '2022-06-30', 1.1),
      makeAnnualSnapshot('2023-08-01', '2023-06-30', 1.2),
      makeAnnualSnapshot('2024-08-01', '2024-06-30', 1.25),
      makeAnnualSnapshot('2025-08-01', '2025-06-30', 1.3, 120),
      stressedAnnualSnapshot,
    ];

    const overview = buildDividendViabilityOverview(annualSnapshots, stressedAnnualSnapshot, null);

    expect(overview.rating).toBe('AT_RISK');
    expect(overview.alerts.map((alert) => alert.id)).toContain('dividend-cut');
    expect(overview.alerts.map((alert) => alert.id)).toContain('fcf-payout');
    expect(overview.alerts.map((alert) => alert.id)).toContain('current-ratio');
    expect(overview.alerts.map((alert) => alert.id)).toContain('interest-coverage');
    expect(overview.snapshot.currentRatio).toBeCloseTo(0.6, 4);
    expect(overview.snapshot.fcfPayoutRatio).toBeCloseTo(220 / 90, 4);
    expect(overview.warnings).toHaveLength(1);
  });
});

