import { describe, expect, it } from 'vitest';
import type { ComprehensiveAnalysis, Filing, MarketPriceHistory } from '../api';
import {
  buildAnnualTrendPoints,
  buildFundamentalSections,
  buildPriceSnapshot,
  splitFundamentalFilings,
} from './fundamentals';

function makeAnalysis(standardizedValues: Record<string, number>): ComprehensiveAnalysis {
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
      documentPeriodEndDate: '2025-12-31',
      fiscalYear: 2025,
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

function findMetricDisplay(
  sections: ReturnType<typeof buildFundamentalSections>,
  label: string,
): string | undefined {
  return sections.flatMap((section) => section.metrics).find((metric) => metric.label === label)?.display;
}

describe('fundamentals utils', () => {
  it('splits recent annual and quarterly filings and keeps newest first', () => {
    const filings: Filing[] = [
      {
        id: 'annual-newer',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-K',
        filingDate: '2026-02-01',
        accessionNumber: '0000789019-26-000010',
        primaryDocument: 'msft10k.htm',
        isXBRL: true,
        isInlineXBRL: true,
      },
      {
        id: 'quarterly',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-Q',
        filingDate: '2025-11-01',
        accessionNumber: '0000789019-25-000010',
        primaryDocument: 'msft10q.htm',
        isXBRL: true,
        isInlineXBRL: true,
      },
      {
        id: 'annual-older',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-K',
        filingDate: '2025-02-01',
        accessionNumber: '0000789019-25-000001',
        primaryDocument: 'msft10kold.htm',
        isXBRL: true,
        isInlineXBRL: true,
      },
    ];

    const split = splitFundamentalFilings(filings);

    expect(split.annual).toHaveLength(2);
    expect(split.annual[0].id).toBe('annual-newer');
    expect(split.quarterly).toHaveLength(1);
    expect(split.quarterly[0].filingUrl).toContain('/msft10q.htm');
  });

  it('derives valuation and profitability metrics from annual, quarterly, and market inputs', () => {
    const annual = {
      kind: 'annual' as const,
      filing: {
        id: 'annual',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-K',
        filingDate: '2026-02-01',
        accessionNumber: '0000789019-26-000010',
        primaryDocument: 'msft10k.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/000078901926000010/msft10k.htm',
      },
      analysis: makeAnalysis({
        Revenue: 1000,
        GrossProfit: 600,
        OperatingIncome: 250,
        NetIncome: 200,
        EarningsPerShareDiluted: 4,
        TotalAssets: 2000,
        TotalEquity: 800,
        Cash: 150,
        LongTermDebt: 300,
        TotalCurrentAssets: 700,
        TotalCurrentLiabilities: 350,
        SharesOutstanding: 100,
        OperatingCashFlow: 260,
        CapitalExpenditures: 80,
      }),
    };
    const quarterly = {
      kind: 'quarterly' as const,
      filing: {
        id: 'quarterly',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-Q',
        filingDate: '2025-11-01',
        accessionNumber: '0000789019-25-000010',
        primaryDocument: 'msft10q.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/000078901925000010/msft10q.htm',
      },
      analysis: makeAnalysis({
        Revenue: 280,
        GrossProfit: 170,
        OperatingIncome: 70,
        NetIncome: 55,
        EarningsPerShareDiluted: 1.1,
        TotalAssets: 2100,
        TotalEquity: 850,
        Cash: 180,
        LongTermDebt: 320,
        TotalCurrentAssets: 720,
        TotalCurrentLiabilities: 360,
        SharesOutstanding: 100,
      }),
    };
    const priceHistory: MarketPriceHistory = {
      ticker: 'MSFT',
      provider: 'TIINGO',
      startDate: '2025-03-12',
      endDate: '2026-03-12',
      prices: [
        { date: '2026-03-10', open: 48, high: 49, low: 47, close: 48, volume: 1200000 },
        { date: '2026-03-11', open: 49, high: 50, low: 48, close: 49, volume: 1100000 },
        { date: '2026-03-12', open: 49.5, high: 51, low: 49, close: 50, volume: 1000000 },
      ],
    };

    const sections = buildFundamentalSections(annual, quarterly, priceHistory);
    const valuationSection = sections.find((section) => section.id === 'valuation');
    const annualSection = sections.find((section) => section.id === 'annual');
    const quarterlySection = sections.find((section) => section.id === 'quarterly');
    const priceMetric = valuationSection?.metrics.find((metric) => metric.label === 'Price');

    expect(findMetricDisplay(sections, 'Market Cap')).toBe('$5.00K');
    expect(findMetricDisplay(sections, 'Enterprise Value')).toBe('$5.14K');
    expect(findMetricDisplay(sections, 'P/E')).toBe('12.50x');
    expect(findMetricDisplay(sections, 'P/B')).toBe('5.88x');
    expect(findMetricDisplay(sections, 'Gross Margin')).toBe('60.00%');
    expect(findMetricDisplay(sections, 'Current Ratio')).toBe('2.00x');
    expect(annualSection?.caption).toBe('Ratios from 10-K filed Feb 1, 2026.');
    expect(quarterlySection?.caption).toBe('Ratios from 10-Q filed Nov 1, 2025.');
    expect(priceMetric?.note).toBe('As of Mar 12, 2026');
  });

  it('builds a compact price snapshot from market bars', () => {
    const history: MarketPriceHistory = {
      ticker: 'MSFT',
      provider: 'TIINGO',
      startDate: '2024-03-12',
      endDate: '2026-03-12',
      prices: [
        { date: '2024-03-12', open: 90, high: 120, low: 40, close: 80, volume: 900000 },
        { date: '2026-03-11', open: 49, high: 52, low: 48, close: 50, volume: 1500000 },
        { date: '2026-03-12', open: 50, high: 53, low: 49, close: 51, volume: 1000000 },
      ],
    };

    const snapshot = buildPriceSnapshot(history);

    expect(snapshot.latestClose).toBe(51);
    expect(snapshot.previousClose).toBe(50);
    expect(snapshot.change).toBe(1);
    expect(snapshot.high52Week).toBe(53);
    expect(snapshot.low52Week).toBe(48);
    expect(snapshot.bars).toBe(2);
  });

  it('does not backfill annual-only metrics from quarterly data when annual filing is missing', () => {
    const quarterly = {
      kind: 'quarterly' as const,
      filing: {
        id: 'quarterly',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-Q',
        filingDate: '2025-11-01',
        accessionNumber: '0000789019-25-000010',
        primaryDocument: 'msft10q.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/000078901925000010/msft10q.htm',
      },
      analysis: makeAnalysis({
        Revenue: 280,
        GrossProfit: 170,
        OperatingIncome: 70,
        NetIncome: 55,
        EarningsPerShareDiluted: 1.1,
        TotalEquity: 850,
        Cash: 180,
        LongTermDebt: 320,
        TotalCurrentAssets: 720,
        TotalCurrentLiabilities: 360,
        SharesOutstanding: 100,
      }),
    };

    const sections = buildFundamentalSections(null, quarterly, null);
    const annualSection = sections.find((section) => section.id === 'annual');
    const currentRatio = annualSection?.metrics.find((metric) => metric.label === 'Current Ratio');
    const debtToEquity = annualSection?.metrics.find((metric) => metric.label === 'Debt/Equity');

    expect(currentRatio?.display).toBe('-');
    expect(debtToEquity?.display).toBe('-');
  });

  it('builds annual earnings and dividend trend points with cash-dividend fallback', () => {
    const olderAnnual = {
      kind: 'annual' as const,
      filing: {
        id: 'annual-2024',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-K',
        filingDate: '2024-07-30',
        accessionNumber: '0000789019-24-000010',
        primaryDocument: 'msft2024.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/000078901924000010/msft2024.htm',
      },
      analysis: {
        ...makeAnalysis({
          EarningsPerShareDiluted: 10,
          PaymentsOfDividendsCommonStock: 250,
          SharesOutstanding: 100,
        }),
        secMetadata: {
          ...makeAnalysis({}).secMetadata,
          documentPeriodEndDate: '2024-06-30',
        },
      },
    };

    const newerAnnual = {
      kind: 'annual' as const,
      filing: {
        id: 'annual-2025',
        companyName: 'MICROSOFT CORP',
        ticker: 'MSFT',
        cik: '0000789019',
        formType: '10-K',
        filingDate: '2025-07-30',
        accessionNumber: '0000789019-25-000010',
        primaryDocument: 'msft2025.htm',
        isXBRL: true,
        isInlineXBRL: true,
        filingUrl: 'https://www.sec.gov/Archives/edgar/data/789019/000078901925000010/msft2025.htm',
      },
      analysis: {
        ...makeAnalysis({
          EarningsPerShareDiluted: 12,
          DividendsPerShare: 3.5,
        }),
        secMetadata: {
          ...makeAnalysis({}).secMetadata,
          documentPeriodEndDate: '2025-06-30',
        },
      },
    };

    const points = buildAnnualTrendPoints([newerAnnual, olderAnnual]);

    expect(points).toHaveLength(2);
    expect(points[0].filingId).toBe('annual-2024');
    expect(points[0].earningsPerShare).toBe(10);
    expect(points[0].dividendsPerShare).toBe(2.5);
    expect(points[1].filingId).toBe('annual-2025');
    expect(points[1].dividendsPerShare).toBe(3.5);
  });
});
