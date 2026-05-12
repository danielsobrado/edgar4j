import React from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { subYears, format } from 'date-fns';
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
} from 'lucide-react';
import {
  downloadsApi,
  filingsApi,
  form13dgApi,
  form4Api,
  marketDataApi,
  type Form13DG,
  type Form4,
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
  splitFundamentalFilings,
  ANNUAL_FORM_TYPES,
  QUARTERLY_FORM_TYPES,
  type FilingCandidate,
  type FilingSnapshot,
} from '../utils/fundamentals';
import {
  FilingPicker,
  FundamentalTrendCard,
  InsiderActivityCard,
  MetricBoard,
  PriceTrendCard,
  findHistoricalClose,
  formatCurrencyValue,
  formatDate,
  formatMultipleValue,
  formatPercentValue,
  getForm4TradeDate,
  getForm4TradeDirection,
  getSortableTimestamp,
  loadFilingAnalysis,
} from './companyFundamentalsView';

const ANNUAL_TREND_FILING_LIMIT = 5;

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
