import React from 'react';
import { format } from 'date-fns';
import {
  AlertCircle,
  ArrowRight,
  Building2,
  CalendarDays,
  ExternalLink,
  FileText,
  Landmark,
  LineChart,
  RefreshCw,
  Search,
  Wallet,
} from 'lucide-react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart as RechartsLineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Link, useSearchParams } from 'react-router-dom';
import {
  companiesApi,
  dividendApi,
  type DividendAlerts,
  type DividendComparison,
  type DividendEvents,
  type DividendFilingEvidence,
  type DividendHistory,
  type DividendRating,
  type DividendScreen,
  type DividendScreenRequest,
  type DividendOverview,
} from '../api';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/ui/button';
import {
  eventTypeClasses,
  eventTypeLabel,
  formatCompactCurrency,
  formatCurrency,
  formatDate,
  formatEvidenceTitle,
  formatFiscalYearEnd,
  formatInteger,
  formatMetricValue,
  formatMultiple,
  formatPercent,
  historyTrendClasses,
  historyTrendLabel,
  parseOptionalNumber,
  ratingClasses,
  severityClasses,
} from './dividendViabilityUtils';
import { DetailCard, SnapshotCard, TrendMetricCard } from './dividendViabilityDashboardCards';
import {
  DividendEventsSection,
  DividendHistoryTableSection,
  DividendPeerComparisonSection,
} from './dividendViabilitySections';

const SAMPLE_COMPANIES = ['AAPL', 'MSFT', 'JNJ'] as const;
const HISTORY_METRICS = [
  'dps_declared',
  'eps_diluted',
  'earnings_payout',
  'revenue',
  'free_cash_flow',
  'dividends_paid',
  'fcf_payout',
  'cash_coverage',
  'retained_cash',
  'gross_debt',
  'net_debt_to_ebitda',
  'current_ratio',
  'interest_coverage',
  'fcf_margin',
] as const;
const DEFAULT_COMPARE_METRICS = ['fcf_payout', 'dps_cagr_5y', 'net_debt_to_ebitda'] as const;
const DEFAULT_SCREEN_METRICS = ['fcf_payout', 'dps_cagr_5y', 'current_ratio', 'net_debt_to_ebitda', 'dividend_yield'] as const;
const SCREEN_RATING_OPTIONS: DividendRating[] = ['SAFE', 'STABLE', 'WATCH', 'AT_RISK'];
const SCREEN_PAGE_SIZE = 12;
const SCREEN_CANDIDATE_LIMIT = 40;
const ANNUAL_TABLE_COLUMNS = [
  { id: 'dps_declared', label: 'DPS', formatHint: 'currency' },
  { id: 'eps_diluted', label: 'EPS', formatHint: 'currency' },
  { id: 'earnings_payout', label: 'Earn. payout', formatHint: 'percent' },
  { id: 'free_cash_flow', label: 'FCF', formatHint: 'compact_currency' },
  { id: 'dividends_paid', label: 'Dividends', formatHint: 'compact_currency' },
  { id: 'fcf_payout', label: 'FCF payout', formatHint: 'percent' },
  { id: 'cash_coverage', label: 'Cash cov.', formatHint: 'multiple' },
  { id: 'net_debt_to_ebitda', label: 'ND / EBITDA', formatHint: 'multiple' },
  { id: 'current_ratio', label: 'Current ratio', formatHint: 'multiple' },
  { id: 'interest_coverage', label: 'Interest cov.', formatHint: 'multiple' },
  { id: 'fcf_margin', label: 'FCF margin', formatHint: 'percent' },
] as const;

interface DashboardData {
  overview: DividendOverview;
  history: DividendHistory;
  alerts: DividendAlerts;
  events: DividendEvents;
}

type DashboardState =
  | { status: 'idle' }
  | { status: 'loading'; query: string }
  | { status: 'error'; query: string; message: string }
  | { status: 'success'; query: string; result: DashboardData };

type EvidenceState =
  | { status: 'idle' }
  | { status: 'loading'; accession: string }
  | { status: 'error'; accession: string; message: string }
  | { status: 'success'; accession: string; result: DividendFilingEvidence };

type ComparisonState =
  | { status: 'idle' }
  | { status: 'loading'; peers: string[] }
  | { status: 'error'; peers: string[]; message: string }
  | { status: 'success'; peers: string[]; result: DividendComparison };

type ScreenState =
  | { status: 'idle' }
  | { status: 'loading'; request: DividendScreenRequest }
  | { status: 'error'; message: string }
  | { status: 'success'; request: DividendScreenRequest; result: DividendScreen };

async function resolveDividendQuery(identifier: string): Promise<string> {
  const trimmed = identifier.trim();
  if (!trimmed) {
    throw new Error('Enter a ticker or CIK to run the analysis.');
  }

  if (/^\d+$/.test(trimmed)) {
    return trimmed;
  }

  try {
    const company = await companiesApi.getCompanyByTicker(trimmed.toUpperCase());
    return company.ticker || company.cik;
  } catch {
    const results = await companiesApi.getCompanies({
      searchTerm: trimmed,
      page: 0,
      size: 1,
      sortBy: 'name',
      sortDir: 'asc',
    });
    const match = results.content[0];
    if (!match) {
      throw new Error(`No company matched "${trimmed}".`);
    }
    return match.ticker || match.cik;
  }
}

async function loadDashboard(query: string): Promise<DashboardData> {
  const resolvedQuery = await resolveDividendQuery(query);
  const [overview, history, alerts, events] = await Promise.all([
    dividendApi.getOverview(resolvedQuery),
    dividendApi.getHistory(resolvedQuery, {
      metrics: [...HISTORY_METRICS],
      periods: 'FY',
      years: 15,
    }),
    dividendApi.getAlerts(resolvedQuery, { active: false }),
    dividendApi.getEvents(resolvedQuery),
  ]);

  return {
    overview,
    history,
    alerts,
    events,
  };
}

export function DividendViabilityDashboard() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [inputValue, setInputValue] = React.useState(searchParams.get('company') ?? '');
  const [reloadToken, setReloadToken] = React.useState(0);
  const [evidenceState, setEvidenceState] = React.useState<EvidenceState>({ status: 'idle' });
  const [comparisonState, setComparisonState] = React.useState<ComparisonState>({ status: 'idle' });
  const [compareInput, setCompareInput] = React.useState('');
  const [screenState, setScreenState] = React.useState<ScreenState>({ status: 'idle' });
  const [screenSearchInput, setScreenSearchInput] = React.useState('');
  const [screenSectorInput, setScreenSectorInput] = React.useState('');
  const [screenMaxFcfPayoutInput, setScreenMaxFcfPayoutInput] = React.useState('60');
  const [screenMinCurrentRatioInput, setScreenMinCurrentRatioInput] = React.useState('1.0');
  const [screenSort, setScreenSort] = React.useState('score');
  const [screenDirection, setScreenDirection] = React.useState<'ASC' | 'DESC'>('DESC');
  const [screenRatings, setScreenRatings] = React.useState<DividendRating[]>(['SAFE', 'STABLE']);
  const [state, setState] = React.useState<DashboardState>(
    searchParams.get('company') ? { status: 'loading', query: searchParams.get('company') ?? '' } : { status: 'idle' },
  );

  const activeQuery = (searchParams.get('company') ?? '').trim();

  React.useEffect(() => {
    setInputValue(activeQuery);
  }, [activeQuery]);

  React.useEffect(() => {
    if (!activeQuery) {
      setState({ status: 'idle' });
      setEvidenceState({ status: 'idle' });
      setComparisonState({ status: 'idle' });
      setCompareInput('');
      return;
    }

    let cancelled = false;
    setEvidenceState({ status: 'idle' });
    setComparisonState({ status: 'idle' });
    setCompareInput('');

    async function run() {
      setState({ status: 'loading', query: activeQuery });
      try {
        const result = await loadDashboard(activeQuery);
        if (!cancelled) {
          setState({ status: 'success', query: activeQuery, result });
        }
      } catch (error) {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : 'Failed to build dividend viability analysis.';
          setState({ status: 'error', query: activeQuery, message });
        }
      }
    }

    void run();

    return () => {
      cancelled = true;
    };
  }, [activeQuery, reloadToken]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = inputValue.trim();
    if (!trimmed) {
      setSearchParams({});
      return;
    }
    setSearchParams({ company: trimmed });
  };

  const loadSample = (symbol: string) => setSearchParams({ company: symbol });
  const retry = () => setReloadToken((current) => current + 1);

  const result = state.status === 'success' ? state.result : null;
  const overview = result?.overview ?? null;
  const history = result?.history ?? null;
  const alerts = result?.alerts ?? null;
  const eventsResponse = result?.events ?? null;
  const company = overview?.company ?? null;
  const latestAnnualReport = overview?.evidence.latestAnnualReport ?? null;
  const latestCurrentReport = overview?.evidence.latestCurrentReport ?? null;
  const historyRows = history?.rows ?? [];
  const latestHistoryRow = historyRows.at(-1) ?? null;
  const annualPeriodsAnalyzed = historyRows.length > 0 ? historyRows.length : (overview?.trend.length ?? 0);
  const activeAlerts = alerts?.activeAlerts ?? overview?.alerts ?? [];
  const historicalAlerts = alerts?.historicalAlerts ?? [];
  const dividendEvents = eventsResponse?.events ?? [];
  const evidenceResult = evidenceState.status === 'success' ? evidenceState.result : null;
  const comparisonResult = comparisonState.status === 'success' ? comparisonState.result : null;
  const screenResult = screenState.status === 'success' ? screenState.result : null;
  const warnings = Array.from(new Set([
    ...(overview?.warnings ?? []),
    ...(history?.warnings ?? []),
    ...(alerts?.warnings ?? []),
    ...(eventsResponse?.warnings ?? []),
  ]));
  const dpsHistorySeries = history?.series.find((series) => series.metric === 'dps_declared') ?? null;
  const payoutHistorySeries = history?.series.find((series) => series.metric === 'fcf_payout') ?? null;
  const earningsPayoutSeries = history?.series.find((series) => series.metric === 'earnings_payout') ?? null;
  const revenueSeries = history?.series.find((series) => series.metric === 'revenue') ?? null;
  const fcfMarginSeries = history?.series.find((series) => series.metric === 'fcf_margin') ?? null;
  const interestCoverageSeries = history?.series.find((series) => series.metric === 'interest_coverage') ?? null;
  const cashCoverageSeries = history?.series.find((series) => series.metric === 'cash_coverage') ?? null;
  const chartData = historyRows.length > 0
    ? historyRows.map((row) => ({
        label: row.periodEnd
          ? format(new Date(row.periodEnd), 'yyyy')
          : (row.filingDate ? format(new Date(row.filingDate), 'yyyy') : row.accessionNumber),
        dividends: row.metrics.dps_declared ?? null,
        earnings: row.metrics.eps_diluted ?? null,
      }))
    : (overview?.trend.map((point) => ({
        label: point.periodEnd
          ? format(new Date(point.periodEnd), 'yyyy')
          : (point.filingDate ? format(new Date(point.filingDate), 'yyyy') : point.accessionNumber),
        dividends: point.dividendsPerShare,
        earnings: point.earningsPerShare,
      })) ?? []);
  const balanceWarning = warnings.find((warning) =>
    warning.includes('quarterly balance-sheet filing') || warning.includes('balance-sheet filing')) ?? null;

  const openEvidence = async (accessionNumber?: string | null) => {
    if (!company?.cik || !accessionNumber) {
      return;
    }

    setEvidenceState({ status: 'loading', accession: accessionNumber });
    try {
      const evidence = await dividendApi.getEvidence(company.cik, accessionNumber);
      setEvidenceState({ status: 'success', accession: accessionNumber, result: evidence });
    } catch (error) {
      setEvidenceState({
        status: 'error',
        accession: accessionNumber,
        message: error instanceof Error ? error.message : 'Failed to load filing evidence.',
      });
    }
  };

  const runComparison = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!company) {
      return;
    }

    const peers = compareInput
      .split(',')
      .map((token) => token.trim().toUpperCase())
      .filter(Boolean);
    if (peers.length === 0) {
      setComparisonState({
        status: 'error',
        peers: [],
        message: 'Enter at least one peer ticker or CIK to compare.',
      });
      return;
    }

    const baseIdentifier = (company.ticker || company.cik).toUpperCase();
    const requestedTickers = Array.from(new Set([baseIdentifier, ...peers]));
    setComparisonState({ status: 'loading', peers: requestedTickers });

    try {
      const comparison = await dividendApi.compare(requestedTickers, {
        metrics: [...DEFAULT_COMPARE_METRICS],
      });
      setComparisonState({ status: 'success', peers: requestedTickers, result: comparison });
    } catch (error) {
      setComparisonState({
        status: 'error',
        peers: requestedTickers,
        message: error instanceof Error ? error.message : 'Failed to compare the selected peers.',
      });
    }
  };

  const toggleScreenRating = (rating: DividendRating) => {
    setScreenRatings((current) =>
      current.includes(rating)
        ? current.filter((item) => item !== rating)
        : [...current, rating],
    );
  };

  const runScreen = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const metricFilters: NonNullable<NonNullable<DividendScreenRequest['filters']>['metrics']> = {};
    const maxFcfPayout = parseOptionalNumber(screenMaxFcfPayoutInput);
    const minCurrentRatio = parseOptionalNumber(screenMinCurrentRatioInput);
    const sectors = screenSectorInput
      .split(',')
      .map((token) => token.trim())
      .filter(Boolean);

    if (maxFcfPayout != null) {
      metricFilters.fcf_payout = { max: maxFcfPayout };
    }
    if (minCurrentRatio != null) {
      metricFilters.current_ratio = { min: minCurrentRatio };
    }

    const filters: NonNullable<DividendScreenRequest['filters']> = {};
    if (Object.keys(metricFilters).length > 0) {
      filters.metrics = metricFilters;
    }
    if (screenRatings.length > 0) {
      filters.viabilityRatings = screenRatings;
    }
    if (sectors.length > 0) {
      filters.sectors = sectors;
    }

    const request: DividendScreenRequest = {
      searchTerm: screenSearchInput.trim() || undefined,
      filters: Object.keys(filters).length > 0 ? filters : undefined,
      metrics: [...DEFAULT_SCREEN_METRICS],
      sort: screenSort,
      direction: screenDirection,
      page: 0,
      size: SCREEN_PAGE_SIZE,
      candidateLimit: SCREEN_CANDIDATE_LIMIT,
    };

    setScreenState({ status: 'loading', request });
    try {
      const result = await dividendApi.screen(request);
      setScreenState({ status: 'success', request, result });
    } catch (error) {
      setScreenState({
        status: 'error',
        message: error instanceof Error ? error.message : 'Failed to run the dividend screener.',
      });
    }
  };

  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-[34px] border border-slate-900/10 bg-[linear-gradient(140deg,#08111f_0%,#12325f_48%,#0f766e_100%)] text-white shadow-xl">
        <div className="px-6 py-7 sm:px-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <p className="text-[11px] font-semibold uppercase tracking-[0.35em] text-emerald-200/80">
                Analysis / Dividend Viability
              </p>
              <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">
                Can this company keep paying and growing its dividend for the next 10+ years?
              </h1>
              <p className="mt-3 text-sm leading-6 text-slate-200 sm:text-base">
                This backend-powered view scores dividend durability from SEC company facts, recent XBRL filings,
                balance-sheet pressure, and stored market-price context.
              </p>
            </div>

            <div className="rounded-[28px] border border-white/15 bg-white/8 p-4 backdrop-blur">
              <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row">
                <label className="sr-only" htmlFor="dividend-viability-company">Company ticker or CIK</label>
                <div className="flex min-w-[260px] items-center gap-2 rounded-2xl border border-white/15 bg-slate-950/40 px-3 py-2">
                  <Search className="h-4 w-4 text-slate-300" />
                  <input
                    id="dividend-viability-company"
                    value={inputValue}
                    onChange={(event) => setInputValue(event.target.value)}
                    placeholder="AAPL or 0000320193"
                    className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-400"
                  />
                </div>
                <Button type="submit" className="rounded-2xl bg-emerald-500 text-slate-950 hover:bg-emerald-400">
                  {state.status === 'loading' ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                  Analyze
                </Button>
              </form>
              <div className="mt-3 flex flex-wrap gap-2">
                {SAMPLE_COMPANIES.map((symbol) => (
                  <button
                    key={symbol}
                    type="button"
                    onClick={() => loadSample(symbol)}
                    className="rounded-full border border-white/15 bg-white/10 px-3 py-1 text-xs font-medium text-slate-100 transition hover:bg-white/15"
                  >
                    {symbol}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Dividend screener</p>
            <h2 className="mt-2 text-xl font-semibold text-slate-950">Filter the covered company universe by payout, liquidity, and rating</h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              This view uses the new `/api/dividend/screen` endpoint to run a bounded first-page screen across the
              local company universe or a narrowed search subset. Use it to find candidates, then jump straight into
              the full company analysis.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {DEFAULT_SCREEN_METRICS.map((metric) => (
              <span key={metric} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium uppercase tracking-[0.12em] text-slate-600">
                {metric.replaceAll('_', ' ')}
              </span>
            ))}
          </div>
        </div>

        <form onSubmit={runScreen} className="mt-5 space-y-4">
          <div className="grid gap-4 xl:grid-cols-[1.4fr,1fr,0.9fr,0.9fr]">
            <div className="space-y-2">
              <label htmlFor="dividend-screen-search" className="text-sm font-medium text-slate-700">
                Screen search
              </label>
              <div className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2">
                <Search className="h-4 w-4 text-slate-400" />
                <input
                  id="dividend-screen-search"
                  value={screenSearchInput}
                  onChange={(event) => setScreenSearchInput(event.target.value)}
                  placeholder="technology, healthcare, AAPL"
                  className="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
                />
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor="dividend-screen-sector" className="text-sm font-medium text-slate-700">
                Sector filter
              </label>
              <input
                id="dividend-screen-sector"
                value={screenSectorInput}
                onChange={(event) => setScreenSectorInput(event.target.value)}
                placeholder="Technology, Healthcare"
                className="h-10 w-full rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none placeholder:text-slate-400"
              />
            </div>

            <div className="space-y-2">
              <label htmlFor="dividend-screen-max-fcf" className="text-sm font-medium text-slate-700">
                Max FCF payout (%)
              </label>
              <input
                id="dividend-screen-max-fcf"
                inputMode="decimal"
                value={screenMaxFcfPayoutInput}
                onChange={(event) => setScreenMaxFcfPayoutInput(event.target.value)}
                placeholder="60"
                className="h-10 w-full rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none placeholder:text-slate-400"
              />
            </div>

            <div className="space-y-2">
              <label htmlFor="dividend-screen-min-current-ratio" className="text-sm font-medium text-slate-700">
                Min current ratio
              </label>
              <input
                id="dividend-screen-min-current-ratio"
                inputMode="decimal"
                value={screenMinCurrentRatioInput}
                onChange={(event) => setScreenMinCurrentRatioInput(event.target.value)}
                placeholder="1.0"
                className="h-10 w-full rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none placeholder:text-slate-400"
              />
            </div>
          </div>

          <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
            <div className="space-y-2">
              <p className="text-sm font-medium text-slate-700">Viability filter</p>
              <div className="flex flex-wrap gap-2">
                {SCREEN_RATING_OPTIONS.map((rating) => {
                  const selected = screenRatings.includes(rating);
                  return (
                    <button
                      key={rating}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => toggleScreenRating(rating)}
                      className={`rounded-full border px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.14em] transition ${
                        selected
                          ? `${ratingClasses(rating)} border-transparent`
                          : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'
                      }`}
                    >
                      {rating}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="space-y-2">
                <label htmlFor="dividend-screen-sort" className="text-sm font-medium text-slate-700">
                  Sort by
                </label>
                <select
                  id="dividend-screen-sort"
                  value={screenSort}
                  onChange={(event) => setScreenSort(event.target.value)}
                  className="h-10 min-w-[180px] rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none"
                >
                  <option value="score">Viability score</option>
                  <option value="dps_cagr_5y">Dividend CAGR (5Y)</option>
                  <option value="fcf_payout">FCF payout ratio</option>
                  <option value="current_ratio">Current ratio</option>
                  <option value="net_debt_to_ebitda">Net debt / EBITDA</option>
                  <option value="dividend_yield">Dividend yield</option>
                  <option value="ticker">Ticker</option>
                  <option value="name">Company name</option>
                </select>
              </div>

              <div className="space-y-2">
                <label htmlFor="dividend-screen-direction" className="text-sm font-medium text-slate-700">
                  Direction
                </label>
                <select
                  id="dividend-screen-direction"
                  value={screenDirection}
                  onChange={(event) => setScreenDirection(event.target.value as 'ASC' | 'DESC')}
                  className="h-10 min-w-[140px] rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none"
                >
                  <option value="DESC">High to low</option>
                  <option value="ASC">Low to high</option>
                </select>
              </div>

              <Button type="submit" variant="outline" className="rounded-2xl">
                {screenState.status === 'loading' ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                Run screener
              </Button>
            </div>
          </div>
        </form>

        <div className="mt-5">
          {screenState.status === 'idle' && (
            <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
              Run the screener to rank the first {SCREEN_CANDIDATE_LIMIT} candidates and inspect the top {SCREEN_PAGE_SIZE}
              matches from the current filters.
            </div>
          )}

          {screenState.status === 'loading' && (
            <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-8">
              <LoadingSpinner
                size="sm"
                text={`Screening ${screenState.request.searchTerm || 'the covered universe'}...`}
              />
            </div>
          )}

          {screenState.status === 'error' && (
            <div className="rounded-3xl border border-rose-200 bg-rose-50 px-4 py-4 text-sm text-rose-700">
              {screenState.message}
            </div>
          )}

          {screenState.status === 'success' && screenResult && (
            <div className="space-y-4">
              {screenResult.warnings.length > 0 && (
                <div className="rounded-3xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
                  {screenResult.warnings.map((warning) => (
                    <p key={warning}>{warning}</p>
                  ))}
                </div>
              )}

              <div className="flex flex-wrap gap-2">
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                  {screenResult.results.totalElements} match{screenResult.results.totalElements === 1 ? '' : 'es'}
                </span>
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                  {screenResult.candidatesEvaluated} candidate{screenResult.candidatesEvaluated === 1 ? '' : 's'} evaluated
                </span>
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                  Showing {screenResult.results.content.length} on page {screenResult.results.page + 1}
                </span>
              </div>

              {screenResult.results.content.length === 0 ? (
                <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                  No companies matched the current screen. Relax the payout or liquidity thresholds and try again.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-3xl border border-slate-200">
                  <table className="min-w-full divide-y divide-slate-200">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Company</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Sector</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Rating</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Score</th>
                        {screenResult.metrics.map((metric) => (
                          <th key={metric.id} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                            {metric.label}
                          </th>
                        ))}
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 bg-white">
                      {screenResult.results.content.map((row) => {
                        const identifier = row.company.ticker || row.company.cik || '';
                        return (
                          <tr key={`${row.company.cik}-${identifier}`}>
                            <td className="px-4 py-4 align-top">
                              <div>
                                <p className="text-sm font-semibold text-slate-950">{row.company.ticker || row.company.cik}</p>
                                <p className="mt-1 text-xs text-slate-500">{row.company.name || row.company.cik}</p>
                                {row.warnings.length > 0 && (
                                  <p className="mt-2 text-xs text-amber-700">{row.warnings[0]}</p>
                                )}
                              </div>
                            </td>
                            <td className="px-4 py-4 align-top text-sm text-slate-700">{row.company.sector || '-'}</td>
                            <td className="px-4 py-4 align-top">
                              <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${ratingClasses(row.viability.rating)}`}>
                                {row.viability.rating}
                              </span>
                            </td>
                            <td className="px-4 py-4 align-top text-sm font-semibold text-slate-950">{row.viability.score}/100</td>
                            {screenResult.metrics.map((metric) => (
                              <td key={`${row.company.cik}-${metric.id}`} className="px-4 py-4 align-top text-sm text-slate-700">
                                {formatMetricValue(row.values[metric.id], metric)}
                              </td>
                            ))}
                            <td className="px-4 py-4 align-top">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={!identifier}
                                onClick={() => identifier && setSearchParams({ company: identifier })}
                                className="rounded-full"
                              >
                                Analyze
                              </Button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </section>

      {state.status === 'idle' && (
        <section className="grid gap-4 lg:grid-cols-3">
          <div className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">What it uses</p>
            <p className="mt-3 text-lg font-semibold text-slate-950">Dedicated backend overview, history, alerts, and events APIs</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              The dashboard now reads specialized backend endpoints for the scorecard, annual metric history, alert
              timeline, and filing-text event extraction, all built from SEC company facts, recent XBRL filings, and
              stored market data when available.
            </p>
          </div>
          <div className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">What you get</p>
            <p className="mt-3 text-lg font-semibold text-slate-950">Score, streaks, and pressure points</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              This slice focuses on dividend per share, payout pressure, uninterrupted years, consecutive raises,
              leverage, current ratio, interest coverage, and evidence links.
            </p>
          </div>
          <div className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Current scope</p>
            <p className="mt-3 text-lg font-semibold text-slate-950">Overview, history, alerts, events, evidence, comparison, and screening</p>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              This version renders off dedicated backend endpoints for overview, annual history, alert feeds, extracted
              dividend events, filing evidence previews, peer comparison, and a bounded dividend screener.
            </p>
          </div>
        </section>
      )}

      {state.status === 'loading' && (
        <section className="rounded-[28px] border border-slate-200 bg-white p-10 shadow-sm">
          <LoadingSpinner size="lg" text={`Building dividend viability view for ${state.query}...`} />
        </section>
      )}

      {state.status === 'error' && (
        <ErrorMessage
          title="Dividend viability analysis failed"
          message={state.message}
          onRetry={retry}
        />
      )}

      {state.status === 'success' && company && overview && (
        <>
          <section className="grid gap-5 xl:grid-cols-[1.4fr,0.9fr]">
            <div className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="flex items-center gap-3 text-slate-950">
                    <div className="rounded-2xl bg-slate-100 p-3">
                      <Building2 className="h-6 w-6 text-slate-700" />
                    </div>
                    <div>
                      <h2 className="text-2xl font-semibold tracking-tight">
                        {company.name || company.ticker || company.cik}
                      </h2>
                      <p className="mt-1 text-sm text-slate-500">
                        {company.ticker || 'Ticker unavailable'} | CIK {company.cik}
                      </p>
                    </div>
                  </div>
                  <div className="mt-5 flex flex-wrap gap-2">
                    <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-700">
                      FY End: {formatFiscalYearEnd(company.fiscalYearEnd)}
                    </div>
                    <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-700">
                      Latest annual filing: {formatDate(latestAnnualReport?.filingDate)}
                    </div>
                    <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-700">
                      Annual periods analyzed: {formatInteger(annualPeriodsAnalyzed)}
                    </div>
                  </div>
                </div>

                <div className="min-w-[240px] rounded-[28px] border border-slate-200 bg-slate-50 p-5">
                  <div className={`inline-flex rounded-full px-3 py-1 text-sm font-semibold ${ratingClasses(overview.viability.rating)}`}>
                    {overview.viability.rating}
                  </div>
                  <p className="mt-4 text-4xl font-semibold text-slate-950">
                    {overview.viability.score}<span className="text-xl text-slate-500">/100</span>
                  </p>
                  <p className="mt-2 text-sm text-slate-500">
                    {activeAlerts.length === 0
                      ? 'No active pressure signals from the current ruleset.'
                      : `${activeAlerts.length} active alert${activeAlerts.length === 1 ? '' : 's'} need review.`}
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
              <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Source stack</p>
              <div className="mt-5 space-y-4">
                <div className="flex items-start gap-3">
                  <div className="rounded-2xl bg-emerald-50 p-2 text-emerald-600">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-medium text-slate-950">Annual trend backbone</p>
                    <p className="text-sm text-slate-500">
                      {annualPeriodsAnalyzed} annual period{annualPeriodsAnalyzed === 1 ? '' : 's'} are available from the dedicated history endpoint.
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="rounded-2xl bg-sky-50 p-2 text-sky-600">
                    <CalendarDays className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-medium text-slate-950">Liquidity and leverage base</p>
                    <p className="text-sm text-slate-500">
                      {balanceWarning
                        ? balanceWarning
                        : 'Liquidity and leverage metrics use the most recent balance-sheet filing resolved by the backend.'}
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="rounded-2xl bg-amber-50 p-2 text-amber-600">
                    <Wallet className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-medium text-slate-950">Market overlay</p>
                    <p className="text-sm text-slate-500">
                      {overview.referencePrice != null
                        ? `Dividend yield estimate uses the stored reference price of ${formatCurrency(overview.referencePrice)}.`
                        : 'Dividend yield is unavailable because stored market data could not be loaded.'}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </section>

          {warnings.length > 0 && (
            <section className="rounded-[28px] border border-amber-200 bg-amber-50 p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <AlertCircle className="mt-0.5 h-5 w-5 text-amber-600" />
                <div>
                  <h2 className="font-semibold text-amber-900">Coverage notes</h2>
                  <div className="mt-2 space-y-1 text-sm text-amber-800">
                    {warnings.map((warning) => (
                      <p key={warning}>{warning}</p>
                    ))}
                  </div>
                </div>
              </div>
            </section>
          )}

          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <SnapshotCard
              label="Dividend / Share"
              value={formatCurrency(overview.snapshot.dpsLatest)}
              note={latestHistoryRow?.periodEnd ? `Latest annual period ${formatDate(latestHistoryRow.periodEnd)}` : undefined}
            />
            <SnapshotCard label="Dividend CAGR (5Y)" value={formatPercent(overview.snapshot.dpsCagr5y)} />
            <SnapshotCard label="FCF Payout Ratio" value={formatPercent(overview.snapshot.fcfPayoutRatio)} />
            <SnapshotCard label="Uninterrupted Years" value={formatInteger(overview.snapshot.uninterruptedYears)} />
            <SnapshotCard label="Consecutive Raises" value={formatInteger(overview.snapshot.consecutiveRaises)} />
            <SnapshotCard label="Net Debt / EBITDA" value={formatMultiple(overview.snapshot.netDebtToEbitda)} />
            <SnapshotCard label="Interest Coverage" value={formatMultiple(overview.snapshot.interestCoverage)} />
            <SnapshotCard label="Current Ratio" value={formatMultiple(overview.snapshot.currentRatio)} />
            <SnapshotCard label="FCF Margin" value={formatPercent(overview.snapshot.fcfMargin)} />
            <SnapshotCard
              label="Dividend Yield"
              value={formatPercent(overview.snapshot.dividendYield)}
              note={overview.referencePrice != null ? `Reference price ${formatCurrency(overview.referencePrice)}` : undefined}
            />
          </section>

          <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Dividend timeline</p>
                <h2 className="mt-2 text-xl font-semibold text-slate-950">Dividend-per-share versus earnings-per-share</h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                  This chart now comes from the dedicated dividend history API. Flat or shrinking DPS against weaker
                  EPS and rising payout ratios is a leading pressure signal for long-term payout durability.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                  {chartData.length} annual point{chartData.length === 1 ? '' : 's'}
                </div>
                {dpsHistorySeries && (
                  <div className={`rounded-full border px-3 py-1 text-sm font-medium ${historyTrendClasses(dpsHistorySeries.trend)}`}>
                    DPS trend: {historyTrendLabel(dpsHistorySeries.trend)}
                  </div>
                )}
                {payoutHistorySeries?.latestValue != null && (
                  <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                    Latest FCF payout: {formatPercent(payoutHistorySeries.latestValue)}
                  </div>
                )}
              </div>
            </div>
            <div className="mt-6 h-[320px]">
              {chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsLineChart data={chartData} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
                    <CartesianGrid stroke="#e2e8f0" strokeDasharray="3 3" />
                    <XAxis dataKey="label" tick={{ fill: '#475569', fontSize: 12 }} />
                    <YAxis tickFormatter={(value) => `$${value}`} tick={{ fill: '#475569', fontSize: 12 }} />
                    <Tooltip
                      formatter={(value, name) => [
                        formatCurrency(typeof value === 'number' ? value : null),
                        name === 'dividends' ? 'Dividend / Share' : 'EPS Diluted',
                      ]}
                      labelFormatter={(label) => `Period ${label}`}
                    />
                    <Legend />
                    <Line type="monotone" dataKey="dividends" stroke="#0f766e" strokeWidth={3} dot={{ r: 4, strokeWidth: 0 }} name="dividends" connectNulls />
                    <Line type="monotone" dataKey="earnings" stroke="#1d4ed8" strokeWidth={2.5} dot={{ r: 3, strokeWidth: 0 }} name="earnings" connectNulls />
                  </RechartsLineChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex h-full items-center justify-center rounded-3xl border border-dashed border-slate-300 bg-slate-50 text-sm text-slate-500">
                  Annual dividend trend data is not available yet.
                </div>
              )}
            </div>
            {(dpsHistorySeries || earningsPayoutSeries) && (
              <div className="mt-5 grid gap-3 md:grid-cols-2">
                {dpsHistorySeries && (
                  <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">History API</p>
                    <p className="mt-2 text-sm font-semibold text-slate-950">{dpsHistorySeries.label}</p>
                    <p className="mt-1 text-sm text-slate-500">
                      Latest {formatCurrency(dpsHistorySeries.latestValue)} · CAGR {formatPercent(dpsHistorySeries.cagr)}
                    </p>
                  </div>
                )}
                {earningsPayoutSeries && (
                  <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">Coverage trend</p>
                    <p className="mt-2 text-sm font-semibold text-slate-950">{earningsPayoutSeries.label}</p>
                    <p className="mt-1 text-sm text-slate-500">
                      Latest {formatPercent(earningsPayoutSeries.latestValue)} · Trend {historyTrendLabel(earningsPayoutSeries.trend)}
                    </p>
                  </div>
                )}
              </div>
            )}
          </section>

          <section className="grid gap-5 xl:grid-cols-3">
            <DetailCard
              title="Coverage Panel"
              icon={Wallet}
              rows={[
                { label: 'Revenue', value: formatCompactCurrency(overview.coverage.revenue) },
                { label: 'Operating Cash Flow', value: formatCompactCurrency(overview.coverage.operatingCashFlow) },
                { label: 'Capital Expenditures', value: formatCompactCurrency(overview.coverage.capitalExpenditures) },
                { label: 'Free Cash Flow', value: formatCompactCurrency(overview.coverage.freeCashFlow) },
                { label: 'Dividends Paid', value: formatCompactCurrency(overview.coverage.dividendsPaid) },
                { label: 'Cash Coverage', value: formatMultiple(overview.coverage.cashCoverage) },
                { label: 'Retained Cash', value: formatCompactCurrency(overview.coverage.retainedCash) },
              ]}
            />
            <DetailCard
              title="Balance Sheet Panel"
              icon={Landmark}
              rows={[
                { label: 'Cash', value: formatCompactCurrency(overview.balance.cash) },
                { label: 'Gross Debt', value: formatCompactCurrency(overview.balance.grossDebt) },
                { label: 'Net Debt', value: formatCompactCurrency(overview.balance.netDebt) },
                { label: 'EBITDA Proxy', value: formatCompactCurrency(overview.balance.ebitdaProxy), note: 'Operating income plus depreciation/amortization proxy.' },
                { label: 'Net Debt / EBITDA', value: formatMultiple(overview.balance.netDebtToEbitda) },
                { label: 'Current Ratio', value: formatMultiple(overview.balance.currentRatio) },
                { label: 'Interest Coverage', value: formatMultiple(overview.balance.interestCoverage) },
              ]}
            />
            <DetailCard
              title="Trend And Evidence"
              icon={LineChart}
              rows={[
                { label: 'Dividend Yield', value: formatPercent(overview.snapshot.dividendYield) },
                { label: '5Y Dividend CAGR', value: formatPercent(overview.snapshot.dpsCagr5y) },
                { label: 'Uninterrupted Years', value: formatInteger(overview.snapshot.uninterruptedYears) },
                { label: 'Consecutive Raises', value: formatInteger(overview.snapshot.consecutiveRaises) },
                { label: 'Latest Annual Report', value: latestAnnualReport?.formType ?? '-', note: formatDate(latestAnnualReport?.filingDate) },
                { label: 'Latest 8-K', value: latestCurrentReport?.formType ?? '-', note: formatDate(latestCurrentReport?.filingDate) },
              ]}
            />
          </section>

          <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Profitability and durability</p>
                <h2 className="mt-2 text-xl font-semibold text-slate-950">Growth, margins, and coverage staying power</h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                  This section extends the history API beyond dividend payout ratios and shows whether revenue, free
                  cash generation, and interest coverage are stable enough to support the dividend path.
                </p>
              </div>
              <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
                {historyRows.length} annual row{historyRows.length === 1 ? '' : 's'}
              </div>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <TrendMetricCard
                eyebrow="Scale"
                title="Revenue trend"
                value={formatCompactCurrency(revenueSeries?.latestValue ?? overview.coverage.revenue)}
                note={
                  revenueSeries
                    ? `${historyTrendLabel(revenueSeries.trend)}${revenueSeries.cagr != null ? ` · CAGR ${formatPercent(revenueSeries.cagr)}` : ''}`
                    : 'Latest annual filing value'
                }
                accentClassName={historyTrendClasses(revenueSeries?.trend ?? 'INSUFFICIENT_DATA')}
              />
              <TrendMetricCard
                eyebrow="Profitability"
                title="Free cash flow margin"
                value={formatPercent(fcfMarginSeries?.latestValue ?? overview.snapshot.fcfMargin)}
                note={
                  fcfMarginSeries
                    ? `${historyTrendLabel(fcfMarginSeries.trend)}${fcfMarginSeries.cagr != null ? ` · CAGR ${formatPercent(fcfMarginSeries.cagr)}` : ''}`
                    : 'Snapshot metric'
                }
                accentClassName={historyTrendClasses(fcfMarginSeries?.trend ?? 'INSUFFICIENT_DATA')}
              />
              <TrendMetricCard
                eyebrow="Debt service"
                title="Interest coverage"
                value={formatMultiple(interestCoverageSeries?.latestValue ?? overview.balance.interestCoverage)}
                note={
                  interestCoverageSeries
                    ? `${historyTrendLabel(interestCoverageSeries.trend)}${interestCoverageSeries.latestValue != null ? ` · latest ${formatMultiple(interestCoverageSeries.latestValue)}` : ''}`
                    : 'Snapshot metric'
                }
                accentClassName={historyTrendClasses(interestCoverageSeries?.trend ?? 'INSUFFICIENT_DATA')}
              />
              <TrendMetricCard
                eyebrow="Dividend headroom"
                title="Cash coverage"
                value={formatMultiple(cashCoverageSeries?.latestValue ?? overview.coverage.cashCoverage)}
                note={
                  cashCoverageSeries
                    ? `${historyTrendLabel(cashCoverageSeries.trend)}${cashCoverageSeries.latestValue != null ? ` · retained ${formatCompactCurrency(latestHistoryRow?.metrics.retained_cash ?? null)}` : ''}`
                    : 'Free cash flow / dividends paid'
                }
                accentClassName={historyTrendClasses(cashCoverageSeries?.trend ?? 'INSUFFICIENT_DATA')}
              />
            </div>
          </section>

          <section className="grid gap-5 xl:grid-cols-[1.2fr,0.8fr]">
            <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-3">
                <div className="rounded-2xl bg-slate-100 p-2 text-slate-700">
                  <AlertCircle className="h-5 w-5" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-slate-950">Alerts</h2>
                  <p className="text-sm text-slate-500">Current rules focus on dividend cuts, payout strain, liquidity, and debt pressure.</p>
                </div>
              </div>
              <div className="mt-5 space-y-3">
                {activeAlerts.length === 0 ? (
                  <div className="rounded-3xl border border-emerald-200 bg-emerald-50 px-4 py-4 text-sm text-emerald-800">
                    No active alerts were raised by the current dividend viability rules.
                  </div>
                ) : activeAlerts.map((alert) => (
                  <div key={alert.id} className={`rounded-3xl border px-4 py-4 ${severityClasses(alert.severity)}`}>
                    <p className="text-sm font-semibold">{alert.title}</p>
                    <p className="mt-1 text-sm leading-6">{alert.description}</p>
                  </div>
                ))}
              </div>

              <div className="mt-6 border-t border-slate-200 pt-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">Alert history</p>
                    <p className="mt-1 text-sm text-slate-500">Historical alert events now come from `/api/dividend/.../alerts`.</p>
                  </div>
                  <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-600">
                    {historicalAlerts.length} event{historicalAlerts.length === 1 ? '' : 's'}
                  </div>
                </div>
                <div className="mt-4 space-y-3">
                  {historicalAlerts.length === 0 ? (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                      No alert history is available yet for this company.
                    </div>
                  ) : historicalAlerts.slice(0, 5).map((event) => (
                    <div key={`${event.id}-${event.periodEnd ?? event.filingDate ?? event.accessionNumber ?? 'event'}`} className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-sm font-semibold text-slate-950">{event.title}</p>
                          <p className="mt-1 text-sm leading-6 text-slate-600">{event.description}</p>
                          <p className="mt-2 text-xs text-slate-500">
                            {event.periodEnd ? `Period ${formatDate(event.periodEnd)}` : 'Historical event'}
                            {event.filingDate ? ` · filed ${formatDate(event.filingDate)}` : ''}
                          </p>
                        </div>
                        <div className="flex flex-col items-end gap-2">
                          <span className={`rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] ${severityClasses(event.severity)}`}>
                            {event.severity}
                          </span>
                          <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] ${event.active ? 'bg-rose-100 text-rose-700' : 'bg-slate-200 text-slate-700'}`}>
                            {event.active ? 'Active' : 'Resolved'}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </section>

            <div className="space-y-5">
              <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-center gap-3">
                  <div className="rounded-2xl bg-slate-100 p-2 text-slate-700">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold text-slate-950">Source Filings</h2>
                    <p className="text-sm text-slate-500">Open the SEC filing or load an inline evidence preview.</p>
                  </div>
                </div>
                <div className="mt-5 space-y-3">
                  {latestAnnualReport && (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                      <div className="flex flex-col gap-3">
                        <div>
                          <p className="text-sm font-semibold text-slate-950">Latest annual report</p>
                          <p className="mt-1 text-sm text-slate-500">
                            {latestAnnualReport.formType ?? 'Filing'} filed {formatDate(latestAnnualReport.filingDate)}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {latestAnnualReport.accessionNumber && (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => openEvidence(latestAnnualReport.accessionNumber)}
                              className="rounded-full"
                            >
                              View evidence
                            </Button>
                          )}
                          {latestAnnualReport.url && (
                            <a
                              href={latestAnnualReport.url}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-100"
                            >
                              Open filing
                              <ExternalLink className="h-4 w-4" />
                            </a>
                          )}
                        </div>
                      </div>
                    </div>
                  )}
                  {latestCurrentReport && (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                      <div className="flex flex-col gap-3">
                        <div>
                          <p className="text-sm font-semibold text-slate-950">Latest 8-K filing</p>
                          <p className="mt-1 text-sm text-slate-500">
                            {latestCurrentReport.formType ?? 'Filing'} filed {formatDate(latestCurrentReport.filingDate)}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {latestCurrentReport.accessionNumber && (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => openEvidence(latestCurrentReport.accessionNumber)}
                              className="rounded-full"
                            >
                              View evidence
                            </Button>
                          )}
                          {latestCurrentReport.url && (
                            <a
                              href={latestCurrentReport.url}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-100"
                            >
                              Open filing
                              <ExternalLink className="h-4 w-4" />
                            </a>
                          )}
                        </div>
                      </div>
                    </div>
                  )}
                </div>

                <div className="mt-5">
                  <Button asChild variant="outline" className="w-full justify-between rounded-2xl">
                    <Link to={`/companies/${company.cik}/fundamentals`}>
                      Open company fundamentals
                      <ArrowRight className="h-4 w-4" />
                    </Link>
                  </Button>
                </div>
              </section>

              <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-center gap-3">
                  <div className="rounded-2xl bg-slate-100 p-2 text-slate-700">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold text-slate-950">Filing Evidence Viewer</h2>
                    <p className="text-sm text-slate-500">Inspect extracted highlights and cleaned filing text for one accession.</p>
                  </div>
                </div>

                <div className="mt-5">
                  {evidenceState.status === 'idle' && (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                      Select View evidence from a source filing or extracted event to inspect the filing text inline.
                    </div>
                  )}

                  {evidenceState.status === 'loading' && (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-8">
                      <LoadingSpinner size="sm" text={`Loading filing evidence for ${evidenceState.accession}...`} />
                    </div>
                  )}

                  {evidenceState.status === 'error' && (
                    <div className="rounded-3xl border border-rose-200 bg-rose-50 px-4 py-4 text-sm text-rose-700">
                      {evidenceState.message}
                    </div>
                  )}

                  {evidenceState.status === 'success' && evidenceResult && (
                    <div className="space-y-4">
                      <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                        <div className="flex flex-col gap-3">
                          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                            <div>
                              <p className="text-sm font-semibold text-slate-950">{formatEvidenceTitle(evidenceResult.filing)}</p>
                              <p className="mt-1 text-xs text-slate-500">
                                Accession {evidenceResult.filing?.accessionNumber ?? evidenceState.accession}
                              </p>
                            </div>
                            {evidenceResult.filing?.url && (
                              <a
                                href={evidenceResult.filing.url}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-100"
                              >
                                Open filing
                                <ExternalLink className="h-4 w-4" />
                              </a>
                            )}
                          </div>
                          <div className="flex flex-wrap gap-2 text-xs text-slate-500">
                            <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                              {evidenceResult.highlights.length} highlight{evidenceResult.highlights.length === 1 ? '' : 's'}
                            </span>
                            {evidenceResult.truncated && (
                              <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-amber-700">
                                Cleaned text preview truncated
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {evidenceResult.warnings.length > 0 && (
                        <div className="rounded-3xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
                          {evidenceResult.warnings.map((warning) => (
                            <p key={warning}>{warning}</p>
                          ))}
                        </div>
                      )}

                      <div className="space-y-3">
                        <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">Extracted highlights</p>
                        {evidenceResult.highlights.length === 0 ? (
                          <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                            No dividend-specific highlights were extracted from this filing.
                          </div>
                        ) : evidenceResult.highlights.map((highlight) => (
                          <div key={highlight.id} className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className={`rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] ${eventTypeClasses(highlight.eventType)}`}>
                                {eventTypeLabel(highlight.eventType)}
                              </span>
                              <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-700">
                                {highlight.confidence}
                              </span>
                              {highlight.sourceSection && (
                                <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-600">
                                  {highlight.sourceSection}
                                </span>
                              )}
                            </div>
                            <p className="mt-3 text-sm leading-6 text-slate-700">
                              {highlight.snippet || highlight.policyLanguage || 'No text snippet was extracted for this highlight.'}
                            </p>
                          </div>
                        ))}
                      </div>

                      <div>
                        <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">Cleaned filing text</p>
                        <div className="mt-3 max-h-[360px] overflow-auto rounded-3xl border border-slate-200 bg-slate-950 px-4 py-4 text-sm leading-6 text-slate-100">
                          <pre className="whitespace-pre-wrap break-words font-sans">
                            {evidenceResult.cleanedText || 'No cleaned filing text is available for this accession.'}
                          </pre>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </section>
            </div>
          </section>

          <DividendEventsSection
            dividendEvents={dividendEvents}
            openEvidence={openEvidence}
          />

          <DividendPeerComparisonSection
            compareInput={compareInput}
            setCompareInput={setCompareInput}
            runComparison={runComparison}
            comparisonState={comparisonState}
            comparisonResult={comparisonResult}
            companyIdentifier={company.ticker || company.cik}
            metrics={DEFAULT_COMPARE_METRICS}
          />

          <DividendHistoryTableSection
            historyRows={historyRows}
            columns={ANNUAL_TABLE_COLUMNS}
          />
        </>
      )}
    </div>
  );
}
