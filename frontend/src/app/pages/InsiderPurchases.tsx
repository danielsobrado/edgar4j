import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShoppingCart,
  RefreshCw,
  DollarSign,
  BarChart3,
  Building2,
  TrendingUp,
} from 'lucide-react';
import * as Switch from '@radix-ui/react-switch';
import { useInsiderPurchases, useSettings } from '../hooks';
import type { InsiderPurchaseFilter, InsiderPurchaseSortBy, MarketCapSource, Settings } from '../api';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Pagination } from '../components/common/Pagination';
import { buildForm4SearchUrl, formatCompact, formatCurrency, formatNumber, toDisplayDate } from '../utils';

const LOOKBACK_OPTIONS = [
  { value: 7, label: '7 days' },
  { value: 14, label: '14 days' },
  { value: 30, label: '30 days' },
  { value: 60, label: '60 days' },
  { value: 90, label: '90 days' },
] as const;

const MARKET_CAP_OPTIONS = [
  { value: 0, label: 'Any market cap' },
  { value: 100_000_000, label: '$100M+' },
  { value: 500_000_000, label: '$500M+' },
  { value: 1_000_000_000, label: '$1B+' },
  { value: 10_000_000_000, label: '$10B+' },
  { value: 50_000_000_000, label: '$50B+' },
] as const;

const TRANSACTION_VALUE_OPTIONS = [
  { value: 0, label: 'Any size' },
  { value: 10_000, label: '$10K+' },
  { value: 50_000, label: '$50K+' },
  { value: 100_000, label: '$100K+' },
  { value: 500_000, label: '$500K+' },
  { value: 1_000_000, label: '$1M+' },
] as const;

const SORT_OPTIONS: ReadonlyArray<{ value: InsiderPurchaseSortBy; label: string }> = [
  { value: 'percentChange', label: '% change' },
  { value: 'transactionDate', label: 'Transaction date' },
  { value: 'transactionValue', label: 'Transaction value' },
  { value: 'marketCap', label: 'Market cap' },
  { value: 'ticker', label: 'Ticker' },
];

const DEFAULT_FILTER: InsiderPurchaseFilter = {
  lookbackDays: 30,
  minMarketCap: 0,
  sp500Only: false,
  minTransactionValue: 0,
  sortBy: 'percentChange',
  sortDir: 'desc',
  page: 0,
  size: 50,
};

const MARKET_CAP_SOURCE_LABELS: Record<MarketCapSource, string> = {
  UNKNOWN: 'Unknown',
  PROVIDER_MARKET_CAP: 'Provider cap',
  PROVIDER_SHARES_OUTSTANDING: 'Provider shares',
  SEC_COMPANYFACTS_SHARES_OUTSTANDING: 'SEC companyfacts',
  SEC_FILING_XBRL_SHARES_OUTSTANDING: 'SEC filing XBRL',
};

function getMarketCapSourceLabel(source: MarketCapSource | null | undefined): string | null {
  if (!source) {
    return null;
  }

  return MARKET_CAP_SOURCE_LABELS[source] ?? source;
}

function buildInitialFilter(settings: Settings | null): InsiderPurchaseFilter {
  return {
    ...DEFAULT_FILTER,
    lookbackDays: settings?.insiderPurchaseLookbackDays ?? DEFAULT_FILTER.lookbackDays,
    minMarketCap: settings?.insiderPurchaseMinMarketCap ?? DEFAULT_FILTER.minMarketCap,
    sp500Only: settings?.insiderPurchaseSp500Only ?? DEFAULT_FILTER.sp500Only,
    minTransactionValue: settings?.insiderPurchaseMinTransactionValue ?? DEFAULT_FILTER.minTransactionValue,
  };
}

function formatCompactCurrency(value: number | null | undefined): string {
  if (value == null) {
    return '-';
  }

  return `$${formatCompact(value)}`;
}

function formatSignedPercent(value: number | null | undefined): string {
  if (value == null) {
    return '-';
  }

  return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
}

function SummaryCard({
  icon,
  title,
  value,
  subtitle,
  tone = 'default',
}: {
  icon: React.ReactNode;
  title: string;
  value: string;
  subtitle?: string;
  tone?: 'default' | 'positive' | 'negative';
}) {
  const toneClass = tone === 'positive'
    ? 'text-green-600'
    : tone === 'negative'
      ? 'text-red-600'
      : 'text-gray-900';

  return (
    <div className="bg-white rounded-lg shadow-sm p-6">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-gray-100">
        {icon}
      </div>
      <p className="text-sm text-gray-600">{title}</p>
      <p className={`mt-1 text-3xl ${toneClass}`}>{value}</p>
      {subtitle ? <p className="mt-1 text-sm text-gray-500">{subtitle}</p> : null}
    </div>
  );
}

function InsiderPurchasesContent({
  initialFilter,
  settingsError,
  onRetrySettings,
}: {
  initialFilter: InsiderPurchaseFilter;
  settingsError: string | null;
  onRetrySettings: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const { purchases, summary, loading, error, filter, setFilter, refresh } = useInsiderPurchases(initialFilter);

  const updateFilter = React.useCallback(
    (patch: Partial<InsiderPurchaseFilter>) => {
      setFilter((current) => ({
        ...current,
        ...patch,
        page: 0,
      }));
    },
    [setFilter],
  );

  const resetFilters = React.useCallback(() => {
    setFilter({
      ...initialFilter,
      size: filter.size ?? initialFilter.size ?? DEFAULT_FILTER.size,
      page: 0,
    });
  }, [filter.size, initialFilter, setFilter]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-green-100">
            <ShoppingCart className="w-5 h-5 text-green-700" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-gray-900">Stocks with Recent Insider Purchases</h1>
            <p className="text-sm text-gray-500">
              Open-market insider buying activity with market-cap filters and post-purchase performance.
            </p>
          </div>
        </div>

        <button
          onClick={refresh}
          className="inline-flex items-center gap-2 self-start rounded-md border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {settingsError ? (
        <ErrorMessage
          title="Saved defaults unavailable"
          message={`${settingsError} Using built-in filters for this session.`}
          onRetry={() => {
            void onRetrySettings();
          }}
        />
      ) : null}

      {error ? (
        <ErrorMessage
          title="Failed to load insider purchases"
          message={error}
          onRetry={refresh}
        />
      ) : null}

      {summary ? (
        <>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              icon={<ShoppingCart className="w-6 h-6 text-blue-600" />}
              title="Total purchases"
              value={summary.totalPurchases.toLocaleString()}
              subtitle={`${summary.uniqueCompanies.toLocaleString()} companies`}
            />
            <SummaryCard
              icon={<Building2 className="w-6 h-6 text-emerald-600" />}
              title="Unique companies"
              value={summary.uniqueCompanies.toLocaleString()}
              subtitle="Across the selected lookback window"
            />
            <SummaryCard
              icon={<DollarSign className="w-6 h-6 text-green-600" />}
              title="Purchase value"
              value={formatCompactCurrency(summary.totalPurchaseValue)}
              subtitle={formatCurrency(summary.totalPurchaseValue)}
            />
            <SummaryCard
              icon={<BarChart3 className="w-6 h-6 text-purple-600" />}
              title="Average % change"
              value={formatSignedPercent(summary.averagePercentChange)}
              subtitle={`${summary.positiveChangeCount} up / ${summary.negativeChangeCount} down`}
              tone={summary.averagePercentChange >= 0 ? 'positive' : 'negative'}
            />
          </div>

          <p className="text-sm text-gray-500">
            Summary cards reflect the selected lookback window before market-cap, S&amp;P 500, and transaction-size filters are applied.
          </p>
        </>
      ) : null}

      <div className="bg-white rounded-lg shadow-sm p-5">
        <div className="mb-4 flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Filters</h2>
            <p className="text-sm text-gray-500">
              Narrow recent insider buys and change the ranking order without leaving the page.
            </p>
          </div>
          <button
            onClick={resetFilters}
            className="self-start rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            Reset to defaults
          </button>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
          <div>
            <label htmlFor="insider-lookback" className="mb-2 block text-sm text-gray-600">
              Lookback
            </label>
            <select
              id="insider-lookback"
              value={filter.lookbackDays ?? DEFAULT_FILTER.lookbackDays}
              onChange={(event) => updateFilter({ lookbackDays: Number(event.target.value) })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {LOOKBACK_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="insider-market-cap" className="mb-2 block text-sm text-gray-600">
              Min market cap
            </label>
            <select
              id="insider-market-cap"
              value={filter.minMarketCap ?? DEFAULT_FILTER.minMarketCap}
              onChange={(event) => updateFilter({ minMarketCap: Number(event.target.value) })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {MARKET_CAP_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="insider-transaction-value" className="mb-2 block text-sm text-gray-600">
              Min transaction value
            </label>
            <select
              id="insider-transaction-value"
              value={filter.minTransactionValue ?? DEFAULT_FILTER.minTransactionValue}
              onChange={(event) => updateFilter({ minTransactionValue: Number(event.target.value) })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {TRANSACTION_VALUE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="insider-sort-by" className="mb-2 block text-sm text-gray-600">
              Sort by
            </label>
            <select
              id="insider-sort-by"
              value={filter.sortBy ?? DEFAULT_FILTER.sortBy}
              onChange={(event) => updateFilter({ sortBy: event.target.value as InsiderPurchaseSortBy })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="insider-sort-dir" className="mb-2 block text-sm text-gray-600">
              Direction
            </label>
            <select
              id="insider-sort-dir"
              value={filter.sortDir ?? DEFAULT_FILTER.sortDir}
              onChange={(event) => updateFilter({ sortDir: event.target.value as 'asc' | 'desc' })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="desc">Descending</option>
              <option value="asc">Ascending</option>
            </select>
          </div>

          <div className="rounded-md border border-gray-200 px-4 py-3">
            <div className="flex items-center justify-between gap-4">
              <div>
                <label htmlFor="insider-sp500-only" className="block text-sm text-gray-700">
                  S&amp;P 500 only
                </label>
                <p className="text-xs text-gray-500">Restrict results to current index members</p>
              </div>
              <Switch.Root
                id="insider-sp500-only"
                checked={filter.sp500Only ?? false}
                onCheckedChange={(checked) => updateFilter({ sp500Only: checked })}
                className="w-11 h-6 rounded-full relative bg-gray-200 data-[state=checked]:bg-blue-600 transition-colors"
              >
                <Switch.Thumb className="block w-5 h-5 bg-white rounded-full shadow transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
              </Switch.Root>
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm overflow-hidden">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Results</h2>
            <p className="text-sm text-gray-500">
              {purchases
                ? `${purchases.totalElements.toLocaleString()} purchase${purchases.totalElements === 1 ? '' : 's'} matched`
                : 'Recent purchase activity'}
            </p>
          </div>
          <div className="inline-flex items-center gap-2 rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-600">
            <TrendingUp className="w-3.5 h-3.5" />
            Ranked by {SORT_OPTIONS.find((option) => option.value === (filter.sortBy ?? DEFAULT_FILTER.sortBy))?.label.toLowerCase()}
          </div>
        </div>

        {loading ? (
          <div className="py-16">
            <LoadingSpinner size="lg" text="Loading insider purchases..." />
          </div>
        ) : !purchases || purchases.content.length === 0 ? (
          <EmptyState
            type="filings"
            title="No insider purchases found"
            message="Try broadening the filters or check back after more Form 4 filings are synced."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium text-gray-600">% change</th>
                    <th className="px-4 py-3 text-left font-medium text-gray-600">Company</th>
                    <th className="px-4 py-3 text-left font-medium text-gray-600">Insider</th>
                    <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                    <th className="px-4 py-3 text-right font-medium text-gray-600">Shares</th>
                    <th className="px-4 py-3 text-right font-medium text-gray-600">Paid</th>
                    <th className="px-4 py-3 text-right font-medium text-gray-600">Current</th>
                    <th className="px-4 py-3 text-right font-medium text-gray-600">Value</th>
                    <th className="px-4 py-3 text-right font-medium text-gray-600">Market cap</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {purchases.content.map((purchase, index) => (
                    <tr key={`${purchase.accessionNumber}-${index}`} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${
                            (purchase.percentChange ?? 0) >= 0
                              ? 'bg-green-50 text-green-700'
                              : 'bg-red-50 text-red-700'
                          }`}
                        >
                          {formatSignedPercent(purchase.percentChange)}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => navigate(buildForm4SearchUrl({ ticker: purchase.ticker, cik: purchase.cik }))}
                            className="truncate text-left font-medium text-blue-600 hover:underline"
                            title={purchase.companyName ?? purchase.ticker}
                          >
                            {purchase.companyName ?? purchase.ticker}
                          </button>
                          {purchase.sp500 ? (
                            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[11px] font-medium text-amber-800">
                              S&amp;P 500
                            </span>
                          ) : null}
                        </div>
                        <div className="mt-1 text-xs font-mono text-gray-500">
                          {purchase.ticker}
                          {purchase.cik ? ` | ${purchase.cik}` : ''}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="max-w-[220px] truncate" title={purchase.insiderName ?? '-'}>
                          {purchase.insiderName ?? '-'}
                        </div>
                        <div className="mt-1 text-xs text-gray-500">
                          {purchase.insiderTitle || purchase.ownerType || '-'}
                        </div>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap font-mono text-xs text-gray-600">
                        {toDisplayDate(purchase.transactionDate)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono">
                        {formatNumber(purchase.transactionShares)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono">
                        {formatCurrency(purchase.purchasePrice)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono">
                        {formatCurrency(purchase.currentPrice)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono">
                        {formatCompactCurrency(purchase.transactionValue)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-gray-600">
                        <div>{formatCompactCurrency(purchase.marketCap)}</div>
                        {purchase.marketCap != null && getMarketCapSourceLabel(purchase.marketCapSource) ? (
                          <div className="mt-1">
                            <span className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700">
                              {getMarketCapSourceLabel(purchase.marketCapSource)}
                            </span>
                          </div>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="border-t border-gray-200 px-5 py-3">
              <Pagination
                page={purchases.page}
                totalPages={purchases.totalPages}
                totalElements={purchases.totalElements}
                size={filter.size ?? DEFAULT_FILTER.size ?? 50}
                onPageChange={(page) => {
                  setFilter((current) => ({ ...current, page }));
                }}
                onPageSizeChange={(size) => {
                  setFilter((current) => ({ ...current, size, page: 0 }));
                }}
              />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export function InsiderPurchasesPage() {
  const { settings, loading, error, refresh } = useSettings();
  const initialFilter = React.useMemo(
    () => (loading && !settings ? DEFAULT_FILTER : buildInitialFilter(settings)),
    [loading, settings],
  );

  return (
    <InsiderPurchasesContent
      key={JSON.stringify(initialFilter)}
      initialFilter={initialFilter}
      settingsError={loading ? null : error}
      onRetrySettings={refresh}
    />
  );
}
