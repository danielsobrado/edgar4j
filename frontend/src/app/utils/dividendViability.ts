import type { MarketPriceHistory } from '../api';
import {
  buildAnnualTrendPoints,
  buildPriceSnapshot,
  getMetricNumber,
  type AnnualTrendPoint,
  type FilingSnapshot,
} from './fundamentals';

export type DividendViabilityRating = 'SAFE' | 'STABLE' | 'WATCH' | 'AT_RISK';
export type DividendAlertSeverity = 'low' | 'medium' | 'high';

export interface DividendViabilityAlert {
  id: string;
  severity: DividendAlertSeverity;
  title: string;
  description: string;
}

export interface DividendViabilitySnapshot {
  dpsLatest: number | null;
  dpsCagr5y: number | null;
  fcfPayoutRatio: number | null;
  uninterruptedYears: number;
  consecutiveRaises: number;
  netDebtToEbitda: number | null;
  interestCoverage: number | null;
  currentRatio: number | null;
  fcfMargin: number | null;
  dividendYield: number | null;
}

export interface DividendCoverageMetrics {
  revenue: number | null;
  operatingCashFlow: number | null;
  capitalExpenditures: number | null;
  freeCashFlow: number | null;
  dividendsPaid: number | null;
  cashCoverage: number | null;
  retainedCash: number | null;
}

export interface DividendBalanceMetrics {
  cash: number | null;
  grossDebt: number | null;
  netDebt: number | null;
  ebitdaProxy: number | null;
  netDebtToEbitda: number | null;
  currentRatio: number | null;
  interestCoverage: number | null;
}

export interface DividendViabilityOverview {
  rating: DividendViabilityRating;
  score: number;
  alerts: DividendViabilityAlert[];
  snapshot: DividendViabilitySnapshot;
  coverage: DividendCoverageMetrics;
  balance: DividendBalanceMetrics;
  trend: AnnualTrendPoint[];
  latestAnnual: FilingSnapshot | null;
  latestBalance: FilingSnapshot | null;
  referencePrice: number | null;
  warnings: string[];
}

function safeDivide(numerator: number | null, denominator: number | null): number | null {
  if (numerator == null || denominator == null || denominator === 0) {
    return null;
  }

  const result = numerator / denominator;
  return Number.isFinite(result) ? result : null;
}

function magnitude(value: number | null): number | null {
  if (value == null) {
    return null;
  }

  return Math.abs(value);
}

function getSnapshotDate(snapshot: FilingSnapshot): number {
  const candidate = snapshot.analysis.secMetadata.documentPeriodEndDate
    ?? snapshot.filing.reportDate
    ?? snapshot.filing.filingDate
    ?? null;
  if (!candidate) {
    return 0;
  }

  const timestamp = Date.parse(candidate);
  return Number.isNaN(timestamp) ? 0 : timestamp;
}

function getLatestSnapshot(snapshots: FilingSnapshot[]): FilingSnapshot | null {
  if (snapshots.length === 0) {
    return null;
  }

  return [...snapshots].sort((left, right) => getSnapshotDate(right) - getSnapshotDate(left))[0] ?? null;
}

function getSharesOutstanding(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  return getMetricNumber(
    snapshot.analysis,
    ['SharesOutstanding'],
    ['CommonStockSharesOutstanding'],
  ) ?? (snapshot.analysis.secMetadata.sharesOutstanding ?? null);
}

function getDividendsPerShare(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  const reportedDividendsPerShare = getMetricNumber(
    snapshot.analysis,
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

  const dividendsPaid = magnitude(getMetricNumber(
    snapshot.analysis,
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
  ));

  return safeDivide(dividendsPaid, getSharesOutstanding(snapshot));
}

function getFreeCashFlow(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  const operatingCashFlow = getMetricNumber(
    snapshot.analysis,
    ['OperatingCashFlow'],
    ['NetCashProvidedByUsedInOperatingActivities'],
  );
  const capitalExpenditures = magnitude(getMetricNumber(
    snapshot.analysis,
    ['CapitalExpenditures'],
    ['PaymentsToAcquirePropertyPlantAndEquipment', 'PaymentsToAcquireProductiveAssets'],
  ));

  if (operatingCashFlow == null || capitalExpenditures == null) {
    return null;
  }

  return operatingCashFlow - capitalExpenditures;
}

function getGrossDebt(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  const longTermDebt = magnitude(getMetricNumber(snapshot.analysis, ['LongTermDebt'], ['LongTermDebt']));
  const shortTermDebt = magnitude(getMetricNumber(
    snapshot.analysis,
    ['DebtCurrent', 'ShortTermDebt'],
    ['DebtCurrent', 'LongTermDebtCurrent', 'ShortTermBorrowings'],
  ));

  if (longTermDebt == null && shortTermDebt == null) {
    return null;
  }

  return (longTermDebt ?? 0) + (shortTermDebt ?? 0);
}

function getEbitdaProxy(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  const operatingIncome = getMetricNumber(snapshot.analysis, ['OperatingIncome'], ['OperatingIncomeLoss']);
  const depreciationAmortization = magnitude(getMetricNumber(
    snapshot.analysis,
    ['DepreciationAmortization'],
    ['DepreciationDepletionAndAmortization', 'DepreciationAndAmortization'],
  ));

  if (operatingIncome == null || depreciationAmortization == null) {
    return null;
  }

  return operatingIncome + depreciationAmortization;
}

function getInterestCoverage(snapshot: FilingSnapshot | null): number | null {
  if (!snapshot) {
    return null;
  }

  const operatingIncome = getMetricNumber(snapshot.analysis, ['OperatingIncome'], ['OperatingIncomeLoss']);
  const interestExpense = magnitude(getMetricNumber(
    snapshot.analysis,
    ['InterestExpense'],
    ['InterestExpense', 'InterestExpenseDebt'],
  ));

  return safeDivide(operatingIncome, interestExpense);
}

function calculateCagr(points: AnnualTrendPoint[], years: number): number | null {
  const dividendPoints = points.filter((point) => point.dividendsPerShare != null);
  if (dividendPoints.length < years + 1) {
    return null;
  }

  const relevantPoints = dividendPoints.slice(-(years + 1));
  const firstValue = relevantPoints[0]?.dividendsPerShare ?? null;
  const lastValue = relevantPoints.at(-1)?.dividendsPerShare ?? null;

  if (firstValue == null || lastValue == null || firstValue <= 0 || lastValue <= 0) {
    return null;
  }

  const result = (lastValue / firstValue) ** (1 / years) - 1;
  return Number.isFinite(result) ? result : null;
}

function countUninterruptedYears(points: AnnualTrendPoint[]): number {
  let count = 0;

  for (let index = points.length - 1; index >= 0; index -= 1) {
    const dividends = points[index]?.dividendsPerShare ?? null;
    if (dividends == null || dividends <= 0) {
      break;
    }
    count += 1;
  }

  return count;
}

function countConsecutiveRaises(points: AnnualTrendPoint[]): number {
  let count = 0;

  for (let index = points.length - 1; index >= 1; index -= 1) {
    const current = points[index]?.dividendsPerShare ?? null;
    const previous = points[index - 1]?.dividendsPerShare ?? null;
    if (current == null || previous == null || current <= previous) {
      break;
    }
    count += 1;
  }

  return count;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function buildAlerts(input: {
  trend: AnnualTrendPoint[];
  fcfPayoutRatio: number | null;
  currentRatio: number | null;
  netDebtToEbitda: number | null;
  interestCoverage: number | null;
}): DividendViabilityAlert[] {
  const alerts: DividendViabilityAlert[] = [];
  const latest = input.trend.at(-1) ?? null;
  const previous = input.trend.length > 1 ? input.trend.at(-2) ?? null : null;

  if (
    latest?.dividendsPerShare != null
    && previous?.dividendsPerShare != null
    && previous.dividendsPerShare > 0
    && latest.dividendsPerShare < previous.dividendsPerShare
  ) {
    alerts.push({
      id: 'dividend-cut',
      severity: 'high',
      title: 'Dividend cut detected',
      description: 'The latest annual dividend-per-share value is below the prior year.',
    });
  }

  if (input.fcfPayoutRatio != null && input.fcfPayoutRatio > 0.85) {
    alerts.push({
      id: 'fcf-payout',
      severity: input.fcfPayoutRatio > 1 ? 'high' : 'medium',
      title: 'Elevated cash payout ratio',
      description: 'Dividends are consuming most of free cash flow.',
    });
  }

  if (input.currentRatio != null && input.currentRatio < 1) {
    alerts.push({
      id: 'current-ratio',
      severity: input.currentRatio < 0.8 ? 'high' : 'medium',
      title: 'Thin near-term liquidity',
      description: 'Current liabilities exceed or nearly exceed current assets.',
    });
  }

  if (input.netDebtToEbitda != null && input.netDebtToEbitda > 3.5) {
    alerts.push({
      id: 'net-debt-to-ebitda',
      severity: input.netDebtToEbitda > 5 ? 'high' : 'medium',
      title: 'Leverage is running hot',
      description: 'Net debt is elevated relative to EBITDA proxy.',
    });
  }

  if (input.interestCoverage != null && input.interestCoverage < 3) {
    alerts.push({
      id: 'interest-coverage',
      severity: input.interestCoverage < 2 ? 'high' : 'medium',
      title: 'Interest coverage is weak',
      description: 'Operating income has limited cushion versus interest expense.',
    });
  }

  return alerts;
}

function buildScore(snapshot: DividendViabilitySnapshot, alerts: DividendViabilityAlert[]): number {
  let score = 50;

  if (snapshot.dpsLatest != null && snapshot.dpsLatest > 0) {
    score += 10;
  }
  if (snapshot.dpsCagr5y != null) {
    if (snapshot.dpsCagr5y >= 0.08) {
      score += 12;
    } else if (snapshot.dpsCagr5y >= 0.03) {
      score += 6;
    } else if (snapshot.dpsCagr5y < 0) {
      score -= 10;
    }
  }
  if (snapshot.fcfPayoutRatio != null) {
    if (snapshot.fcfPayoutRatio <= 0.6) {
      score += 14;
    } else if (snapshot.fcfPayoutRatio <= 0.8) {
      score += 8;
    } else if (snapshot.fcfPayoutRatio > 1) {
      score -= 12;
    } else {
      score -= 6;
    }
  }
  if (snapshot.uninterruptedYears >= 10) {
    score += 10;
  } else if (snapshot.uninterruptedYears >= 5) {
    score += 5;
  }
  if (snapshot.consecutiveRaises >= 5) {
    score += 8;
  } else if (snapshot.consecutiveRaises >= 1) {
    score += 4;
  }
  if (snapshot.netDebtToEbitda != null) {
    if (snapshot.netDebtToEbitda <= 1.5) {
      score += 10;
    } else if (snapshot.netDebtToEbitda <= 3) {
      score += 4;
    } else {
      score -= 8;
    }
  }
  if (snapshot.interestCoverage != null) {
    if (snapshot.interestCoverage >= 8) {
      score += 8;
    } else if (snapshot.interestCoverage >= 4) {
      score += 4;
    } else if (snapshot.interestCoverage < 2) {
      score -= 8;
    }
  }
  if (snapshot.currentRatio != null) {
    if (snapshot.currentRatio >= 1.5) {
      score += 5;
    } else if (snapshot.currentRatio < 1) {
      score -= 5;
    }
  }
  if (snapshot.fcfMargin != null) {
    if (snapshot.fcfMargin >= 0.15) {
      score += 5;
    } else if (snapshot.fcfMargin < 0.05) {
      score -= 4;
    }
  }

  for (const alert of alerts) {
    if (alert.severity === 'high') {
      score -= 8;
    } else if (alert.severity === 'medium') {
      score -= 4;
    } else {
      score -= 2;
    }
  }

  return clamp(Math.round(score), 0, 100);
}

function toRating(score: number): DividendViabilityRating {
  if (score >= 80) {
    return 'SAFE';
  }
  if (score >= 65) {
    return 'STABLE';
  }
  if (score >= 45) {
    return 'WATCH';
  }
  return 'AT_RISK';
}

export function buildDividendViabilityOverview(
  annualSnapshots: FilingSnapshot[],
  balanceSnapshot: FilingSnapshot | null,
  priceHistory: MarketPriceHistory | null | undefined,
): DividendViabilityOverview {
  const latestAnnual = getLatestSnapshot(annualSnapshots);
  const latestBalance = balanceSnapshot ?? latestAnnual;
  const trend = buildAnnualTrendPoints(annualSnapshots);
  const latestTrendPoint = trend.at(-1) ?? null;

  const revenue = latestAnnual
    ? getMetricNumber(
      latestAnnual.analysis,
      ['Revenue'],
      ['Revenues', 'RevenueFromContractWithCustomerExcludingAssessedTax'],
    )
    : null;
  const operatingCashFlow = latestAnnual
    ? getMetricNumber(
      latestAnnual.analysis,
      ['OperatingCashFlow'],
      ['NetCashProvidedByUsedInOperatingActivities'],
    )
    : null;
  const capitalExpenditures = latestAnnual
    ? magnitude(getMetricNumber(
      latestAnnual.analysis,
      ['CapitalExpenditures'],
      ['PaymentsToAcquirePropertyPlantAndEquipment', 'PaymentsToAcquireProductiveAssets'],
    ))
    : null;
  const dividendsPaid = latestAnnual
    ? magnitude(getMetricNumber(
      latestAnnual.analysis,
      ['DividendsPaid', 'PaymentsOfDividendsCommonStock'],
      ['PaymentsOfDividendsCommonStock', 'DividendsCommonStockCash', 'PaymentsOfOrdinaryDividends'],
    ))
    : null;
  const freeCashFlow = getFreeCashFlow(latestAnnual);
  const cashCoverage = safeDivide(freeCashFlow, dividendsPaid);
  const retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;

  const cash = latestBalance
    ? getMetricNumber(latestBalance.analysis, ['Cash'], ['CashAndCashEquivalentsAtCarryingValue'])
    : null;
  const grossDebt = getGrossDebt(latestBalance);
  const netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
  const ebitdaProxy = getEbitdaProxy(latestAnnual);
  const netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0 ? safeDivide(netDebt, ebitdaProxy) : null;
  const currentAssets = latestBalance
    ? getMetricNumber(latestBalance.analysis, ['TotalCurrentAssets'], ['AssetsCurrent'])
    : null;
  const currentLiabilities = latestBalance
    ? getMetricNumber(latestBalance.analysis, ['TotalCurrentLiabilities'], ['LiabilitiesCurrent'])
    : null;
  const currentRatio = safeDivide(currentAssets, currentLiabilities);
  const interestCoverage = getInterestCoverage(latestAnnual);

  const priceSnapshot = buildPriceSnapshot(priceHistory);
  const referencePrice = priceSnapshot.latestClose;
  const dpsLatest = latestTrendPoint?.dividendsPerShare ?? getDividendsPerShare(latestAnnual);
  const dpsCagr5y = calculateCagr(trend, 5);
  const fcfPayoutRatio = dividendsPaid != null && freeCashFlow != null && freeCashFlow > 0
    ? safeDivide(dividendsPaid, freeCashFlow)
    : null;
  const fcfMargin = safeDivide(freeCashFlow, revenue);
  const dividendYield = referencePrice != null && referencePrice > 0 && dpsLatest != null
    ? dpsLatest / referencePrice
    : null;

  const snapshot: DividendViabilitySnapshot = {
    dpsLatest,
    dpsCagr5y,
    fcfPayoutRatio,
    uninterruptedYears: countUninterruptedYears(trend),
    consecutiveRaises: countConsecutiveRaises(trend),
    netDebtToEbitda,
    interestCoverage,
    currentRatio,
    fcfMargin,
    dividendYield,
  };

  const alerts = buildAlerts({
    trend,
    fcfPayoutRatio,
    currentRatio,
    netDebtToEbitda,
    interestCoverage,
  });
  const score = buildScore(snapshot, alerts);
  const warnings: string[] = [];

  if (trend.length < 6) {
    warnings.push('Fewer than six annual XBRL filings were available, so long-range dividend trend coverage is limited.');
  }
  if (referencePrice == null) {
    warnings.push('Market-price overlay is unavailable. Configure a market data provider in Settings to estimate dividend yield.');
  }
  if (!balanceSnapshot) {
    warnings.push('No recent quarterly balance-sheet filing was available, so liquidity and leverage use the latest annual report.');
  }

  return {
    rating: toRating(score),
    score,
    alerts,
    snapshot,
    coverage: {
      revenue,
      operatingCashFlow,
      capitalExpenditures,
      freeCashFlow,
      dividendsPaid,
      cashCoverage,
      retainedCash,
    },
    balance: {
      cash,
      grossDebt,
      netDebt,
      ebitdaProxy,
      netDebtToEbitda,
      currentRatio,
      interestCoverage,
    },
    trend,
    latestAnnual,
    latestBalance,
    referencePrice,
    warnings,
  };
}
