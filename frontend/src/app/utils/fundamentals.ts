import type { ComprehensiveAnalysis, Filing, MarketPriceHistory } from '../api';

export type FilingBucket = 'annual' | 'quarterly';

export interface FilingCandidate extends Filing {
  filingUrl: string;
}

export interface FilingSnapshot {
  kind: FilingBucket;
  filing: FilingCandidate;
  analysis: ComprehensiveAnalysis;
}

export interface PriceSnapshot {
  latestClose: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  high52Week: number | null;
  low52Week: number | null;
  averageVolume30: number | null;
  bars: number;
  asOf: string | null;
}

export interface MetricCell {
  label: string;
  display: string;
  value: number | null;
  note?: string;
  tone?: 'neutral' | 'positive' | 'negative';
}

export interface MetricSection {
  id: string;
  title: string;
  caption: string;
  metrics: MetricCell[];
}

export interface AnnualTrendPoint {
  filingId: string;
  filingDate: string | null;
  periodEnd: string | null;
  earningsPerShare: number | null;
  dividendsPerShare: number | null;
}

export const ANNUAL_FORM_TYPES = ['10-K', '10-K/A', '20-F', '20-F/A', '40-F', '40-F/A'] as const;
export const QUARTERLY_FORM_TYPES = ['10-Q', '10-Q/A'] as const;

const ANNUAL_FORMS = new Set(ANNUAL_FORM_TYPES);
const QUARTERLY_FORMS = new Set(QUARTERLY_FORM_TYPES);

function getSortableDate(value?: string | null): number {
  if (!value) {
    return 0;
  }

  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? 0 : timestamp;
}

function formatDisplayDate(value?: string | null): string {
  if (!value) {
    return 'unknown date';
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

function buildSecPrimaryDocumentUrl(filing: Filing): string | null {
  if (!filing.primaryDocument || !filing.accessionNumber || !filing.cik) {
    return null;
  }

  const rawCik = filing.cik.replace(/^0+/, '') || filing.cik;
  const accession = filing.accessionNumber.replace(/-/g, '');
  return `https://www.sec.gov/Archives/edgar/data/${rawCik}/${accession}/${filing.primaryDocument}`;
}

export function getFilingUrlCandidates(filing: Filing): string[] {
  const candidates = [
    buildSecPrimaryDocumentUrl(filing),
    filing.url ?? null,
  ].filter((value): value is string => Boolean(value));

  return Array.from(new Set(candidates));
}

export function buildFilingCandidate(filing: Filing): FilingCandidate | null {
  const filingUrl = getFilingUrlCandidates(filing)[0];
  if (!filingUrl) {
    return null;
  }

  return {
    ...filing,
    filingUrl,
  };
}

export function splitFundamentalFilings(filings: Filing[]) {
  const enriched = filings
    .filter((filing) => filing.isXBRL || filing.isInlineXBRL || Boolean(filing.primaryDocument) || Boolean(filing.url))
    .map(buildFilingCandidate)
    .filter((filing): filing is FilingCandidate => Boolean(filing))
    .sort((left, right) => getSortableDate(right.filingDate) - getSortableDate(left.filingDate));

  return {
    annual: enriched.filter((filing) => ANNUAL_FORMS.has(filing.formType)),
    quarterly: enriched.filter((filing) => QUARTERLY_FORMS.has(filing.formType)),
  };
}

function getStandardizedNumber(
  analysis: ComprehensiveAnalysis | null | undefined,
  keys: string[],
): number | null {
  if (!analysis) {
    return null;
  }

  for (const key of keys) {
    const raw = analysis.standardizedValues?.[key];
    if (typeof raw === 'number' && Number.isFinite(raw)) {
      return raw;
    }
  }

  return null;
}

function getFinancialNumber(
  analysis: ComprehensiveAnalysis | null | undefined,
  keys: string[],
): number | null {
  if (!analysis) {
    return null;
  }

  for (const key of keys) {
    const raw = analysis.keyFinancials?.[key];
    if (typeof raw === 'number' && Number.isFinite(raw)) {
      return raw;
    }
  }

  return null;
}

export function getMetricNumber(
  analysis: ComprehensiveAnalysis | null | undefined,
  standardizedKeys: string[],
  financialKeys: string[] = [],
): number | null {
  return getStandardizedNumber(analysis, standardizedKeys) ?? getFinancialNumber(analysis, financialKeys);
}

function safeDivide(numerator: number | null, denominator: number | null): number | null {
  if (numerator == null || denominator == null || denominator === 0) {
    return null;
  }

  const result = numerator / denominator;
  return Number.isFinite(result) ? result : null;
}

function getSharesOutstanding(analysis: ComprehensiveAnalysis | null | undefined): number | null {
  return getMetricNumber(
    analysis,
    ['SharesOutstanding'],
    ['CommonStockSharesOutstanding'],
  ) ?? (analysis?.secMetadata?.sharesOutstanding ?? null);
}

function getDividendsPerShare(analysis: ComprehensiveAnalysis | null | undefined): number | null {
  const reportedDividendsPerShare = getMetricNumber(
    analysis,
    [
      'DividendsPerShare',
      'CommonStockDividendsPerShareDeclared',
      'CommonStockDividendsPerShareCashPaid',
      'CommonStockDividendsPerShareDeclaredAndPaid',
    ],
    [
      'CommonStockDividendsPerShareDeclared',
      'CommonStockDividendsPerShareCashPaid',
      'CommonStockDividendsPerShareDeclaredAndPaid',
      'DividendsPerShare',
    ],
  );
  if (reportedDividendsPerShare != null) {
    return reportedDividendsPerShare;
  }

  const dividendsPaid = getMetricNumber(
    analysis,
    [
      'DividendsPaid',
      'PaymentsOfDividendsCommonStock',
      'DividendsCommonStockCash',
      'PaymentsOfOrdinaryDividends',
    ],
    [
      'PaymentsOfDividendsCommonStock',
      'DividendsCommonStockCash',
      'PaymentsOfOrdinaryDividends',
    ],
  );

  return safeDivide(dividendsPaid, getSharesOutstanding(analysis));
}

function formatCurrency(value: number | null, maximumFractionDigits: number = 2): string {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits,
  }).format(value);
}

function formatCompactCurrency(value: number | null): string {
  if (value == null) {
    return '-';
  }

  const absValue = Math.abs(value);
  if (absValue >= 1_000_000_000_000) {
    return `${value < 0 ? '-' : ''}$${(absValue / 1_000_000_000_000).toFixed(2)}T`;
  }
  if (absValue >= 1_000_000_000) {
    return `${value < 0 ? '-' : ''}$${(absValue / 1_000_000_000).toFixed(2)}B`;
  }
  if (absValue >= 1_000_000) {
    return `${value < 0 ? '-' : ''}$${(absValue / 1_000_000).toFixed(2)}M`;
  }
  if (absValue >= 1_000) {
    return `${value < 0 ? '-' : ''}$${(absValue / 1_000).toFixed(2)}K`;
  }
  return formatCurrency(value, 0);
}

function formatCompactNumber(value: number | null): string {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(value);
}

function formatPercent(value: number | null): string {
  if (value == null) {
    return '-';
  }

  return `${(value * 100).toFixed(2)}%`;
}

function formatMultiple(value: number | null): string {
  if (value == null) {
    return '-';
  }

  return `${value.toFixed(2)}x`;
}

function metric(
  label: string,
  value: number | null,
  display: string,
  note?: string,
  tone: MetricCell['tone'] = 'neutral',
): MetricCell {
  return { label, value, display, note, tone };
}

export function buildPriceSnapshot(history: MarketPriceHistory | null | undefined): PriceSnapshot {
  const prices = history?.prices ?? [];
  const latestBar = prices.at(-1) ?? null;
  const previousBar = prices.at(-2) ?? null;
  const last30 = prices.slice(-30);
  const latestTimestamp = latestBar?.date ? Date.parse(latestBar.date) : NaN;
  const trailingYearThreshold = Number.isNaN(latestTimestamp)
    ? null
    : latestTimestamp - (365 * 24 * 60 * 60 * 1000);
  const trailingYearPrices = trailingYearThreshold == null
    ? prices
    : prices.filter((price) => {
      const timestamp = Date.parse(price.date);
      return !Number.isNaN(timestamp) && timestamp >= trailingYearThreshold;
    });
  const totalVolume = last30.reduce((sum, price) => sum + (price.volume ?? 0), 0);

  const latestClose = latestBar?.close ?? null;
  const previousClose = previousBar?.close ?? null;
  const change = latestClose != null && previousClose != null ? latestClose - previousClose : null;
  const changePercent = change != null && previousClose ? change / previousClose : null;

  return {
    latestClose,
    previousClose,
    change,
    changePercent,
    high52Week: trailingYearPrices.length > 0 ? Math.max(...trailingYearPrices.map((price) => price.high)) : null,
    low52Week: trailingYearPrices.length > 0 ? Math.min(...trailingYearPrices.map((price) => price.low)) : null,
    averageVolume30: last30.length > 0 ? totalVolume / last30.length : null,
    bars: trailingYearPrices.length,
    asOf: latestBar?.date ?? null,
  };
}

export function buildAnnualTrendPoints(filings: FilingSnapshot[]): AnnualTrendPoint[] {
  return filings
    .map((snapshot) => ({
      filingId: snapshot.filing.id,
      filingDate: snapshot.filing.filingDate ?? null,
      periodEnd: snapshot.analysis.secMetadata.documentPeriodEndDate
        ?? snapshot.filing.reportDate
        ?? snapshot.filing.filingDate
        ?? null,
      earningsPerShare: getMetricNumber(
        snapshot.analysis,
        ['EarningsPerShareDiluted'],
        ['EarningsPerShareDiluted'],
      ),
      dividendsPerShare: getDividendsPerShare(snapshot.analysis),
    }))
    .filter((point) => point.earningsPerShare != null || point.dividendsPerShare != null)
    .sort(
      (left, right) =>
        getSortableDate(left.periodEnd ?? left.filingDate)
        - getSortableDate(right.periodEnd ?? right.filingDate),
    );
}

export function buildFundamentalSections(
  annual: FilingSnapshot | null,
  quarterly: FilingSnapshot | null,
  priceHistory: MarketPriceHistory | null | undefined,
): MetricSection[] {
  const price = buildPriceSnapshot(priceHistory);
  const annualBalanceAnalysis = annual?.analysis ?? null;
  const quarterlyBalanceAnalysis = quarterly?.analysis ?? null;
  const valuationBalanceAnalysis = quarterlyBalanceAnalysis ?? annualBalanceAnalysis ?? null;

  const sharesOutstanding = getSharesOutstanding(valuationBalanceAnalysis);

  const annualRevenue = getMetricNumber(
    annual?.analysis,
    ['Revenue'],
    ['Revenues', 'RevenueFromContractWithCustomerExcludingAssessedTax'],
  );
  const annualGrossProfit = getMetricNumber(annual?.analysis, ['GrossProfit'], ['GrossProfit']);
  const annualOperatingIncome = getMetricNumber(annual?.analysis, ['OperatingIncome'], ['OperatingIncomeLoss']);
  const annualNetIncome = getMetricNumber(annual?.analysis, ['NetIncome'], ['NetIncomeLoss']);
  const annualDilutedEps = getMetricNumber(annual?.analysis, ['EarningsPerShareDiluted'], ['EarningsPerShareDiluted']);
  const annualOperatingCashFlow = getMetricNumber(
    annual?.analysis,
    ['OperatingCashFlow'],
    ['NetCashProvidedByUsedInOperatingActivities'],
  );
  const annualCapex = getMetricNumber(annual?.analysis, ['CapitalExpenditures']);

  const quarterlyRevenue = getMetricNumber(
    quarterly?.analysis,
    ['Revenue'],
    ['Revenues', 'RevenueFromContractWithCustomerExcludingAssessedTax'],
  );
  const quarterlyGrossProfit = getMetricNumber(quarterly?.analysis, ['GrossProfit'], ['GrossProfit']);
  const quarterlyOperatingIncome = getMetricNumber(quarterly?.analysis, ['OperatingIncome'], ['OperatingIncomeLoss']);
  const quarterlyNetIncome = getMetricNumber(quarterly?.analysis, ['NetIncome'], ['NetIncomeLoss']);
  const quarterlyDilutedEps = getMetricNumber(
    quarterly?.analysis,
    ['EarningsPerShareDiluted'],
    ['EarningsPerShareDiluted'],
  );

  const totalAssets = getMetricNumber(valuationBalanceAnalysis, ['TotalAssets'], ['Assets']);
  const totalEquity = getMetricNumber(valuationBalanceAnalysis, ['TotalEquity'], ['StockholdersEquity']);
  const cash = getMetricNumber(valuationBalanceAnalysis, ['Cash'], ['CashAndCashEquivalentsAtCarryingValue']);
  const longTermDebt = getMetricNumber(valuationBalanceAnalysis, ['LongTermDebt']);
  const currentAssets = getMetricNumber(valuationBalanceAnalysis, ['TotalCurrentAssets'], ['AssetsCurrent']);
  const currentLiabilities = getMetricNumber(valuationBalanceAnalysis, ['TotalCurrentLiabilities'], ['LiabilitiesCurrent']);

  const annualAssets = getMetricNumber(annualBalanceAnalysis, ['TotalAssets'], ['Assets']);
  const annualEquity = getMetricNumber(annualBalanceAnalysis, ['TotalEquity'], ['StockholdersEquity']);
  const annualLongTermDebt = getMetricNumber(annualBalanceAnalysis, ['LongTermDebt']);
  const annualCurrentAssets = getMetricNumber(annualBalanceAnalysis, ['TotalCurrentAssets'], ['AssetsCurrent']);
  const annualCurrentLiabilities = getMetricNumber(annualBalanceAnalysis, ['TotalCurrentLiabilities'], ['LiabilitiesCurrent']);

  const quarterlyEquity = getMetricNumber(quarterlyBalanceAnalysis, ['TotalEquity'], ['StockholdersEquity']);
  const quarterlyCash = getMetricNumber(quarterlyBalanceAnalysis, ['Cash'], ['CashAndCashEquivalentsAtCarryingValue']);
  const quarterlyLongTermDebt = getMetricNumber(quarterlyBalanceAnalysis, ['LongTermDebt']);
  const quarterlyCurrentAssetsOnly = getMetricNumber(quarterlyBalanceAnalysis, ['TotalCurrentAssets'], ['AssetsCurrent']);
  const quarterlyCurrentLiabilitiesOnly = getMetricNumber(quarterlyBalanceAnalysis, ['TotalCurrentLiabilities'], ['LiabilitiesCurrent']);

  const marketCap = price.latestClose != null && sharesOutstanding != null
    ? price.latestClose * sharesOutstanding
    : null;
  const enterpriseValue = marketCap != null
    ? marketCap + (longTermDebt ?? 0) - (cash ?? 0)
    : null;

  const bookValuePerShare = safeDivide(totalEquity, sharesOutstanding);
  const priceToBook = price.latestClose != null && bookValuePerShare != null && bookValuePerShare > 0
    ? price.latestClose / bookValuePerShare
    : null;
  const priceToEarnings = price.latestClose != null && annualDilutedEps != null && annualDilutedEps > 0
    ? price.latestClose / annualDilutedEps
    : null;
  const priceToSales = safeDivide(marketCap, annualRevenue);
  const enterpriseValueToSales = safeDivide(enterpriseValue, annualRevenue);
  const grossMargin = safeDivide(annualGrossProfit, annualRevenue);
  const operatingMargin = safeDivide(annualOperatingIncome, annualRevenue);
  const netMargin = safeDivide(annualNetIncome, annualRevenue);
  const roa = safeDivide(annualNetIncome, annualAssets);
  const roe = safeDivide(annualNetIncome, annualEquity);
  const currentRatio = safeDivide(annualCurrentAssets, annualCurrentLiabilities);
  const debtToEquity = safeDivide(annualLongTermDebt, annualEquity);
  const cashPerShare = safeDivide(cash, sharesOutstanding);
  const quarterlyGrossMargin = safeDivide(quarterlyGrossProfit, quarterlyRevenue);
  const quarterlyOperatingMargin = safeDivide(quarterlyOperatingIncome, quarterlyRevenue);
  const quarterlyNetMargin = safeDivide(quarterlyNetIncome, quarterlyRevenue);
  const quarterlyCurrentRatio = safeDivide(quarterlyCurrentAssetsOnly, quarterlyCurrentLiabilitiesOnly);
  const quarterlyDebtToEquity = safeDivide(quarterlyLongTermDebt, quarterlyEquity);

  const valuationCaption = sharesOutstanding != null
    ? 'Daily price metrics are computed from Tiingo bars and the latest shares outstanding found in the selected statements.'
    : 'Daily price metrics need both Tiingo bars and statement share counts.';
  const annualCaption = annual
    ? `Ratios from ${annual.filing.formType} filed ${formatDisplayDate(annual.filing.filingDate)}.`
    : 'No annual XBRL filing is available for this company yet.';
  const quarterlyCaption = quarterly
    ? `Ratios from ${quarterly.filing.formType} filed ${formatDisplayDate(quarterly.filing.filingDate)}.`
    : 'No quarterly XBRL filing is available for this company yet.';

  return [
    {
      id: 'valuation',
      title: 'Price And Valuation',
      caption: valuationCaption,
      metrics: [
        metric('Price', price.latestClose, formatCurrency(price.latestClose), price.asOf ? `As of ${formatDisplayDate(price.asOf)}` : undefined),
        metric(
          'Day Change',
          price.changePercent,
          price.change != null && price.changePercent != null
            ? `${formatCurrency(price.change)} (${formatPercent(price.changePercent)})`
            : '-',
          price.previousClose != null ? `Previous close ${formatCurrency(price.previousClose)}` : undefined,
          (price.change ?? 0) > 0 ? 'positive' : (price.change ?? 0) < 0 ? 'negative' : 'neutral',
        ),
        metric('52W High', price.high52Week, formatCurrency(price.high52Week)),
        metric('52W Low', price.low52Week, formatCurrency(price.low52Week)),
        metric('Avg Volume', price.averageVolume30, formatCompactNumber(price.averageVolume30), '30 trading days'),
        metric('Market Cap', marketCap, formatCompactCurrency(marketCap)),
        metric('Enterprise Value', enterpriseValue, formatCompactCurrency(enterpriseValue)),
        metric('P/E', priceToEarnings, formatMultiple(priceToEarnings), annualDilutedEps != null ? `Diluted EPS ${formatCurrency(annualDilutedEps)}` : undefined),
        metric('P/B', priceToBook, formatMultiple(priceToBook), bookValuePerShare != null ? `Book/share ${formatCurrency(bookValuePerShare)}` : undefined),
        metric('P/S', priceToSales, formatMultiple(priceToSales)),
        metric('EV/Sales', enterpriseValueToSales, formatMultiple(enterpriseValueToSales)),
        metric('Cash/Share', cashPerShare, formatCurrency(cashPerShare)),
      ],
    },
    {
      id: 'annual',
      title: 'Annual Fundamentals',
      caption: annualCaption,
      metrics: [
        metric('Revenue', annualRevenue, formatCompactCurrency(annualRevenue)),
        metric('Gross Profit', annualGrossProfit, formatCompactCurrency(annualGrossProfit)),
        metric('Operating Income', annualOperatingIncome, formatCompactCurrency(annualOperatingIncome)),
        metric('Net Income', annualNetIncome, formatCompactCurrency(annualNetIncome)),
        metric('EPS Diluted', annualDilutedEps, formatCurrency(annualDilutedEps)),
        metric('Gross Margin', grossMargin, formatPercent(grossMargin)),
        metric('Operating Margin', operatingMargin, formatPercent(operatingMargin)),
        metric('Net Margin', netMargin, formatPercent(netMargin)),
        metric('ROA', roa, formatPercent(roa)),
        metric('ROE', roe, formatPercent(roe)),
        metric('Current Ratio', currentRatio, formatMultiple(currentRatio)),
        metric('Debt/Equity', debtToEquity, formatMultiple(debtToEquity)),
        metric('Op Cash Flow', annualOperatingCashFlow, formatCompactCurrency(annualOperatingCashFlow)),
        metric('CapEx', annualCapex, formatCompactCurrency(annualCapex)),
      ],
    },
    {
      id: 'quarterly',
      title: 'Quarterly Fundamentals',
      caption: quarterlyCaption,
      metrics: [
        metric('Revenue', quarterlyRevenue, formatCompactCurrency(quarterlyRevenue)),
        metric('Gross Profit', quarterlyGrossProfit, formatCompactCurrency(quarterlyGrossProfit)),
        metric('Operating Income', quarterlyOperatingIncome, formatCompactCurrency(quarterlyOperatingIncome)),
        metric('Net Income', quarterlyNetIncome, formatCompactCurrency(quarterlyNetIncome)),
        metric('EPS Diluted', quarterlyDilutedEps, formatCurrency(quarterlyDilutedEps)),
        metric('Gross Margin', quarterlyGrossMargin, formatPercent(quarterlyGrossMargin)),
        metric('Operating Margin', quarterlyOperatingMargin, formatPercent(quarterlyOperatingMargin)),
        metric('Net Margin', quarterlyNetMargin, formatPercent(quarterlyNetMargin)),
        metric('Current Ratio', quarterlyCurrentRatio, formatMultiple(quarterlyCurrentRatio)),
        metric('Debt/Equity', quarterlyDebtToEquity, formatMultiple(quarterlyDebtToEquity)),
        metric('Long-Term Debt', quarterlyLongTermDebt, formatCompactCurrency(quarterlyLongTermDebt)),
        metric('Cash', quarterlyCash, formatCompactCurrency(quarterlyCash)),
        metric('Total Equity', quarterlyEquity, formatCompactCurrency(quarterlyEquity)),
      ],
    },
  ];
}
