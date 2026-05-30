import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  CalendarRange,
  Download,
  FileJson,
  Filter,
  RefreshCw,
  Search,
  SlidersHorizontal,
  TrendingDown,
  TrendingUp,
  Users,
} from 'lucide-react';
import { insiderActivityApi } from '../api';
import { downloadsApi } from '../api';
import type {
  InsiderActivity,
  InsiderActivityFilter,
  InsiderActivityPreset,
  InsiderActivitySide,
  InsiderActivityView,
} from '../api';
import { useInsiderActivity } from '../hooks';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Pagination } from '../components/common/Pagination';
import { buildForm4SearchUrl, formatCompact, formatCurrency, formatNumber, toDisplayDate } from '../utils';
import { showError, showSuccess } from '../store/notificationStore';
import { InsiderCoverageHeatmap } from '../components/insider/InsiderCoverageHeatmap';

const PRESETS: ReadonlyArray<{
  value: InsiderActivityPreset;
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
}> = [
  { value: 'LATEST_PURCHASES', label: 'Latest Purchases', description: 'Last 2 trading days', icon: TrendingUp },
  { value: 'LATEST_SALES', label: 'Latest Sales', description: 'Last 2 trading days', icon: TrendingDown },
  { value: 'MULTI_INSIDER_BUYS', label: '2+ Buyers', description: 'Last 3 months', icon: Users },
  { value: 'MULTI_INSIDER_SELLS', label: '2+ Sellers', description: 'Last 3 months', icon: Users },
  { value: 'MILLION_DOLLAR_BUYS', label: '$1M+ Buys', description: 'Last 30 days', icon: TrendingUp },
  { value: 'MILLION_DOLLAR_SELLS', label: '$1M+ Sells', description: 'Last 30 days', icon: TrendingDown },
];

const VIEW_OPTIONS: Array<{ value: InsiderActivityView; label: string }> = [
  { value: 'AGGREGATE', label: 'Stocks' },
  { value: 'TRANSACTION', label: 'Transactions' },
];

const SIDE_OPTIONS: Array<{ value: InsiderActivitySide; label: string }> = [
  { value: 'BUY', label: 'Buys' },
  { value: 'SELL', label: 'Sells' },
];

const DEFAULT_FILTER: Required<Pick<InsiderActivityFilter, 'preset' | 'page' | 'size' | 'sortDir'>> = {
  preset: 'LATEST_PURCHASES',
  page: 0,
  size: 50,
  sortDir: 'desc',
};

const PRESET_DEFAULTS: Record<InsiderActivityPreset, Partial<InsiderActivityFilter>> = {
  LATEST_PURCHASES: { view: 'TRANSACTION', side: 'BUY', transactionCodes: ['P'], sortBy: 'transactionDate' },
  LATEST_SALES: { view: 'TRANSACTION', side: 'SELL', transactionCodes: ['S'], sortBy: 'transactionDate' },
  MULTI_INSIDER_BUYS: { view: 'AGGREGATE', side: 'BUY', transactionCodes: ['P'], minInsiderCount: 2, sortBy: 'totalValue' },
  MULTI_INSIDER_SELLS: { view: 'AGGREGATE', side: 'SELL', transactionCodes: ['S'], minInsiderCount: 2, sortBy: 'totalValue' },
  MILLION_DOLLAR_BUYS: { view: 'AGGREGATE', side: 'BUY', transactionCodes: ['P'], minTotalAmount: 1_000_000, sortBy: 'totalValue' },
  MILLION_DOLLAR_SELLS: { view: 'AGGREGATE', side: 'SELL', transactionCodes: ['S'], minTotalAmount: 1_000_000, sortBy: 'totalValue' },
};

function parseFilter(searchParams: URLSearchParams): InsiderActivityFilter {
  const preset = parsePreset(searchParams.get('preset'));
  const presetDefaults = PRESET_DEFAULTS[preset];

  return {
    ...DEFAULT_FILTER,
    ...presetDefaults,
    preset,
    view: parseView(searchParams.get('view')) ?? presetDefaults.view,
    side: parseSide(searchParams.get('side')) ?? presetDefaults.side,
    transactionCodes: parseCodes(searchParams.get('transactionCodes')) ?? presetDefaults.transactionCodes,
    dateFrom: searchParams.get('dateFrom') ?? undefined,
    dateTo: searchParams.get('dateTo') ?? undefined,
    symbol: searchParams.get('symbol') ?? undefined,
    minPrice: parsePositiveNumber(searchParams.get('minPrice')),
    minShares: parsePositiveNumber(searchParams.get('minShares')),
    minTotalAmount: parsePositiveNumber(searchParams.get('minTotalAmount')) ?? presetDefaults.minTotalAmount,
    minInsiderCount: parsePositiveNumber(searchParams.get('minInsiderCount')) ?? presetDefaults.minInsiderCount,
    insiderTitle: searchParams.get('insiderTitle') ?? undefined,
    sortBy: searchParams.get('sortBy') ?? presetDefaults.sortBy,
    sortDir: searchParams.get('sortDir') === 'asc' ? 'asc' : 'desc',
    page: Number(searchParams.get('page') ?? DEFAULT_FILTER.page),
    size: Number(searchParams.get('size') ?? DEFAULT_FILTER.size),
  };
}

function writeFilter(filter: InsiderActivityFilter): URLSearchParams {
  const params = new URLSearchParams();
  setParam(params, 'preset', filter.preset);
  setParam(params, 'view', filter.view);
  setParam(params, 'side', filter.side);
  if (filter.transactionCodes?.length) {
    params.set('transactionCodes', filter.transactionCodes.join(','));
  }
  setParam(params, 'dateFrom', filter.dateFrom);
  setParam(params, 'dateTo', filter.dateTo);
  setParam(params, 'symbol', filter.symbol);
  setNumberParam(params, 'minPrice', filter.minPrice);
  setNumberParam(params, 'minShares', filter.minShares);
  setNumberParam(params, 'minTotalAmount', filter.minTotalAmount);
  setNumberParam(params, 'minInsiderCount', filter.minInsiderCount);
  setParam(params, 'insiderTitle', filter.insiderTitle);
  setParam(params, 'sortBy', filter.sortBy);
  setParam(params, 'sortDir', filter.sortDir);
  params.set('page', String(filter.page ?? 0));
  params.set('size', String(filter.size ?? 50));
  return params;
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

function parsePreset(value: string | null): InsiderActivityPreset {
  return PRESETS.some((preset) => preset.value === value) ? value as InsiderActivityPreset : DEFAULT_FILTER.preset;
}

function parseView(value: string | null): InsiderActivityView | undefined {
  return value === 'AGGREGATE' || value === 'TRANSACTION' ? value : undefined;
}

function parseSide(value: string | null): InsiderActivitySide | undefined {
  return value === 'BUY' || value === 'SELL' ? value : undefined;
}

function parseCodes(value: string | null): string[] | undefined {
  if (!value) {
    return undefined;
  }
  const codes = value.split(',').map((code) => code.trim().toUpperCase()).filter(Boolean);
  return codes.length ? codes : undefined;
}

function parsePositiveNumber(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function setParam(params: URLSearchParams, key: string, value: string | undefined | null) {
  if (value && value.trim()) {
    params.set(key, value.trim());
  }
}

function setNumberParam(params: URLSearchParams, key: string, value: number | undefined | null) {
  if (value != null && value > 0) {
    params.set(key, String(value));
  }
}

function AggregateRows({
  rows,
  onOpenForm4,
}: {
  rows: InsiderActivity[];
  onOpenForm4: (row: InsiderActivity) => void;
}) {
  return (
    <tbody className="divide-y divide-gray-100">
      {rows.map((row) => (
        <tr key={`${row.ticker}-${row.side}`} className="hover:bg-gray-50">
          <td className="px-4 py-3">
            <button
              onClick={() => onOpenForm4(row)}
              className="text-left font-medium text-blue-600 hover:underline"
            >
              {row.companyName ?? row.ticker}
            </button>
            <div className="mt-1 text-xs font-mono text-gray-500">
              {row.ticker}{row.cik ? ` | ${row.cik}` : ''}
            </div>
          </td>
          <td className="px-4 py-3">
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
              row.side === 'BUY' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
            }`}>
              {row.side}
            </span>
          </td>
          <td className="px-4 py-3 text-right font-mono">{formatCompactCurrency(row.totalValue)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatNumber(row.insiderCount)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatNumber(row.transactionCount)}</td>
          <td className="px-4 py-3 whitespace-nowrap font-mono text-xs">{toDisplayDate(row.latestTransactionDate)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatNumber(row.totalShares)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCurrency(row.averagePrice)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCompactCurrency(row.marketCap)}</td>
          <td className="px-4 py-3 text-xs text-gray-600">{row.transactionCodes?.join(', ') || '-'}</td>
        </tr>
      ))}
    </tbody>
  );
}

function TransactionRows({
  rows,
  onOpenForm4,
}: {
  rows: InsiderActivity[];
  onOpenForm4: (row: InsiderActivity) => void;
}) {
  return (
    <tbody className="divide-y divide-gray-100">
      {rows.map((row, index) => (
        <tr key={`${row.accessionNumber}-${index}`} className="hover:bg-gray-50">
          <td className="px-4 py-3 whitespace-nowrap font-mono text-xs">{toDisplayDate(row.transactionDate)}</td>
          <td className="px-4 py-3">
            <button
              onClick={() => onOpenForm4(row)}
              className="text-left font-medium text-blue-600 hover:underline"
            >
              {row.companyName ?? row.ticker}
            </button>
            <div className="mt-1 text-xs font-mono text-gray-500">
              {row.ticker}{row.cik ? ` | ${row.cik}` : ''}
            </div>
          </td>
          <td className="px-4 py-3">
            <div className="max-w-[220px] truncate" title={row.insiderName ?? '-'}>
              {row.insiderName ?? '-'}
            </div>
            <div className="mt-1 text-xs text-gray-500">{row.insiderTitle || row.ownerType || '-'}</div>
          </td>
          <td className="px-4 py-3">
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
              row.side === 'BUY' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
            }`}>
              {row.transactionCode ?? row.side}
            </span>
          </td>
          <td className="px-4 py-3 text-right font-mono">{formatNumber(row.transactionShares)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCurrency(row.transactionPrice)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCompactCurrency(row.transactionValue)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCurrency(row.currentPrice)}</td>
          <td className="px-4 py-3 text-right">
            <span className={`font-mono ${(row.percentChange ?? 0) >= 0 ? 'text-green-700' : 'text-red-700'}`}>
              {formatSignedPercent(row.percentChange)}
            </span>
          </td>
          <td className="px-4 py-3 text-right font-mono">{formatCompactCurrency(row.marketCap)}</td>
        </tr>
      ))}
    </tbody>
  );
}

export function InsiderActivityPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const filter = React.useMemo(() => parseFilter(searchParams), [searchParams]);
  const { results, loading, error, refresh } = useInsiderActivity(filter);
  const [exporting, setExporting] = React.useState<'CSV' | 'JSON' | null>(null);
  const [codeInput, setCodeInput] = React.useState((filter.transactionCodes ?? []).join(','));
  const [showCoverage, setShowCoverage] = React.useState(false);
  const [coverageRefreshKey, setCoverageRefreshKey] = React.useState(0);
  const [downloadingCoverage, setDownloadingCoverage] = React.useState(false);

  React.useEffect(() => {
    setCodeInput((filter.transactionCodes ?? []).join(','));
  }, [filter.transactionCodes]);

  const updateFilter = React.useCallback((patch: Partial<InsiderActivityFilter>) => {
    setSearchParams(writeFilter({
      ...filter,
      ...patch,
      page: patch.page ?? 0,
    }));
  }, [filter, setSearchParams]);

  const applyCodeInput = React.useCallback(() => {
    updateFilter({ transactionCodes: parseCodes(codeInput) ?? [] });
  }, [codeInput, updateFilter]);

  const setPreset = React.useCallback((preset: InsiderActivityPreset) => {
    setSearchParams(writeFilter({
      ...DEFAULT_FILTER,
      ...PRESET_DEFAULTS[preset],
      preset,
      page: 0,
      size: filter.size ?? DEFAULT_FILTER.size,
    }));
  }, [filter.size, setSearchParams]);

  const exportResults = React.useCallback(async (format: 'CSV' | 'JSON') => {
    setExporting(format);
    try {
      await insiderActivityApi.export(filter, format);
    } finally {
      setExporting(null);
    }
  }, [filter]);

  const openForm4 = React.useCallback((row: InsiderActivity) => {
    navigate(buildForm4SearchUrl({ ticker: row.ticker, cik: row.cik ?? undefined }));
  }, [navigate]);

  const downloadInsiderForm = React.useCallback(async (
    form: string,
    from: string,
    to: string,
    syncMode: 'COMPANY' | 'FILING_DATE'
  ) => {
    setDownloadingCoverage(true);
    try {
      const job = await downloadsApi.downloadRemoteFilings({
        formType: form,
        dateFrom: from,
        dateTo: to,
        remoteFilingSyncMode: syncMode,
      });
      showSuccess(
        'Download queued',
        `Form ${form} sync queued for ${from} to ${to} (${syncMode}). Check Downloads for job ${job.id}.`
      );
    } catch (error) {
      showError('Download failed', error instanceof Error ? error.message : 'Failed to queue insider filing sync');
    } finally {
      setDownloadingCoverage(false);
      setCoverageRefreshKey((current) => current + 1);
    }
  }, []);

  const activePreset = PRESETS.find((preset) => preset.value === filter.preset) ?? PRESETS[0];
  const isAggregate = filter.view === 'AGGREGATE';

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-100">
            <Filter className="w-5 h-5 text-blue-700" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-gray-900">Insider Activity Screener</h1>
            <p className="text-sm text-gray-500">
              Preset and custom Form 4 screens for insider buying and selling activity.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setShowCoverage((current) => !current)}
            className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${
              showCoverage
                ? 'border-blue-600 bg-blue-600 text-white'
                : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
          >
            <CalendarRange className="h-4 w-4" />
            Data Coverage
          </button>
          <button
            onClick={() => void exportResults('CSV')}
            disabled={Boolean(exporting)}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            <Download className="w-4 h-4" />
            CSV
          </button>
          <button
            onClick={() => void exportResults('JSON')}
            disabled={Boolean(exporting)}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            <FileJson className="w-4 h-4" />
            JSON
          </button>
          <button
            onClick={refresh}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {error ? (
        <ErrorMessage title="Failed to load insider activity" message={error} onRetry={refresh} />
      ) : null}

      {showCoverage && (
        <InsiderCoverageHeatmap
          onSelectRange={(from, to) => updateFilter({ dateFrom: from, dateTo: to })}
          onDownload={downloadInsiderForm}
          downloading={downloadingCoverage}
          refreshKey={String(coverageRefreshKey)}
        />
      )}

      <div className="grid grid-cols-1 gap-3 lg:grid-cols-3 xl:grid-cols-6">
        {PRESETS.map((preset) => {
          const Icon = preset.icon;
          const active = preset.value === filter.preset;
          return (
            <button
              key={preset.value}
              onClick={() => setPreset(preset.value)}
              className={`rounded-lg border p-4 text-left transition-colors ${
                active ? 'border-blue-600 bg-blue-50' : 'border-gray-200 bg-white hover:bg-gray-50'
              }`}
            >
              <div className="flex items-center gap-2">
                <Icon className={`h-4 w-4 ${active ? 'text-blue-700' : 'text-gray-500'}`} />
                <span className="text-sm font-semibold text-gray-900">{preset.label}</span>
              </div>
              <p className="mt-1 text-xs text-gray-500">{preset.description}</p>
            </button>
          );
        })}
      </div>

      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-gray-500" />
          <h2 className="text-lg font-semibold text-gray-900">Filters</h2>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div>
            <label htmlFor="activity-view" className="mb-2 block text-sm text-gray-600">View</label>
            <select
              id="activity-view"
              value={filter.view ?? 'TRANSACTION'}
              onChange={(event) => updateFilter({ view: event.target.value as InsiderActivityView })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {VIEW_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="activity-side" className="mb-2 block text-sm text-gray-600">Side</label>
            <select
              id="activity-side"
              value={filter.side ?? 'BUY'}
              onChange={(event) => updateFilter({ side: event.target.value as InsiderActivitySide })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {SIDE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="activity-symbol" className="mb-2 block text-sm text-gray-600">Symbol</label>
            <input
              id="activity-symbol"
              value={filter.symbol ?? ''}
              onChange={(event) => updateFilter({ symbol: event.target.value || undefined })}
              placeholder="Ex: AAPL"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-codes" className="mb-2 block text-sm text-gray-600">Transaction codes</label>
            <div className="flex gap-2">
              <input
                id="activity-codes"
                value={codeInput}
                onChange={(event) => setCodeInput(event.target.value)}
                onBlur={applyCodeInput}
                placeholder="P,S"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                onClick={applyCodeInput}
                aria-label="Apply transaction codes"
                title="Apply transaction codes"
                className="inline-flex h-10 w-10 items-center justify-center rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
              >
                <Search className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div>
            <label htmlFor="activity-date-from" className="mb-2 block text-sm text-gray-600">Transaction date from</label>
            <input
              id="activity-date-from"
              type="date"
              value={filter.dateFrom ?? ''}
              onChange={(event) => updateFilter({ dateFrom: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-date-to" className="mb-2 block text-sm text-gray-600">Transaction date to</label>
            <input
              id="activity-date-to"
              type="date"
              value={filter.dateTo ?? ''}
              onChange={(event) => updateFilter({ dateTo: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-min-amount" className="mb-2 block text-sm text-gray-600">Min total amount</label>
            <input
              id="activity-min-amount"
              type="number"
              min="0"
              value={filter.minTotalAmount ?? ''}
              onChange={(event) => updateFilter({ minTotalAmount: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 1000000"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-min-insiders" className="mb-2 block text-sm text-gray-600">Min insiders</label>
            <input
              id="activity-min-insiders"
              type="number"
              min="0"
              value={filter.minInsiderCount ?? ''}
              onChange={(event) => updateFilter({ minInsiderCount: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 2"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-min-shares" className="mb-2 block text-sm text-gray-600">Min shares</label>
            <input
              id="activity-min-shares"
              type="number"
              min="0"
              value={filter.minShares ?? ''}
              onChange={(event) => updateFilter({ minShares: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 10000"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-min-price" className="mb-2 block text-sm text-gray-600">Min share price</label>
            <input
              id="activity-min-price"
              type="number"
              min="0"
              value={filter.minPrice ?? ''}
              onChange={(event) => updateFilter({ minPrice: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 20"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-title" className="mb-2 block text-sm text-gray-600">Insider title</label>
            <input
              id="activity-title"
              value={filter.insiderTitle ?? ''}
              onChange={(event) => updateFilter({ insiderTitle: event.target.value || undefined })}
              placeholder="Ex: Director"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="activity-sort" className="mb-2 block text-sm text-gray-600">Sort by</label>
            <select
              id="activity-sort"
              value={filter.sortBy ?? (isAggregate ? 'totalValue' : 'transactionDate')}
              onChange={(event) => updateFilter({ sortBy: event.target.value })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="totalValue">Value</option>
              <option value="transactionDate">Date</option>
              <option value="ticker">Ticker</option>
              <option value="insiderCount">Insiders</option>
              <option value="transactionCount">Transactions</option>
              <option value="totalShares">Shares</option>
              <option value="marketCap">Market cap</option>
              <option value="percentChange">% change</option>
            </select>
          </div>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg bg-white shadow-sm">
        <div className="flex flex-col gap-2 border-b border-gray-200 px-5 py-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{activePreset.label}</h2>
            <p className="text-sm text-gray-500">
              {results ? `${results.totalElements.toLocaleString()} row${results.totalElements === 1 ? '' : 's'} matched` : activePreset.description}
            </p>
          </div>
          <span className="self-start rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">
            {isAggregate ? 'Stock aggregate' : 'Transaction rows'}
          </span>
        </div>

        {loading ? (
          <div className="py-16">
            <LoadingSpinner size="lg" text="Loading insider activity..." />
          </div>
        ) : !results || results.content.length === 0 ? (
          <EmptyState
            type="filings"
            title="No insider activity found"
            message="Adjust the preset, date window, transaction codes, or thresholds."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                {isAggregate ? (
                  <>
                    <thead className="border-b border-gray-200 bg-gray-50">
                      <tr>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Company</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Side</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Value</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Insiders</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Transactions</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Latest date</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Shares</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Avg price</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Market cap</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Codes</th>
                      </tr>
                    </thead>
                    <AggregateRows rows={results.content} onOpenForm4={openForm4} />
                  </>
                ) : (
                  <>
                    <thead className="border-b border-gray-200 bg-gray-50">
                      <tr>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Company</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Insider</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Code</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Shares</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Price</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Value</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Current</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">% change</th>
                        <th className="px-4 py-3 text-right font-medium text-gray-600">Market cap</th>
                      </tr>
                    </thead>
                    <TransactionRows rows={results.content} onOpenForm4={openForm4} />
                  </>
                )}
              </table>
            </div>

            <div className="border-t border-gray-200 px-5 py-3">
              <Pagination
                page={results.page}
                totalPages={results.totalPages}
                totalElements={results.totalElements}
                size={filter.size ?? 50}
                onPageChange={(page) => updateFilter({ page })}
                onPageSizeChange={(size) => updateFilter({ size, page: 0 })}
              />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

