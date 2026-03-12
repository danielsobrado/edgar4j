import React from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { subYears, format } from 'date-fns';
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import {
  ArrowLeft,
  Building2,
  CalendarDays,
  ExternalLink,
  FileText,
  Landmark,
  Loader2,
  TrendingDown,
  TrendingUp,
  Users,
} from 'lucide-react';
import {
  downloadsApi,
  filingsApi,
  form13dgApi,
  form4Api,
  marketDataApi,
  xbrlApi,
  type Form13DG,
  type Form4,
  type Form4Transaction,
  type MarketPriceHistory,
} from '../api';
import { useCompanyByCik } from '../hooks';
import { ErrorMessage, ErrorPage } from '../components/common/ErrorMessage';
import { LoadingPage, LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/ui/button';
import { showError, showInfo, showSuccess } from '../store/notificationStore';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '../components/ui/breadcrumb';
import {
  buildAnnualTrendPoints,
  buildFundamentalSections,
  buildPriceSnapshot,
  getFilingUrlCandidates,
  splitFundamentalFilings,
  ANNUAL_FORM_TYPES,
  QUARTERLY_FORM_TYPES,
  type FilingCandidate,
  type FilingSnapshot,
  type MetricCell,
  type MetricSection,
} from '../utils/fundamentals';

const ANNUAL_TREND_FILING_LIMIT = 5;

function formatDate(value?: string | null): string {
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

function formatCurrencyValue(value: number, maximumFractionDigits: number = 2): string {
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

function formatPercentValue(value: number, maximumFractionDigits: number = 2): string {
  return `${(value * 100).toFixed(maximumFractionDigits)}%`;
}

function formatMultipleValue(value: number, maximumFractionDigits: number = 2): string {
  return `${value.toFixed(maximumFractionDigits)}x`;
}

function findHistoricalClose(
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

async function loadFilingAnalysis(
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

function MetricBoard({
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

function FilingPicker({
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

function PriceTrendCard({
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

function FundamentalTrendCard({
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

function getForm4TradeDirection(filing: Form4): 'buy' | 'sell' | null {
  const code = getPrimaryForm4Transaction(filing)?.acquiredDisposedCode ?? filing.acquiredDisposedCode;
  if (code === 'A') {
    return 'buy';
  }
  if (code === 'D') {
    return 'sell';
  }
  return null;
}

function getForm4TradeDate(filing: Form4): string | null {
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

function getSortableTimestamp(value?: string | null): number {
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
          {formatDate(filing.eventDate)} event • filed {formatDate(filing.filedDate)}
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

function InsiderActivityCard({
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

export function CompanyFundamentals() {
  const { cik } = useParams<{ cik: string }>();
  const navigate = useNavigate();
  const { company, loading: companyLoading, error: companyError } = useCompanyByCik(cik);

  const [filingsLoading, setFilingsLoading] = React.useState(true);
  const [filingsError, setFilingsError] = React.useState<string | null>(null);
  const [annualCandidates, setAnnualCandidates] = React.useState<FilingCandidate[]>([]);
  const [quarterlyCandidates, setQuarterlyCandidates] = React.useState<FilingCandidate[]>([]);
  const [selectedAnnualId, setSelectedAnnualId] = React.useState<string | null>(null);
  const [selectedQuarterlyId, setSelectedQuarterlyId] = React.useState<string | null>(null);

  const [annualSnapshot, setAnnualSnapshot] = React.useState<FilingSnapshot | null>(null);
  const [quarterlySnapshot, setQuarterlySnapshot] = React.useState<FilingSnapshot | null>(null);
  const [annualLoading, setAnnualLoading] = React.useState(false);
  const [quarterlyLoading, setQuarterlyLoading] = React.useState(false);
  const [annualError, setAnnualError] = React.useState<string | null>(null);
  const [quarterlyError, setQuarterlyError] = React.useState<string | null>(null);
  const [annualTrendSnapshots, setAnnualTrendSnapshots] = React.useState<FilingSnapshot[]>([]);
  const [annualTrendLoading, setAnnualTrendLoading] = React.useState(false);
  const [annualTrendError, setAnnualTrendError] = React.useState<string | null>(null);
  const [recentForm4Filings, setRecentForm4Filings] = React.useState<Form4[]>([]);
  const [recentOwnershipFilings, setRecentOwnershipFilings] = React.useState<Form13DG[]>([]);
  const [insiderActivityLoading, setInsiderActivityLoading] = React.useState(false);
  const [form4ActivityError, setForm4ActivityError] = React.useState<string | null>(null);
  const [ownershipActivityError, setOwnershipActivityError] = React.useState<string | null>(null);
  const [metadataSyncLoading, setMetadataSyncLoading] = React.useState(false);
  const [metadataSyncJobId, setMetadataSyncJobId] = React.useState<string | null>(null);
  const [metadataSyncError, setMetadataSyncError] = React.useState<string | null>(null);

  const [priceHistory, setPriceHistory] = React.useState<MarketPriceHistory | null>(null);
  const [priceLoading, setPriceLoading] = React.useState(false);
  const [priceError, setPriceError] = React.useState<string | null>(null);
  const annualTrendCacheRef = React.useRef(new Map<string, FilingSnapshot>());
  const metadataSyncTriggerRef = React.useRef<'auto' | 'manual'>('auto');
  const autoSyncCikRef = React.useRef<string | null>(null);

  const loadFundamentalFilings = React.useCallback(async () => {
    if (!cik) {
      setFilingsLoading(false);
      setFilingsError('A company CIK is required.');
      return { annual: [], quarterly: [] };
    }

    setFilingsLoading(true);
    setFilingsError(null);

    try {
      const [annualResponse, quarterlyResponse] = await Promise.all([
        filingsApi.searchFilings({
          cik,
          formTypes: [...ANNUAL_FORM_TYPES],
          page: 0,
          size: 12,
          sortBy: 'fillingDate',
          sortDir: 'desc',
        }),
        filingsApi.searchFilings({
          cik,
          formTypes: [...QUARTERLY_FORM_TYPES],
          page: 0,
          size: 12,
          sortBy: 'fillingDate',
          sortDir: 'desc',
        }),
      ]);
      const split = splitFundamentalFilings([
        ...(annualResponse.content ?? []),
        ...(quarterlyResponse.content ?? []),
      ]);
      setAnnualCandidates(split.annual);
      setQuarterlyCandidates(split.quarterly);
      return split;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load company filings';
      setFilingsError(message);
      setAnnualCandidates([]);
      setQuarterlyCandidates([]);
      return { annual: [], quarterly: [] };
    } finally {
      setFilingsLoading(false);
    }
  }, [cik]);

  const refreshLatestMetadata = React.useCallback(async (mode: 'auto' | 'manual' = 'auto') => {
    if (!cik || metadataSyncLoading || metadataSyncJobId) {
      return;
    }

    metadataSyncTriggerRef.current = mode;
    setMetadataSyncLoading(true);
    setMetadataSyncError(null);

    try {
      let activeSubmissionJob = null;

      try {
        const activeJobs = await downloadsApi.getActiveJobs();
        activeSubmissionJob = activeJobs.find(
          (job) => job.type === 'SUBMISSIONS' && job.cik === cik,
        ) ?? null;
      } catch {
        // Fall through to starting a fresh sync if active jobs cannot be resolved.
      }

      if (activeSubmissionJob) {
        setMetadataSyncJobId(activeSubmissionJob.id);
        if (mode === 'manual') {
          showInfo('Refresh already running', `Latest submissions sync for CIK ${cik} is already in progress.`);
        }
        return;
      }

      const job = await downloadsApi.downloadSubmissions(cik);
      setMetadataSyncJobId(job.id);
      if (mode === 'manual') {
        showSuccess('Refresh started', `Latest submissions sync for CIK ${cik} has been queued.`);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to start submissions sync.';
      setMetadataSyncError(message);
      if (mode === 'manual') {
        showError('Refresh failed', message);
      }
    } finally {
      setMetadataSyncLoading(false);
    }
  }, [cik, metadataSyncJobId, metadataSyncLoading]);

  React.useEffect(() => {
    if (!cik) {
      setFilingsLoading(false);
      setFilingsError('A company CIK is required.');
      return;
    }

    setAnnualCandidates([]);
    setQuarterlyCandidates([]);
    setSelectedAnnualId(null);
    setSelectedQuarterlyId(null);
    setAnnualSnapshot(null);
    setQuarterlySnapshot(null);
    setAnnualTrendSnapshots([]);
    setRecentForm4Filings([]);
    setRecentOwnershipFilings([]);
    setAnnualError(null);
    setQuarterlyError(null);
    setAnnualTrendError(null);
    setAnnualTrendLoading(false);
    setInsiderActivityLoading(false);
    setForm4ActivityError(null);
    setOwnershipActivityError(null);
    setMetadataSyncJobId(null);
    setMetadataSyncLoading(false);
    setMetadataSyncError(null);
    annualTrendCacheRef.current = new Map();
    autoSyncCikRef.current = null;

    const run = async () => {
      await loadFundamentalFilings();
    };

    void run();
  }, [cik, loadFundamentalFilings]);

  React.useEffect(() => {
    if (!cik || autoSyncCikRef.current === cik) {
      return;
    }

    autoSyncCikRef.current = cik;
    void refreshLatestMetadata('auto');
  }, [cik, refreshLatestMetadata]);

  React.useEffect(() => {
    if (!annualCandidates.some((filing) => filing.id === selectedAnnualId)) {
      setSelectedAnnualId(annualCandidates[0]?.id ?? null);
    }
  }, [annualCandidates, selectedAnnualId]);

  React.useEffect(() => {
    if (!quarterlyCandidates.some((filing) => filing.id === selectedQuarterlyId)) {
      setSelectedQuarterlyId(quarterlyCandidates[0]?.id ?? null);
    }
  }, [quarterlyCandidates, selectedQuarterlyId]);

  const selectedAnnual = React.useMemo(
    () => annualCandidates.find((filing) => filing.id === selectedAnnualId) ?? null,
    [annualCandidates, selectedAnnualId],
  );
  const selectedQuarterly = React.useMemo(
    () => quarterlyCandidates.find((filing) => filing.id === selectedQuarterlyId) ?? null,
    [quarterlyCandidates, selectedQuarterlyId],
  );

  React.useEffect(() => {
    if (!selectedAnnual) {
      setAnnualSnapshot(null);
      setAnnualError(null);
      return;
    }

    let cancelled = false;

    const loadAnnual = async () => {
      setAnnualLoading(true);
      setAnnualError(null);
      setAnnualSnapshot(null);
      try {
        const snapshot = await loadFilingAnalysis(selectedAnnual, 'annual');
        if (!cancelled) {
          annualTrendCacheRef.current.set(snapshot.filing.id, snapshot);
          setAnnualSnapshot(snapshot);
        }
      } catch (error) {
        if (!cancelled) {
          setAnnualSnapshot(null);
          setAnnualError(error instanceof Error ? error.message : 'Failed to analyze annual filing');
        }
      } finally {
        if (!cancelled) {
          setAnnualLoading(false);
        }
      }
    };

    void loadAnnual();

    return () => {
      cancelled = true;
    };
  }, [selectedAnnual]);

  React.useEffect(() => {
    if (annualCandidates.length === 0) {
      setAnnualTrendSnapshots([]);
      setAnnualTrendError(null);
      setAnnualTrendLoading(false);
      return;
    }

    const targetFilings = annualCandidates.slice(0, ANNUAL_TREND_FILING_LIMIT);
    let cancelled = false;

    const loadAnnualTrendSnapshots = async () => {
      setAnnualTrendLoading(true);
      setAnnualTrendError(null);

      const missingFilings = targetFilings.filter(
        (filing) => !annualTrendCacheRef.current.has(filing.id),
      );

      if (missingFilings.length > 0) {
        const results = await Promise.allSettled(
          missingFilings.map((filing) => loadFilingAnalysis(filing, 'annual')),
        );

        if (cancelled) {
          return;
        }

        results.forEach((result, index) => {
          if (result.status === 'fulfilled') {
            annualTrendCacheRef.current.set(missingFilings[index].id, result.value);
          }
        });
      }

      const snapshots = targetFilings
        .map((filing) => annualTrendCacheRef.current.get(filing.id) ?? null)
        .filter((snapshot): snapshot is FilingSnapshot => Boolean(snapshot));

      if (!cancelled) {
        setAnnualTrendSnapshots(snapshots);
        setAnnualTrendError(
          snapshots.length === 0
            ? 'Recent annual filings could not be analyzed for trend history.'
            : null,
        );
        setAnnualTrendLoading(false);
      }
    };

    void loadAnnualTrendSnapshots();

    return () => {
      cancelled = true;
    };
  }, [annualCandidates]);

  React.useEffect(() => {
    if (!selectedQuarterly) {
      setQuarterlySnapshot(null);
      setQuarterlyError(null);
      return;
    }

    let cancelled = false;

    const loadQuarterly = async () => {
      setQuarterlyLoading(true);
      setQuarterlyError(null);
      setQuarterlySnapshot(null);
      try {
        const snapshot = await loadFilingAnalysis(selectedQuarterly, 'quarterly');
        if (!cancelled) {
          setQuarterlySnapshot(snapshot);
        }
      } catch (error) {
        if (!cancelled) {
          setQuarterlySnapshot(null);
          setQuarterlyError(error instanceof Error ? error.message : 'Failed to analyze quarterly filing');
        }
      } finally {
        if (!cancelled) {
          setQuarterlyLoading(false);
        }
      }
    };

    void loadQuarterly();

    return () => {
      cancelled = true;
    };
  }, [selectedQuarterly]);

  React.useEffect(() => {
    if (!company?.ticker) {
      setPriceHistory(null);
      setPriceError(null);
      return;
    }

    let cancelled = false;
    const startDate = format(subYears(new Date(), ANNUAL_TREND_FILING_LIMIT + 1), 'yyyy-MM-dd');
    const endDate = format(new Date(), 'yyyy-MM-dd');

    const loadPrices = async () => {
      setPriceLoading(true);
      setPriceError(null);
      setPriceHistory(null);
      try {
        const history = await marketDataApi.getPriceHistory(company.ticker, startDate, endDate);
        if (!cancelled) {
          setPriceHistory(history);
        }
      } catch (error) {
        if (!cancelled) {
          setPriceHistory(null);
          setPriceError(error instanceof Error ? error.message : 'Failed to load market data');
        }
      } finally {
        if (!cancelled) {
          setPriceLoading(false);
        }
      }
    };

    void loadPrices();

    return () => {
      cancelled = true;
    };
  }, [company?.ticker]);

  React.useEffect(() => {
    if (!company?.cik) {
      setRecentForm4Filings([]);
      setRecentOwnershipFilings([]);
      setForm4ActivityError(null);
      setOwnershipActivityError(null);
      setInsiderActivityLoading(false);
      return;
    }

    let cancelled = false;

    const loadInsiderActivity = async () => {
      setInsiderActivityLoading(true);
      setForm4ActivityError(null);
      setOwnershipActivityError(null);

      const [form4Result, ownershipResult] = await Promise.allSettled([
        company.ticker
          ? form4Api.getBySymbol(company.ticker.toUpperCase(), 0, 12)
          : Promise.resolve({ content: [] }),
        form13dgApi.getByIssuerCik(company.cik, 0, 6),
      ]);

      if (cancelled) {
        return;
      }

      if (form4Result.status === 'fulfilled') {
        const sortedForm4 = [...(form4Result.value.content ?? [])].sort(
          (left, right) => getSortableTimestamp(getForm4TradeDate(right)) - getSortableTimestamp(getForm4TradeDate(left)),
        );
        setRecentForm4Filings(sortedForm4);
      } else {
        setRecentForm4Filings([]);
        setForm4ActivityError('Form 4 activity is unavailable right now.');
      }

      if (ownershipResult.status === 'fulfilled') {
        const sortedOwnership = [...(ownershipResult.value.content ?? [])].sort(
          (left, right) =>
            getSortableTimestamp(right.eventDate || right.filedDate)
            - getSortableTimestamp(left.eventDate || left.filedDate),
        );
        setRecentOwnershipFilings(sortedOwnership);
      } else {
        setRecentOwnershipFilings([]);
        setOwnershipActivityError('13D/G activity is unavailable right now.');
      }

      setInsiderActivityLoading(false);
    };

    void loadInsiderActivity();

    return () => {
      cancelled = true;
    };
  }, [company?.cik, company?.ticker]);

  React.useEffect(() => {
    if (!metadataSyncJobId) {
      return;
    }

    let cancelled = false;

    const pollJob = async () => {
      try {
        const job = await downloadsApi.getJobById(metadataSyncJobId);
        if (cancelled) {
          return;
        }

        if (job.status === 'COMPLETED') {
          setMetadataSyncJobId(null);
          setMetadataSyncError(null);
          const split = await loadFundamentalFilings();
          if (cancelled) {
            return;
          }
          if (metadataSyncTriggerRef.current === 'manual') {
            if (split.annual.length > 0 || split.quarterly.length > 0) {
              showSuccess('Metadata refreshed', 'Latest company submissions were downloaded and cached.');
            } else {
              showInfo('Refresh completed', 'Latest company submissions were downloaded, but no XBRL filing candidates are available yet.');
            }
          }
          metadataSyncTriggerRef.current = 'auto';
          return;
        }

        if (job.status === 'FAILED') {
          const message = job.error || 'Latest submissions sync failed.';
          setMetadataSyncJobId(null);
          setMetadataSyncError(message);
          if (metadataSyncTriggerRef.current === 'manual') {
            showError('Refresh failed', message);
          }
          metadataSyncTriggerRef.current = 'auto';
          return;
        }

        if (job.status === 'CANCELLED') {
          setMetadataSyncJobId(null);
          if (metadataSyncTriggerRef.current === 'manual') {
            showInfo('Refresh cancelled', 'Latest submissions sync was cancelled.');
          }
          metadataSyncTriggerRef.current = 'auto';
        }
      } catch (error) {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : 'Failed to check job status.';
          setMetadataSyncJobId(null);
          setMetadataSyncError(message);
          if (metadataSyncTriggerRef.current === 'manual') {
            showError('Refresh status unavailable', message);
          }
          metadataSyncTriggerRef.current = 'auto';
        }
      }
    };

    void pollJob();
    const interval = setInterval(() => {
      void pollJob();
    }, 5000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [metadataSyncJobId, loadFundamentalFilings]);

  const priceSnapshot = React.useMemo(() => buildPriceSnapshot(priceHistory), [priceHistory]);
  const annualTrendPoints = React.useMemo(
    () => buildAnnualTrendPoints(annualTrendSnapshots),
    [annualTrendSnapshots],
  );
  const earningsTrendData = React.useMemo(
    () =>
      annualTrendPoints
        .filter((point) => point.earningsPerShare != null)
        .map((point) => ({
          filingId: point.filingId,
          periodLabel: formatDate(point.periodEnd ?? point.filingDate),
          filedLabel: formatDate(point.filingDate),
          value: point.earningsPerShare as number,
          valuationDisplay: (() => {
            const referenceClose = findHistoricalClose(priceHistory, point.periodEnd ?? point.filingDate);
            if (referenceClose == null || point.earningsPerShare == null || point.earningsPerShare <= 0) {
              return null;
            }
            return formatMultipleValue(referenceClose / point.earningsPerShare);
          })(),
        })),
    [annualTrendPoints, priceHistory],
  );
  const dividendTrendData = React.useMemo(
    () =>
      annualTrendPoints
        .filter((point) => point.dividendsPerShare != null)
        .map((point) => ({
          filingId: point.filingId,
          periodLabel: formatDate(point.periodEnd ?? point.filingDate),
          filedLabel: formatDate(point.filingDate),
          value: point.dividendsPerShare as number,
          yieldDisplay: (() => {
            const referenceClose = findHistoricalClose(priceHistory, point.periodEnd ?? point.filingDate);
            if (referenceClose == null || point.dividendsPerShare == null || referenceClose <= 0) {
              return null;
            }
            return formatPercentValue(point.dividendsPerShare / referenceClose);
          })(),
        })),
    [annualTrendPoints, priceHistory],
  );
  const sections = React.useMemo(
    () => buildFundamentalSections(annualSnapshot, quarterlySnapshot, priceHistory),
    [annualSnapshot, quarterlySnapshot, priceHistory],
  );
  const latestBuy = React.useMemo(
    () => recentForm4Filings.find((filing) => getForm4TradeDirection(filing) === 'buy') ?? null,
    [recentForm4Filings],
  );
  const latestSell = React.useMemo(
    () => recentForm4Filings.find((filing) => getForm4TradeDirection(filing) === 'sell') ?? null,
    [recentForm4Filings],
  );
  const metadataSyncActive = metadataSyncLoading || metadataSyncJobId !== null;

  if (!cik) {
    return (
      <ErrorPage
        title="Missing company"
        message="A valid company CIK is required to open the fundamentals page."
      />
    );
  }

  if (companyLoading && !company) {
    return <LoadingPage text="Loading company fundamentals..." />;
  }

  if (companyError) {
    return (
      <ErrorPage
        title="Failed to load company"
        message={companyError}
        onRetry={() => window.location.reload()}
      />
    );
  }

  if (!company) {
    return (
      <ErrorPage
        title="Company not found"
        message="The requested company could not be resolved from the local SEC data."
      />
    );
  }

  const showFatalDataError = !filingsLoading && filingsError && annualCandidates.length === 0 && quarterlyCandidates.length === 0;
  if (showFatalDataError) {
    return (
      <ErrorPage
        title="Fundamentals unavailable"
        message={filingsError}
        onRetry={() => window.location.reload()}
      />
    );
  }

  const priceTone = (priceSnapshot.change ?? 0) > 0 ? 'text-emerald-600' : (priceSnapshot.change ?? 0) < 0 ? 'text-rose-600' : 'text-slate-900';

  return (
    <div className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to="/companies">Companies</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Fundamentals</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <section className="overflow-hidden rounded-[32px] border border-slate-200 bg-[radial-gradient(circle_at_top_left,_rgba(29,78,216,0.18),_transparent_38%),linear-gradient(135deg,#f8fafc_0%,#ffffff_52%,#eff6ff_100%)] shadow-sm">
        <div className="grid gap-6 px-6 py-6 lg:grid-cols-[1.2fr_0.8fr] lg:px-8">
          <div className="space-y-5">
            <button
              type="button"
              onClick={() => navigate('/companies')}
              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white/80 px-3 py-2 text-sm text-slate-700 transition-colors hover:border-slate-400 hover:text-slate-950"
            >
              <ArrowLeft className="h-4 w-4" />
              Back to Companies
            </button>

            <div>
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-3xl font-semibold tracking-tight text-slate-950">{company.name}</h1>
                {company.ticker && (
                  <span className="rounded-full bg-blue-600/10 px-3 py-1 text-sm font-semibold text-blue-700">
                    {company.ticker}
                  </span>
                )}
              </div>
              <div className="mt-3 flex flex-wrap gap-2 text-sm text-slate-600">
                <span className="rounded-full bg-white px-3 py-1 shadow-sm">CIK {company.cik}</span>
                {company.stateOfIncorporation && (
                  <span className="rounded-full bg-white px-3 py-1 shadow-sm">
                    Incorporation {company.stateOfIncorporation}
                  </span>
                )}
                {company.exchanges?.[0] && (
                  <span className="rounded-full bg-white px-3 py-1 shadow-sm">{company.exchanges[0]}</span>
                )}
                {company.sicDescription && (
                  <span className="rounded-full bg-white px-3 py-1 shadow-sm">{company.sicDescription}</span>
                )}
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-white/90 p-4 shadow-sm">
                <div className="flex items-center gap-2 text-sm text-slate-600">
                  <CalendarDays className="h-4 w-4 text-blue-600" />
                  Price Bars
                </div>
                <p className="mt-3 text-2xl font-semibold text-slate-950">{priceSnapshot.bars || '-'}</p>
                <p className="mt-1 text-xs text-slate-500">One-year daily range fetched from Tiingo</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white/90 p-4 shadow-sm">
                <div className="flex items-center gap-2 text-sm text-slate-600">
                  <Landmark className="h-4 w-4 text-blue-600" />
                  Annual Source
                </div>
                <p className="mt-3 text-xl font-semibold text-slate-950">
                  {selectedAnnual?.formType ?? '-'}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedAnnual
                    ? formatDate(selectedAnnual.filingDate)
                    : metadataSyncActive
                      ? 'Refreshing latest SEC metadata'
                      : 'No annual filing selected'}
                </p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white/90 p-4 shadow-sm">
                <div className="flex items-center gap-2 text-sm text-slate-600">
                  <Building2 className="h-4 w-4 text-blue-600" />
                  Quarterly Source
                </div>
                <p className="mt-3 text-xl font-semibold text-slate-950">
                  {selectedQuarterly?.formType ?? '-'}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedQuarterly
                    ? formatDate(selectedQuarterly.filingDate)
                    : metadataSyncActive
                      ? 'Refreshing latest SEC metadata'
                      : 'No quarterly filing selected'}
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-[28px] border border-slate-200 bg-slate-950 p-6 text-white shadow-sm">
            <p className="text-[11px] font-semibold uppercase tracking-[0.32em] text-blue-200/80">Live Price Snapshot</p>
            <div className="mt-4 flex items-end justify-between gap-3">
              <div>
                <p className="text-4xl font-semibold tracking-tight">
                  {priceSnapshot.latestClose != null ? `$${priceSnapshot.latestClose.toFixed(2)}` : '-'}
                </p>
                <p className={`mt-2 text-sm font-medium ${priceTone}`}>
                  {priceSnapshot.change != null && priceSnapshot.changePercent != null ? (
                    <>
                      {priceSnapshot.change > 0 ? <TrendingUp className="mr-1 inline h-4 w-4" /> : priceSnapshot.change < 0 ? <TrendingDown className="mr-1 inline h-4 w-4" /> : null}
                      {priceSnapshot.change > 0 ? '+' : ''}
                      ${priceSnapshot.change.toFixed(2)} ({(priceSnapshot.changePercent * 100).toFixed(2)}%)
                    </>
                  ) : (
                    'Price feed unavailable'
                  )}
                </p>
              </div>
              {priceHistory?.provider && (
                <div className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-blue-100">
                  {priceHistory.provider}
                </div>
              )}
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3">
              <div className="rounded-2xl bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.24em] text-slate-400">52W High</p>
                <p className="mt-2 text-lg font-semibold text-white">
                  {priceSnapshot.high52Week != null ? `$${priceSnapshot.high52Week.toFixed(2)}` : '-'}
                </p>
              </div>
              <div className="rounded-2xl bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.24em] text-slate-400">52W Low</p>
                <p className="mt-2 text-lg font-semibold text-white">
                  {priceSnapshot.low52Week != null ? `$${priceSnapshot.low52Week.toFixed(2)}` : '-'}
                </p>
              </div>
              <div className="rounded-2xl bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.24em] text-slate-400">As Of</p>
                <p className="mt-2 text-lg font-semibold text-white">{formatDate(priceSnapshot.asOf)}</p>
              </div>
              <div className="rounded-2xl bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.24em] text-slate-400">Ticker</p>
                <p className="mt-2 text-lg font-semibold text-white">{company.ticker || '-'}</p>
              </div>
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <Button onClick={() => navigate(`/search?cik=${company.cik}`)}>
                <FileText className="h-4 w-4" />
                Search Filings
              </Button>
              {company.ticker && (
                <Button variant="outline" className="border-white/20 bg-white/5 text-white hover:bg-white/10 hover:text-white" onClick={() => navigate(`/search?ticker=${company.ticker}`)}>
                  <ExternalLink className="h-4 w-4" />
                  Search By Ticker
                </Button>
              )}
            </div>
          </div>
        </div>
      </section>

      {(priceError || annualError || quarterlyError || annualTrendError || metadataSyncError) && (
        <div className="grid gap-4">
          {priceError && (
            <ErrorMessage
              title="Price feed issue"
              message={priceError}
            />
          )}
          {annualError && (
            <ErrorMessage
              title="Annual filing analysis issue"
              message={annualError}
            />
          )}
          {quarterlyError && (
            <ErrorMessage
              title="Quarterly filing analysis issue"
              message={quarterlyError}
            />
          )}
          {annualTrendError && (
            <ErrorMessage
              title="Annual trend issue"
              message={annualTrendError}
            />
          )}
          {metadataSyncError && (
            <ErrorMessage
              title="Metadata refresh issue"
              message={metadataSyncError}
            />
          )}
        </div>
      )}

      {metadataSyncActive && (
        <div className="rounded-[24px] border border-blue-100 bg-blue-50 px-5 py-4 text-sm text-blue-900 shadow-sm">
          Refreshing the latest SEC submissions metadata for CIK {company.cik}. Updated filing records will be cached locally and this page will reload them automatically.
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <div className="space-y-6">
          <FilingPicker
            title="Annual Statement Source"
            icon={Landmark}
            filings={annualCandidates}
            selectedId={selectedAnnualId}
            onSelect={setSelectedAnnualId}
            onOpenFiling={(id) => navigate(`/filing/${id}`)}
            selectedSnapshot={annualSnapshot}
            loading={filingsLoading || annualLoading}
            emptyAction={{
              label: metadataSyncActive ? 'Refreshing Latest Metadata' : 'Refresh Latest Metadata',
              hint: metadataSyncActive
                ? 'Latest SEC submissions metadata is being refreshed and cached automatically.'
                : 'Re-run the SEC submissions refresh if you want to force the local cache to update now.',
              loading: metadataSyncActive,
              disabled: metadataSyncActive || !cik,
              onClick: () => {
                void refreshLatestMetadata('manual');
              },
            }}
          />
          <FilingPicker
            title="Quarterly Statement Source"
            icon={CalendarDays}
            filings={quarterlyCandidates}
            selectedId={selectedQuarterlyId}
            onSelect={setSelectedQuarterlyId}
            onOpenFiling={(id) => navigate(`/filing/${id}`)}
            selectedSnapshot={quarterlySnapshot}
            loading={filingsLoading || quarterlyLoading}
          />
          <InsiderActivityCard
            ticker={company.ticker || company.cik}
            latestBuy={latestBuy}
            latestSell={latestSell}
            ownershipFilings={recentOwnershipFilings}
            loading={insiderActivityLoading}
            form4Error={form4ActivityError}
            ownershipError={ownershipActivityError}
          />
        </div>
        <div className="space-y-6">
          <PriceTrendCard history={priceHistory} ticker={company.ticker || company.cik} />
          <FundamentalTrendCard
            title="Earnings Trend"
            subtitle={`Diluted EPS across the latest annual filings for ${company.ticker || company.cik}.`}
            badge="Annual"
            data={earningsTrendData}
            loading={annualTrendLoading}
            valueLabel="Diluted EPS"
            valueFormatter={(value) => formatCurrencyValue(value)}
            tooltipMetricLabel="P/E"
            tooltipMetricResolver={(point) => point.valuationDisplay}
            emptyMessage="No annual diluted EPS values were found in the recent XBRL filing history."
            color="#059669"
            gradientId="fundamentalsEarningsFill"
          />
          <FundamentalTrendCard
            title="Dividend Trend"
            subtitle={`Dividends per share across the latest annual filings for ${company.ticker || company.cik}.`}
            badge="Annual"
            data={dividendTrendData}
            loading={annualTrendLoading}
            valueLabel="Dividend / Share"
            valueFormatter={(value) => formatCurrencyValue(value)}
            tooltipMetricLabel="Yield"
            tooltipMetricResolver={(point) => point.yieldDisplay}
            emptyMessage="No annual dividend-per-share values were found in the recent XBRL filing history."
            color="#d97706"
            gradientId="fundamentalsDividendFill"
          />
        </div>
      </div>

      {(filingsLoading || annualLoading || quarterlyLoading || priceLoading) && (
        <div className="rounded-[28px] border border-slate-200 bg-white px-6 py-8 shadow-sm">
          <LoadingSpinner size="md" text="Refreshing ratio board..." />
        </div>
      )}

      <div className="space-y-6">
        {sections.map((section) => (
          <MetricBoard
            key={section.id}
            section={section}
            action={section.id === 'annual' && annualCandidates.length === 0 ? (
              <Button
                size="sm"
                variant="outline"
                className="border-white/20 bg-white/5 text-white hover:bg-white/10 hover:text-white"
                onClick={() => {
                  void refreshLatestMetadata('manual');
                }}
                disabled={metadataSyncActive || !cik}
              >
                {metadataSyncActive ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileText className="h-4 w-4" />}
                {metadataSyncActive ? 'Refreshing Latest' : 'Refresh Latest'}
              </Button>
            ) : undefined}
          />
        ))}
      </div>
    </div>
  );
}
