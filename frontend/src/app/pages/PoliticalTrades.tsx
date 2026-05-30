import React from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  CalendarRange,
  Download,
  FileJson,
  Filter,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Vote,
} from 'lucide-react';
import { politicalTradesApi } from '../api';
import type {
  PoliticalTrade,
  PoliticalTradeExportFormat,
  PoliticalTradeFilter,
  PoliticalTradeSyncResponse,
} from '../api';
import { usePoliticalTrades } from '../hooks';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Pagination } from '../components/common/Pagination';
import { PoliticalCoverageHeatmap } from '../components/political/PoliticalCoverageHeatmap';
import { formatCurrency, formatNumber, toDisplayDate } from '../utils';

const DEFAULT_FILTER: Required<Pick<PoliticalTradeFilter, 'assetType' | 'page' | 'size' | 'sortBy' | 'sortDir'>> = {
  assetType: 'stock',
  page: 0,
  size: 50,
  sortBy: 'disclosureDate',
  sortDir: 'desc',
};

const ASSET_TYPE_OPTIONS = [
  { value: 'stock', label: 'Stocks' },
  { value: 'ALL', label: 'All Assets' },
  { value: 'etf', label: 'ETF' },
  { value: 'mutual-fund', label: 'Mutual Fund' },
  { value: 'crypto', label: 'Crypto' },
  { value: 'corporate-bond', label: 'Corporate Bond' },
];

const TRANSACTION_OPTIONS = [
  { value: '', label: 'All Types' },
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
  { value: 'EXCHANGE', label: 'Exchange' },
  { value: 'RECEIVE', label: 'Received' },
];

const PARTY_OPTIONS = [
  { value: '', label: 'All Parties' },
  { value: 'Democrat', label: 'Democrat' },
  { value: 'Republican', label: 'Republican' },
  { value: 'Independent', label: 'Independent' },
];

const CHAMBER_OPTIONS = [
  { value: '', label: 'All Chambers' },
  { value: 'House', label: 'House' },
  { value: 'Senate', label: 'Senate' },
];

function parseFilter(searchParams: URLSearchParams): PoliticalTradeFilter {
  return {
    ...DEFAULT_FILTER,
    politician: searchParams.get('politician') ?? undefined,
    ticker: searchParams.get('ticker') ?? undefined,
    issuer: searchParams.get('issuer') ?? undefined,
    party: searchParams.get('party') ?? undefined,
    chamber: searchParams.get('chamber') ?? undefined,
    state: searchParams.get('state') ?? undefined,
    assetType: searchParams.get('assetType') ?? DEFAULT_FILTER.assetType,
    transactionType: searchParams.get('transactionType') ?? undefined,
    owner: searchParams.get('owner') ?? undefined,
    tradedDateFrom: searchParams.get('tradedDateFrom') ?? undefined,
    tradedDateTo: searchParams.get('tradedDateTo') ?? undefined,
    disclosureDateFrom: searchParams.get('disclosureDateFrom') ?? undefined,
    disclosureDateTo: searchParams.get('disclosureDateTo') ?? undefined,
    minAmount: parsePositiveNumber(searchParams.get('minAmount')),
    maxAmount: parsePositiveNumber(searchParams.get('maxAmount')),
    sortBy: searchParams.get('sortBy') ?? DEFAULT_FILTER.sortBy,
    sortDir: searchParams.get('sortDir') === 'asc' ? 'asc' : 'desc',
    page: Number(searchParams.get('page') ?? DEFAULT_FILTER.page),
    size: Number(searchParams.get('size') ?? DEFAULT_FILTER.size),
  };
}

function writeFilter(filter: PoliticalTradeFilter): URLSearchParams {
  const params = new URLSearchParams();
  setParam(params, 'politician', filter.politician);
  setParam(params, 'ticker', filter.ticker);
  setParam(params, 'issuer', filter.issuer);
  setParam(params, 'party', filter.party);
  setParam(params, 'chamber', filter.chamber);
  setParam(params, 'state', filter.state);
  setParam(params, 'assetType', filter.assetType);
  setParam(params, 'transactionType', filter.transactionType);
  setParam(params, 'owner', filter.owner);
  setParam(params, 'tradedDateFrom', filter.tradedDateFrom);
  setParam(params, 'tradedDateTo', filter.tradedDateTo);
  setParam(params, 'disclosureDateFrom', filter.disclosureDateFrom);
  setParam(params, 'disclosureDateTo', filter.disclosureDateTo);
  setNumberParam(params, 'minAmount', filter.minAmount);
  setNumberParam(params, 'maxAmount', filter.maxAmount);
  setParam(params, 'sortBy', filter.sortBy);
  setParam(params, 'sortDir', filter.sortDir);
  params.set('page', String(filter.page ?? 0));
  params.set('size', String(filter.size ?? 50));
  return params;
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

function initials(name: string | null | undefined): string {
  if (!name) {
    return '?';
  }
  const parts = name.split(/\s+/).filter(Boolean);
  return parts.slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?';
}

function transactionClass(type: string | null | undefined): string {
  const normalized = type?.toUpperCase();
  if (normalized === 'BUY') {
    return 'bg-green-50 text-green-700';
  }
  if (normalized === 'SELL') {
    return 'bg-red-50 text-red-700';
  }
  return 'bg-slate-100 text-slate-700';
}

function formatAmountLabel(row: PoliticalTrade): string {
  if (!row.amountLabel || row.amountLabel.toUpperCase() === 'N/A') {
    return '-';
  }
  const parts = row.amountLabel.replace(/\$/g, '').split('-').map((part) => part.trim()).filter(Boolean);
  if (parts.length === 2) {
    return `$${parts[0]} - $${parts[1]}`;
  }
  return row.amountLabel.startsWith('$') ? row.amountLabel : `$${row.amountLabel}`;
}

function syncSummary(syncResult: PoliticalTradeSyncResponse | null): string | null {
  if (!syncResult) {
    return null;
  }
  return `${formatNumber(syncResult.insertedRows)} inserted, ${formatNumber(syncResult.updatedRows)} updated, ${formatNumber(syncResult.fetchedRows)} fetched`;
}

function latestPoliticalUpdateDay(rows: PoliticalTrade[] | undefined, syncResult: PoliticalTradeSyncResponse | null): string {
  const candidates = [
    syncResult?.syncedAt,
    ...(rows ?? []).map((row) => row.updatedAt),
  ].filter(Boolean) as string[];
  const latest = candidates
    .map((value) => new Date(value))
    .filter((value) => Number.isFinite(value.getTime()))
    .sort((a, b) => b.getTime() - a.getTime())[0];
  return latest ? toDisplayDate(latest.toISOString()) : '-';
}

function PoliticalTradeRows({ rows }: { rows: PoliticalTrade[] }) {
  return (
    <tbody className="divide-y divide-gray-100">
      {rows.map((row, index) => (
        <tr key={row.sourceTradeId ?? `${row.politicianName}-${row.ticker}-${index}`} className="hover:bg-gray-50">
          <td className="px-4 py-3">
            <div className="flex min-w-[180px] items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-700">
                {initials(row.politicianName)}
              </div>
              <div>
                <div className="font-medium text-gray-900">{row.politicianName ?? '-'}</div>
                <div className="mt-1 text-xs text-gray-500">
                  {[row.party, row.chamber, row.state].filter(Boolean).join(' | ') || '-'}
                </div>
              </div>
            </div>
          </td>
          <td className="px-4 py-3">
            <div className="font-mono text-sm font-semibold text-gray-900">{row.ticker ? `$${row.ticker}` : '-'}</div>
            <div className="mt-1 max-w-[240px] truncate text-xs text-gray-500" title={row.issuerName ?? undefined}>
              {row.issuerName ?? '-'}
            </div>
          </td>
          <td className="px-4 py-3 whitespace-nowrap font-mono text-xs">{toDisplayDate(row.tradedDate)}</td>
          <td className="px-4 py-3 whitespace-nowrap font-mono text-xs">{toDisplayDate(row.disclosureDate)}</td>
          <td className="px-4 py-3 text-right">
            <span className="font-mono text-sm">{row.filedAfterDays == null ? '-' : row.filedAfterDays}</span>
            {row.filedAfterDays != null ? <span className="ml-1 text-xs text-gray-500">days</span> : null}
          </td>
          <td className="px-4 py-3 text-xs text-gray-600">{row.owner ?? '-'}</td>
          <td className="px-4 py-3">
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${transactionClass(row.transactionType)}`}>
              {row.transactionType ?? '-'}
            </span>
          </td>
          <td className="px-4 py-3 text-right font-mono">{formatAmountLabel(row)}</td>
          <td className="px-4 py-3 text-right font-mono">{formatCurrency(row.price)}</td>
        </tr>
      ))}
    </tbody>
  );
}

export function PoliticalTradesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filter = React.useMemo(() => parseFilter(searchParams), [searchParams]);
  const { results, loading, error, refresh } = usePoliticalTrades(filter);
  const [exporting, setExporting] = React.useState<PoliticalTradeExportFormat | null>(null);
  const [syncing, setSyncing] = React.useState(false);
  const [syncResult, setSyncResult] = React.useState<PoliticalTradeSyncResponse | null>(null);
  const [syncError, setSyncError] = React.useState<string | null>(null);
  const [syncMaxPages, setSyncMaxPages] = React.useState(25);
  const [forceSync, setForceSync] = React.useState(false);
  const [politicians, setPoliticians] = React.useState<string[]>([]);
  const [showCoverage, setShowCoverage] = React.useState(false);
  const [coverageRefreshKey, setCoverageRefreshKey] = React.useState(0);

  const updateFilter = React.useCallback((patch: Partial<PoliticalTradeFilter>) => {
    setSearchParams(writeFilter({
      ...filter,
      ...patch,
      page: patch.page ?? 0,
    }));
  }, [filter, setSearchParams]);

  const exportResults = React.useCallback(async (format: PoliticalTradeExportFormat) => {
    setExporting(format);
    try {
      await politicalTradesApi.export(filter, format);
    } finally {
      setExporting(null);
    }
  }, [filter]);

  const syncTrades = React.useCallback(async () => {
    setSyncing(true);
    setSyncError(null);
    try {
      const response = await politicalTradesApi.sync({
        assetType: filter.assetType ?? DEFAULT_FILTER.assetType,
        maxPages: syncMaxPages,
        force: forceSync,
      });
      setSyncResult(response);
      await refresh();
      setCoverageRefreshKey((current) => current + 1);
      setPoliticians(await politicalTradesApi.politicians(filter.politician ?? ''));
    } catch (err) {
      setSyncError(err instanceof Error ? err.message : 'Political trade sync failed');
    } finally {
      setSyncing(false);
    }
  }, [filter.assetType, filter.politician, forceSync, refresh, syncMaxPages]);

  React.useEffect(() => {
    let cancelled = false;
    politicalTradesApi.politicians(filter.politician ?? '')
      .then((names) => {
        if (!cancelled) {
          setPoliticians(names);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPoliticians([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [filter.politician]);

  const matchedText = results
    ? `${results.totalElements.toLocaleString()} trade${results.totalElements === 1 ? '' : 's'} matched`
    : 'Cached congressional trade disclosures';
  const latestUpdateDay = latestPoliticalUpdateDay(results?.content, syncResult);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-100">
            <Vote className="h-5 w-5 text-blue-700" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-gray-900">Political Trades</h1>
            <p className="text-sm text-gray-500">
              Cached congressional trading disclosures from public Capitol Trades pages.
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
            <Download className="h-4 w-4" />
            CSV
          </button>
          <button
            onClick={() => void exportResults('JSON')}
            disabled={Boolean(exporting)}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            <FileJson className="h-4 w-4" />
            JSON
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={refresh}
              className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            <span className="text-xs text-gray-500">Updated {latestUpdateDay}</span>
          </div>
        </div>
      </div>

      {error ? (
        <ErrorMessage title="Failed to load political trades" message={error} onRetry={refresh} />
      ) : null}

      {syncError ? (
        <ErrorMessage title="Failed to sync political trades" message={syncError} onRetry={() => void syncTrades()} />
      ) : null}

      {showCoverage && (
        <PoliticalCoverageHeatmap
          onSelectRange={(from, to) => updateFilter({ disclosureDateFrom: from, disclosureDateTo: to })}
          onSync={() => void syncTrades()}
          syncing={syncing}
          refreshKey={String(coverageRefreshKey)}
        />
      )}

      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-gray-500" />
          <h2 className="text-lg font-semibold text-gray-900">Filters</h2>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div>
            <label htmlFor="political-politician" className="mb-2 block text-sm text-gray-600">Politician</label>
            <input
              id="political-politician"
              list="political-politician-options"
              value={filter.politician ?? ''}
              onChange={(event) => updateFilter({ politician: event.target.value || undefined })}
              placeholder="Ex: Gottheimer"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <datalist id="political-politician-options">
              {politicians.map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
          </div>

          <div>
            <label htmlFor="political-ticker" className="mb-2 block text-sm text-gray-600">Ticker</label>
            <input
              id="political-ticker"
              value={filter.ticker ?? ''}
              onChange={(event) => updateFilter({ ticker: event.target.value || undefined })}
              placeholder="Ex: T"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-issuer" className="mb-2 block text-sm text-gray-600">Issuer</label>
            <input
              id="political-issuer"
              value={filter.issuer ?? ''}
              onChange={(event) => updateFilter({ issuer: event.target.value || undefined })}
              placeholder="Ex: AT&T"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-asset" className="mb-2 block text-sm text-gray-600">Asset type</label>
            <select
              id="political-asset"
              value={filter.assetType ?? DEFAULT_FILTER.assetType}
              onChange={(event) => updateFilter({ assetType: event.target.value })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {ASSET_TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="political-type" className="mb-2 block text-sm text-gray-600">Transaction type</label>
            <select
              id="political-type"
              value={filter.transactionType ?? ''}
              onChange={(event) => updateFilter({ transactionType: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {TRANSACTION_OPTIONS.map((option) => <option key={option.value || 'all'} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="political-party" className="mb-2 block text-sm text-gray-600">Party</label>
            <select
              id="political-party"
              value={filter.party ?? ''}
              onChange={(event) => updateFilter({ party: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {PARTY_OPTIONS.map((option) => <option key={option.value || 'all'} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="political-chamber" className="mb-2 block text-sm text-gray-600">Chamber</label>
            <select
              id="political-chamber"
              value={filter.chamber ?? ''}
              onChange={(event) => updateFilter({ chamber: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {CHAMBER_OPTIONS.map((option) => <option key={option.value || 'all'} value={option.value}>{option.label}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="political-state" className="mb-2 block text-sm text-gray-600">State</label>
            <input
              id="political-state"
              value={filter.state ?? ''}
              onChange={(event) => updateFilter({ state: event.target.value || undefined })}
              placeholder="Ex: NJ"
              maxLength={2}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm uppercase focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-traded-from" className="mb-2 block text-sm text-gray-600">Traded from</label>
            <input
              id="political-traded-from"
              type="date"
              value={filter.tradedDateFrom ?? ''}
              onChange={(event) => updateFilter({ tradedDateFrom: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-traded-to" className="mb-2 block text-sm text-gray-600">Traded to</label>
            <input
              id="political-traded-to"
              type="date"
              value={filter.tradedDateTo ?? ''}
              onChange={(event) => updateFilter({ tradedDateTo: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-disclosure-from" className="mb-2 block text-sm text-gray-600">Disclosure from</label>
            <input
              id="political-disclosure-from"
              type="date"
              value={filter.disclosureDateFrom ?? ''}
              onChange={(event) => updateFilter({ disclosureDateFrom: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-disclosure-to" className="mb-2 block text-sm text-gray-600">Disclosure to</label>
            <input
              id="political-disclosure-to"
              type="date"
              value={filter.disclosureDateTo ?? ''}
              onChange={(event) => updateFilter({ disclosureDateTo: event.target.value || undefined })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-min-amount" className="mb-2 block text-sm text-gray-600">Min amount</label>
            <input
              id="political-min-amount"
              type="number"
              min="0"
              value={filter.minAmount ?? ''}
              onChange={(event) => updateFilter({ minAmount: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 15000"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-max-amount" className="mb-2 block text-sm text-gray-600">Max amount</label>
            <input
              id="political-max-amount"
              type="number"
              min="0"
              value={filter.maxAmount ?? ''}
              onChange={(event) => updateFilter({ maxAmount: parsePositiveNumber(event.target.value) })}
              placeholder="Ex: 50000"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="political-sort" className="mb-2 block text-sm text-gray-600">Sort by</label>
            <select
              id="political-sort"
              value={filter.sortBy ?? DEFAULT_FILTER.sortBy}
              onChange={(event) => updateFilter({ sortBy: event.target.value })}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="disclosureDate">Disclosure date</option>
              <option value="tradedDate">Trade date</option>
              <option value="politicianName">Politician</option>
              <option value="ticker">Ticker</option>
              <option value="issuerName">Issuer</option>
              <option value="filedAfterDays">Filed after</option>
              <option value="amount">Amount</option>
              <option value="price">Price</option>
              <option value="transactionType">Type</option>
            </select>
          </div>

          <div>
            <label htmlFor="political-owner" className="mb-2 block text-sm text-gray-600">Owner</label>
            <input
              id="political-owner"
              value={filter.owner ?? ''}
              onChange={(event) => updateFilter({ owner: event.target.value || undefined })}
              placeholder="Ex: Spouse"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        <div className="mt-5 flex flex-col gap-3 border-t border-gray-100 pt-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-[160px_1fr]">
            <div>
              <label htmlFor="political-sync-pages" className="mb-2 block text-sm text-gray-600">Sync pages</label>
              <input
                id="political-sync-pages"
                type="number"
                min="1"
                max="250"
                value={syncMaxPages}
                onChange={(event) => setSyncMaxPages(Number(event.target.value) || 25)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <label className="flex items-center gap-2 self-end pb-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={forceSync}
                onChange={(event) => setForceSync(event.target.checked)}
                className="h-4 w-4 rounded border-gray-300"
              />
              Force backfill
            </label>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            {syncSummary(syncResult) ? (
              <span className="text-sm text-gray-500">{syncSummary(syncResult)}</span>
            ) : null}
            <button
              onClick={() => void syncTrades()}
              disabled={syncing}
              className="inline-flex items-center justify-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
            >
              <Search className={`h-4 w-4 ${syncing ? 'animate-pulse' : ''}`} />
              {syncing ? 'Syncing' : 'Sync Latest'}
            </button>
          </div>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg bg-white shadow-sm">
        <div className="flex flex-col gap-2 border-b border-gray-200 px-5 py-4 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-gray-500" />
              <h2 className="text-lg font-semibold text-gray-900">Trade Results</h2>
            </div>
            <p className="mt-1 text-sm text-gray-500">{matchedText}</p>
          </div>
          <span className="self-start rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">
            {filter.assetType === 'ALL' ? 'All assets' : filter.assetType ?? 'stock'}
          </span>
        </div>

        {loading ? (
          <div className="py-16">
            <LoadingSpinner size="lg" text="Loading political trades..." />
          </div>
        ) : !results || results.content.length === 0 ? (
          <EmptyState
            type="filings"
            title="No political trades found"
            message="Sync the latest rows or adjust the filters."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-3">Politician</th>
                    <th className="px-4 py-3">Ticker / Issuer</th>
                    <th className="px-4 py-3">Date Traded</th>
                    <th className="px-4 py-3">Disclosure Date</th>
                    <th className="px-4 py-3 text-right">Filed After</th>
                    <th className="px-4 py-3">Owner</th>
                    <th className="px-4 py-3">Transaction Type</th>
                    <th className="px-4 py-3 text-right">Amount</th>
                    <th className="px-4 py-3 text-right">Price</th>
                  </tr>
                </thead>
                <PoliticalTradeRows rows={results.content} />
              </table>
            </div>
            <div className="border-t border-gray-200 px-5">
              <Pagination
                page={results.page}
                totalPages={results.totalPages}
                totalElements={results.totalElements}
                size={results.size}
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
