import React from 'react';
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import { ExternalLink, FileText, Loader2, Users } from 'lucide-react';
import { xbrlApi, type Form13DG, type Form4, type Form4Transaction, type MarketPriceHistory } from '../api';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/ui/button';
import {
  getFilingUrlCandidates,
  type FilingCandidate,
  type FilingSnapshot,
  type MetricCell,
  type MetricSection,
} from '../utils/fundamentals';

export function formatDate(value?: string | null): string {
  if (!value) {
    return '-';
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

function formatFilingPeriod(filing: FilingSnapshot | null): string {
  const periodEnd = filing?.analysis.secMetadata.documentPeriodEndDate;
  if (periodEnd) {
    return formatDate(periodEnd);
  }
  return filing?.filing.reportDate ? formatDate(filing.filing.reportDate) : '-';
}

function formatFilingLabel(filing: FilingCandidate): string {
  return `${filing.formType} | ${formatDate(filing.filingDate)}`;
}

function metricToneClass(tone: MetricCell['tone']) {
  if (tone === 'positive') {
    return 'text-emerald-600';
  }
  if (tone === 'negative') {
    return 'text-rose-600';
  }
  return 'text-slate-950';
}

export function formatCurrencyValue(value: number, maximumFractionDigits: number = 2): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits,
    minimumFractionDigits: maximumFractionDigits,
  }).format(value);
}

function formatSignedCurrencyValue(value: number, maximumFractionDigits: number = 2): string {
  const sign = value > 0 ? '+' : value < 0 ? '-' : '';
  return `${sign}${formatCurrencyValue(Math.abs(value), maximumFractionDigits)}`;
}

export function formatPercentValue(value: number, maximumFractionDigits: number = 2): string {
  return `${(value * 100).toFixed(maximumFractionDigits)}%`;
}

export function formatMultipleValue(value: number, maximumFractionDigits: number = 2): string {
  return `${value.toFixed(maximumFractionDigits)}x`;
}

export function findHistoricalClose(
  history: MarketPriceHistory | null,
  targetDate?: string | null,
): number | null {
  if (!history || !targetDate) {
    return null;
  }

  const targetTimestamp = Date.parse(targetDate);
  if (Number.isNaN(targetTimestamp)) {
    return null;
  }

  let previousClose: number | null = null;

  for (const price of history.prices) {
    const timestamp = Date.parse(price.date);
    if (Number.isNaN(timestamp)) {
      continue;
    }

    if (timestamp <= targetTimestamp) {
      previousClose = price.close;
      continue;
    }

    return previousClose ?? price.close;
  }

  return previousClose;
}

export async function loadFilingAnalysis(
  filing: FilingCandidate,
  kind: FilingSnapshot['kind'],
): Promise<FilingSnapshot> {
  const candidates = getFilingUrlCandidates(filing);
  let lastError: unknown = null;

  for (const filingUrl of candidates) {
    try {
      const analysis = await xbrlApi.getComprehensiveAnalysis(filingUrl);
      return {
        kind,
        filing: {
          ...filing,
          filingUrl,
        },
        analysis,
      };
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Failed to analyze filing');
}

export function MetricBoard({
  section,
  action,
}: {
  section: MetricSection;
  action?: React.ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
      <div className="bg-[linear-gradient(135deg,#0f172a_0%,#1e293b_55%,#1d4ed8_100%)] px-6 py-5 text-white">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.35em] text-blue-100/80">
              {section.title}
            </p>
            <p className="mt-2 max-w-3xl text-sm text-slate-200">{section.caption}</p>
          </div>
          {action}
        </div>
      </div>
      <div className="grid grid-cols-2 divide-x divide-y divide-slate-200 lg:grid-cols-4">
        {section.metrics.map((metric) => (
          <div key={`${section.id}-${metric.label}`} className="min-h-[108px] bg-white px-5 py-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">
              {metric.label}
            </p>
            <p className={`mt-3 text-xl font-semibold ${metricToneClass(metric.tone)}`}>
              {metric.display}
            </p>
            {metric.note && (
              <p className="mt-2 text-xs leading-5 text-slate-500">{metric.note}</p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}

export function FilingPicker({
  title,
  icon: Icon,
  filings,
  selectedId,
  onSelect,
  onOpenFiling,
  selectedSnapshot,
  loading,
  emptyAction,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  filings: FilingCandidate[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onOpenFiling: (id: string) => void;
  selectedSnapshot: FilingSnapshot | null;
  loading: boolean;
  emptyAction?: {
    label: string;
    hint?: string;
    loading?: boolean;
    disabled?: boolean;
    onClick: () => void;
  };
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-slate-900">
            <Icon className="h-5 w-5 text-blue-600" />
            <h2 className="text-lg font-semibold">{title}</h2>
          </div>
          <p className="mt-1 text-sm text-slate-600">
            Pick the statement source used for this side of the ratio board.
          </p>
        </div>
        {loading && <LoadingSpinner size="sm" />}
      </div>

      {filings.length === 0 ? (
        <div className="mt-5 rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-600">
          <p>No XBRL filing candidates were found in the recent company history.</p>
          {emptyAction && (
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <Button
                size="sm"
                onClick={emptyAction.onClick}
                disabled={emptyAction.disabled}
              >
                {emptyAction.loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileText className="h-4 w-4" />}
                {emptyAction.label}
              </Button>
              {emptyAction.hint && (
                <p className="text-xs text-slate-500">{emptyAction.hint}</p>
              )}
            </div>
          )}
        </div>
      ) : (
        <>
          <div className="mt-5 flex flex-wrap gap-2">
            {filings.slice(0, 6).map((filing) => {
              const active = filing.id === selectedId;
              return (
                <button
                  key={filing.id}
                  type="button"
                  onClick={() => onSelect(filing.id)}
                  className={`rounded-full border px-3 py-2 text-sm transition-colors ${
                    active
                      ? 'border-slate-950 bg-slate-950 text-white'
                      : 'border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-400 hover:bg-slate-100'
                  }`}
                >
                  {formatFilingLabel(filing)}
                </button>
              );
            })}
          </div>

          {selectedSnapshot && (
            <div className="mt-5 grid gap-3 rounded-2xl bg-slate-50 p-4 md:grid-cols-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">Filed</p>
                <p className="mt-2 text-sm font-medium text-slate-900">
                  {formatDate(selectedSnapshot.filing.filingDate)}
                </p>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">Period End</p>
                <p className="mt-2 text-sm font-medium text-slate-900">
                  {formatFilingPeriod(selectedSnapshot)}
                </p>
              </div>
              <div className="flex items-end justify-start md:justify-end">
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => window.open(selectedSnapshot.filing.filingUrl, '_blank', 'noopener,noreferrer')}
                  >
                    <ExternalLink className="h-4 w-4" />
                    SEC Document
                  </Button>
                  {selectedSnapshot.filing.id && (
                    <Button
                      size="sm"
                      onClick={() => onOpenFiling(selectedSnapshot.filing.id)}
                    >
                      <FileText className="h-4 w-4" />
                      Filing Detail
                    </Button>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}

export function PriceTrendCard({
  history,
  ticker,
}: {
  history: MarketPriceHistory | null;
  ticker: string;
}) {
  const data = React.useMemo(
    () =>
      history?.prices.slice(-90).map((price) => ({
        date: price.date,
        dateLabel: formatDate(price.date),
        close: price.close,
      })) ?? [],
    [history],
  );

  if (!history || data.length === 0) {
    return (
      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Price Trend</h2>
        <p className="mt-2 text-sm text-slate-600">No Tiingo price bars were returned for this ticker.</p>
      </section>
    );
  }

  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Price Trend</h2>
          <p className="mt-1 text-sm text-slate-600">
            Last 90 daily closes for {ticker}. Provider: {history.provider}
          </p>
        </div>
        <div className="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-blue-700">
          Daily
        </div>
      </div>

      <div className="mt-5 h-48 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data}>
            <defs>
              <linearGradient id="fundamentalsPriceFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#2563eb" stopOpacity={0.35} />
                <stop offset="100%" stopColor="#2563eb" stopOpacity={0.03} />
              </linearGradient>
            </defs>
            <XAxis dataKey="dateLabel" hide />
            <Tooltip
              formatter={(value: number) => [`$${value.toFixed(2)}`, 'Close']}
              labelFormatter={(_, payload) => payload?.[0]?.payload?.dateLabel ?? '-'}
            />
            <Area
              type="monotone"
              dataKey="close"
              stroke="#1d4ed8"
              strokeWidth={2.5}
              fill="url(#fundamentalsPriceFill)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

interface TrendCardPoint {
  filingId: string;
  periodLabel: string;
  filedLabel: string;
  value: number;
  valuationDisplay?: string | null;
  yieldDisplay?: string | null;
}

export function FundamentalTrendCard({
  title,
  subtitle,
  badge,
  data,
  loading,
  valueLabel,
  valueFormatter,
  tooltipMetricLabel,
  tooltipMetricResolver,
  emptyMessage,
  color,
  gradientId,
}: {
  title: string;
  subtitle: string;
  badge: string;
  data: TrendCardPoint[];
  loading: boolean;
  valueLabel: string;
  valueFormatter: (value: number) => string;
  tooltipMetricLabel?: string;
  tooltipMetricResolver?: (point: TrendCardPoint) => string | null | undefined;
  emptyMessage: string;
  color: string;
  gradientId: string;
}) {
  if (loading && data.length === 0) {
    return (
      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
            <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
          </div>
          <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-slate-600">
            {badge}
          </div>
        </div>
        <div className="mt-8">
          <LoadingSpinner size="sm" text="Loading annual trend..." />
        </div>
      </section>
    );
  }

  if (data.length === 0) {
    return (
      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
            <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
          </div>
          <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-slate-600">
            {badge}
          </div>
        </div>
        <p className="mt-5 text-sm text-slate-600">{emptyMessage}</p>
      </section>
    );
  }

  const latest = data.at(-1) ?? null;
  const earliest = data[0] ?? null;
  const delta = latest && earliest ? latest.value - earliest.value : null;
  const deltaTone = delta != null && delta > 0
    ? 'text-emerald-600'
    : delta != null && delta < 0
      ? 'text-rose-600'
      : 'text-slate-500';

  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
        </div>
        <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-slate-600">
          {badge}
        </div>
      </div>

      {latest && (
        <div className="mt-5 flex items-end justify-between gap-4">
          <div>
            <p className="text-3xl font-semibold tracking-tight text-slate-950">
              {valueFormatter(latest.value)}
            </p>
            <p className={`mt-2 text-sm ${deltaTone}`}>
              {delta != null && earliest && latest.filingId !== earliest.filingId
                ? `${formatSignedCurrencyValue(delta)} vs ${earliest.periodLabel}`
                : `Filed ${latest.filedLabel}`}
            </p>
          </div>
          <div className="text-right text-xs uppercase tracking-[0.24em] text-slate-500">
            <p>{data.length} Filings</p>
            <p className="mt-2">{latest.periodLabel}</p>
          </div>
        </div>
      )}

      <div className="mt-5 h-40 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data}>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={color} stopOpacity={0.32} />
                <stop offset="100%" stopColor={color} stopOpacity={0.04} />
              </linearGradient>
            </defs>
            <XAxis dataKey="periodLabel" hide />
            <Tooltip
              content={({ active, payload }) => {
                const point = payload?.[0]?.payload as TrendCardPoint | undefined;
                if (!active || !point) {
                  return null;
                }

                const secondaryMetric = tooltipMetricResolver?.(point);

                return (
                  <div className="rounded-md border border-slate-200 bg-white px-4 py-3 text-sm shadow-lg">
                    <p className="font-medium text-slate-950">{point.periodLabel}</p>
                    <p className="mt-2 text-slate-700">
                      {valueLabel}: <span className="font-semibold" style={{ color }}>{valueFormatter(point.value)}</span>
                    </p>
                    {tooltipMetricLabel && secondaryMetric && (
                      <p className="mt-1 text-slate-700">
                        {tooltipMetricLabel}: <span className="font-semibold text-slate-950">{secondaryMetric}</span>
                      </p>
                    )}
                  </div>
                );
              }}
            />
            <Area
              type="monotone"
              dataKey="value"
              stroke={color}
              strokeWidth={2.5}
              fill={`url(#${gradientId})`}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

function formatCompactCurrencyValue(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: Math.abs(value) >= 1000 ? 'compact' : 'standard',
    maximumFractionDigits: 2,
  }).format(value);
}

function formatCompactNumberValue(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    notation: Math.abs(value) >= 1000 ? 'compact' : 'standard',
    maximumFractionDigits: 2,
  }).format(value);
}

function getPrimaryForm4Transaction(filing: Form4): Form4Transaction | null {
  return filing.transactions?.[0] ?? null;
}

export function getForm4TradeDirection(filing: Form4): 'buy' | 'sell' | null {
  const code = getPrimaryForm4Transaction(filing)?.acquiredDisposedCode ?? filing.acquiredDisposedCode;
  if (code === 'A') {
    return 'buy';
  }
  if (code === 'D') {
    return 'sell';
  }
  return null;
}

export function getForm4TradeDate(filing: Form4): string | null {
  return getPrimaryForm4Transaction(filing)?.transactionDate
    ?? filing.transactionDate
    ?? filing.periodOfReport
    ?? null;
}

function getForm4TradeValue(filing: Form4): number | null {
  const transaction = getPrimaryForm4Transaction(filing);
  const shares = transaction?.transactionShares ?? filing.transactionShares ?? null;
  const price = transaction?.transactionPricePerShare ?? filing.transactionPricePerShare ?? null;
  return transaction?.transactionValue
    ?? filing.transactionValue
    ?? (shares != null && price != null ? Math.abs(shares) * price : null);
}

function getForm4TradeShares(filing: Form4): number | null {
  const shares = getPrimaryForm4Transaction(filing)?.transactionShares ?? filing.transactionShares ?? null;
  return shares != null ? Math.abs(shares) : null;
}

function getForm4Relationship(filing: Form4): string {
  if (filing.officerTitle) {
    return filing.officerTitle;
  }
  if (filing.isDirector) {
    return 'Director';
  }
  if (filing.isOfficer) {
    return 'Officer';
  }
  if (filing.isTenPercentOwner) {
    return '10% Owner';
  }
  if (filing.isOther) {
    return 'Other Insider';
  }
  return filing.ownerType ?? 'Insider';
}

export function getSortableTimestamp(value?: string | null): number {
  if (!value) {
    return 0;
  }

  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? 0 : timestamp;
}

function Form4TradeTile({
  title,
  filing,
  direction,
  error,
}: {
  title: string;
  filing: Form4 | null;
  direction: 'buy' | 'sell';
  error?: string | null;
}) {
  const toneClasses = direction === 'buy'
    ? 'border-emerald-200 bg-emerald-50/70 text-emerald-700'
    : 'border-rose-200 bg-rose-50/70 text-rose-700';

  if (error) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-4">
        <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">{title}</p>
        <p className="mt-3 text-sm text-slate-600">{error}</p>
      </div>
    );
  }

  if (!filing) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-4">
        <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">{title}</p>
        <p className="mt-3 text-sm text-slate-600">No recent {direction} transaction was found for this symbol.</p>
      </div>
    );
  }

  return (
    <div className={`rounded-2xl border p-4 ${toneClasses}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.24em]">{title}</p>
          <p className="mt-2 text-lg font-semibold text-slate-950">{filing.rptOwnerName || 'Unknown Insider'}</p>
          <p className="mt-1 text-sm text-slate-600">{getForm4Relationship(filing)}</p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-700">
          Form 4
        </span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Date</p>
          <p className="mt-2 font-medium text-slate-900">{formatDate(getForm4TradeDate(filing))}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Value</p>
          <p className="mt-2 font-medium text-slate-900">{formatCompactCurrencyValue(getForm4TradeValue(filing))}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Shares</p>
          <p className="mt-2 font-medium text-slate-900">{formatCompactNumberValue(getForm4TradeShares(filing))}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-[0.22em] text-slate-500">Code</p>
          <p className="mt-2 font-medium text-slate-900">
            {getPrimaryForm4Transaction(filing)?.transactionCode ?? filing.acquiredDisposedCode ?? '-'}
          </p>
        </div>
      </div>
    </div>
  );
}

function OwnershipRow({
  filing,
}: {
  filing: Form13DG;
}) {
  const is13D = filing.scheduleType === '13D' || filing.scheduleType === 'SCHEDULE 13D';

  return (
    <div className="flex items-start justify-between gap-4 rounded-2xl border border-slate-200 bg-white px-4 py-3">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-medium text-slate-950">{filing.filingPersonName}</p>
          <span className={`rounded-full px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.22em] ${
            is13D ? 'bg-orange-100 text-orange-800' : 'bg-emerald-100 text-emerald-800'
          }`}>
            {filing.scheduleType}
          </span>
        </div>
        <p className="mt-1 text-sm text-slate-600">
          {formatDate(filing.eventDate)} event | filed {formatDate(filing.filedDate)}
        </p>
      </div>
      <div className="text-right">
        <p className="text-sm font-semibold text-slate-950">
          {filing.percentOfClass != null ? `${filing.percentOfClass.toFixed(2)}%` : '-'}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          {formatCompactNumberValue(filing.sharesBeneficiallyOwned)}
        </p>
      </div>
    </div>
  );
}

export function InsiderActivityCard({
  ticker,
  latestBuy,
  latestSell,
  ownershipFilings,
  loading,
  form4Error,
  ownershipError,
}: {
  ticker: string;
  latestBuy: Form4 | null;
  latestSell: Form4 | null;
  ownershipFilings: Form13DG[];
  loading: boolean;
  form4Error: string | null;
  ownershipError: string | null;
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-slate-900">
            <Users className="h-5 w-5 text-blue-600" />
            <h2 className="text-lg font-semibold">Latest Insider Activity</h2>
          </div>
          <p className="mt-1 text-sm text-slate-600">
            Most recent Form 4 insider trades and 13D/G ownership filings for {ticker}.
          </p>
        </div>
        <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-slate-600">
          Form 4 + 13D/G
        </div>
      </div>

      {loading ? (
        <div className="mt-8">
          <LoadingSpinner size="sm" text="Loading insider activity..." />
        </div>
      ) : (
        <div className="mt-5 space-y-5">
          <div className="grid gap-4 md:grid-cols-2">
            <Form4TradeTile
              title="Latest Buy"
              filing={latestBuy}
              direction="buy"
              error={form4Error}
            />
            <Form4TradeTile
              title="Latest Sell"
              filing={latestSell}
              direction="sell"
              error={form4Error}
            />
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">Latest 13D/G</p>
                <p className="mt-1 text-sm text-slate-600">
                  Recent beneficial ownership filings for this issuer CIK.
                </p>
              </div>
              <span className="rounded-full bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-700">
                Ownership
              </span>
            </div>

            {ownershipError ? (
              <p className="mt-4 text-sm text-slate-600">{ownershipError}</p>
            ) : ownershipFilings.length === 0 ? (
              <p className="mt-4 text-sm text-slate-600">No recent 13D/G filings were found for this issuer.</p>
            ) : (
              <div className="mt-4 space-y-3">
                {ownershipFilings.slice(0, 3).map((filing) => (
                  <OwnershipRow key={filing.id} filing={filing} />
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}


