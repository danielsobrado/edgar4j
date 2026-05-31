import React from 'react';
import { CalendarDays, CalendarRange, CheckCircle, Clock, Download, Loader, RefreshCw, XCircle } from 'lucide-react';
import * as Progress from '@radix-ui/react-progress';
import { downloadsApi, settingsApi, DownloadJob, UsaSpendingCsvPage } from '../api';
import { useDownloadJob } from '../hooks';
import { showError, showSuccess } from '../store/notificationStore';
import { Pagination } from '../components/common/Pagination';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import { UsaSpendingCoverageHeatmap } from '../components/usaspending/UsaSpendingCoverageHeatmap';
import { toDisplayDate } from '../utils';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const COLUMN_PAGE_SIZE = 30;
const CSV_COLUMN_PREFIX = 'col:csv:';

function toDateInputValue(date: Date) {
  return date.toISOString().slice(0, 10);
}

function getFiscalYearRange() {
  const today = new Date();
  const year = today.getFullYear();
  const fiscalYearStartYear = today.getMonth() >= 9 ? year : year - 1;
  return {
    start: `${fiscalYearStartYear}-10-01`,
    end: toDateInputValue(today),
  };
}

function getThisMonthRange() {
  const today = new Date();
  return {
    start: toDateInputValue(new Date(today.getFullYear(), today.getMonth(), 1)),
    end: toDateInputValue(today),
  };
}

function getLookbackRange(days: number) {
  const today = new Date();
  return {
    start: toDateInputValue(new Date(today.getTime() - ((days - 1) * ONE_DAY_MS))),
    end: toDateInputValue(today),
  };
}

function getYesterdayRange() {
  const yesterday = new Date(Date.now() - ONE_DAY_MS);
  return {
    start: toDateInputValue(yesterday),
    end: toDateInputValue(yesterday),
  };
}

function getStatusIcon(job?: DownloadJob | null) {
  switch (job?.status) {
    case 'COMPLETED':
      return <CheckCircle className="w-5 h-5 text-green-600" />;
    case 'FAILED':
      return <XCircle className="w-5 h-5 text-red-600" />;
    case 'IN_PROGRESS':
      return <Loader className="w-5 h-5 text-blue-600 animate-spin" />;
    default:
      return <Clock className="w-5 h-5 text-gray-500" />;
  }
}

function getConfidenceClass(confidence: number) {
  if (confidence >= 95) return 'bg-green-100 text-green-800';
  if (confidence >= 85) return 'bg-blue-100 text-blue-800';
  return 'bg-amber-100 text-amber-800';
}

// USAspending award CSVs report dollar figures as plain numbers; format these columns as currency.
const CURRENCY_COLUMN_PATTERN = /(obligation|obligated|outlay|subsidy|face_value|_amount|value_of_award|options_value)/i;

function isCurrencyColumn(header: string) {
  return CURRENCY_COLUMN_PATTERN.test(header);
}

function formatCurrencyCell(value: string) {
  if (!value || value.trim() === '') {
    return value;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return value;
  }
  return numeric.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatMarketCap(value?: number) {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return null;
  }
  return value.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  });
}

function latestJobUpdateDay(job?: DownloadJob | null) {
  return toDisplayDate(job?.completedAt ?? job?.startedAt);
}

function getCsvColumnId(header: string) {
  return `${CSV_COLUMN_PREFIX}${header}`;
}

const PREVIEW_COLUMN_DEFS = [
  { id: 'col:candidate', label: 'Candidate EDGAR Match', sticky: true, baseWidth: 'min-w-72' },
  { id: 'col:confidence', label: 'Confidence', sticky: false, baseWidth: 'min-w-28' },
  { id: 'col:cik', label: 'CIK', sticky: false, baseWidth: 'min-w-32' },
  { id: 'col:ticker', label: 'Ticker', sticky: false, baseWidth: 'min-w-24' },
  { id: 'col:matchSource', label: 'Match Source', sticky: false, baseWidth: 'min-w-44' },
] as const;

export function UsaSpendingDownloads() {
  const fiscalYearRange = React.useMemo(getFiscalYearRange, []);
  const [dateFrom, setDateFrom] = React.useState(fiscalYearRange.start);
  const [dateTo, setDateTo] = React.useState(fiscalYearRange.end);
  const [starting, setStarting] = React.useState(false);
  const [jobId, setJobId] = React.useState<string>();
  const [csvPage, setCsvPage] = React.useState<UsaSpendingCsvPage | null>(null);
  const [csvLoading, setCsvLoading] = React.useState(false);
  const [csvError, setCsvError] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const [columnPage, setColumnPage] = React.useState(0);
  const [showCoverage, setShowCoverage] = React.useState(false);
  const [filterCompany, setFilterCompany] = React.useState('');
  const [filterConfidenceMin, setFilterConfidenceMin] = React.useState('');
  const [filterConfidenceMax, setFilterConfidenceMax] = React.useState('');
  const [filterCik, setFilterCik] = React.useState('');
  const [filterTicker, setFilterTicker] = React.useState('');
  const [filterSource, setFilterSource] = React.useState('');
  const [hiddenColumns, setHiddenColumns] = React.useState<Set<string>>(new Set());
  const [sortColumnId, setSortColumnId] = React.useState<string | null>(null);
  const [sortDirection, setSortDirection] = React.useState<'asc' | 'desc'>('asc');
  const dateFromRef = React.useRef<HTMLInputElement>(null);
  const dateToRef = React.useRef<HTMLInputElement>(null);
  const rangeInitializedFromLatestJobRef = React.useRef(false);
  const hiddenColumnsTouchedRef = React.useRef(false);
  const { job, refresh } = useDownloadJob(jobId, 5000);

  React.useEffect(() => {
    let cancelled = false;

    const loadColumnPreferences = async () => {
      try {
        const preferences = await settingsApi.getUsaSpendingColumnPreferences();
        if (!cancelled && !hiddenColumnsTouchedRef.current) {
          setHiddenColumns(new Set(preferences.hiddenColumns ?? []));
        }
      } catch {
        // Column preferences are optional; the page remains usable with all columns visible.
      }
    };

    void loadColumnPreferences();
    return () => {
      cancelled = true;
    };
  }, []);

  React.useEffect(() => {
    if (jobId) {
      return;
    }

    let cancelled = false;
    const loadLatestCompletedJob = async () => {
      try {
        const jobs = await downloadsApi.getJobs(50);
        const latestCompletedJob = jobs.find(candidate =>
          candidate.type === 'USA_SPENDING_AWARDS' && candidate.status === 'COMPLETED'
        );
        if (!cancelled && latestCompletedJob) {
          setJobId(latestCompletedJob.id);
        }
      } catch {
        // The page remains usable even if recent jobs cannot be loaded.
      }
    };

    void loadLatestCompletedJob();
    return () => {
      cancelled = true;
    };
  }, [jobId]);

  React.useEffect(() => {
    if (rangeInitializedFromLatestJobRef.current || job?.type !== 'USA_SPENDING_AWARDS' || job.status !== 'COMPLETED') {
      return;
    }

    const latestUpdateDate = job.dateTo ?? job.completedAt?.slice(0, 10) ?? job.startedAt?.slice(0, 10);
    if (!latestUpdateDate) {
      return;
    }

    rangeInitializedFromLatestJobRef.current = true;
    setDateFrom(latestUpdateDate);
    setDateTo(toDateInputValue(new Date()));
  }, [job]);

  const fetchCsvPage = React.useCallback(async () => {
    if (!job || job.status !== 'COMPLETED') {
      return;
    }

    setCsvLoading(true);
    setCsvError(null);
    try {
      const data = await downloadsApi.getUsaSpendingCsvPage(job.id, page, size);
      setCsvPage(data);
      setColumnPage(0);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load USAspending CSV preview';
      setCsvError(message);
    } finally {
      setCsvLoading(false);
    }
  }, [job, page, size]);

  React.useEffect(() => {
    void fetchCsvPage();
  }, [fetchCsvPage]);

  const applyRange = (range: { start: string; end: string }) => {
    setDateFrom(range.start);
    setDateTo(range.end);
  };

  const handleStartDownload = async () => {
    const requestedDateFrom = dateFromRef.current?.value || dateFrom;
    const requestedDateTo = dateToRef.current?.value || dateTo;

    if (!requestedDateFrom || !requestedDateTo) {
      showError('Download Failed', 'Start date and end date are required');
      return;
    }

    setStarting(true);
    try {
      const startedJob = await downloadsApi.downloadUsaSpendingAwards({
        dateFrom: requestedDateFrom,
        dateTo: requestedDateTo,
      });
      setJobId(startedJob.id);
      setCsvPage(null);
      setCsvError(null);
      setPage(0);
      setColumnPage(0);
      showSuccess('Download Started', `USAspending award CSV download queued for ${requestedDateFrom} to ${requestedDateTo}`);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start USAspending download';
      showError('Download Failed', message);
    } finally {
      setStarting(false);
    }
  };

  const progress = job?.progress ?? 0;
  const latestUpdateDay = latestJobUpdateDay(job);
  const visibleColumnStart = columnPage * COLUMN_PAGE_SIZE;
  const visibleHeaders = csvPage?.headers
    .map((header, index) => ({ header, index, columnId: getCsvColumnId(header) }))
    .slice(visibleColumnStart, visibleColumnStart + COLUMN_PAGE_SIZE) ?? [];
  const columnPageCount = csvPage ? Math.ceil(csvPage.headers.length / COLUMN_PAGE_SIZE) : 0;
  const allCsvColumns = React.useMemo(() => {
    if (!csvPage) {
      return [];
    }
    return csvPage.headers.map((header, index) => ({
      header,
      index,
      columnId: getCsvColumnId(header),
    }));
  }, [csvPage]);
  const visibleMainColumns = React.useMemo(() => PREVIEW_COLUMN_DEFS.filter(({ id }) => !hiddenColumns.has(id)), [hiddenColumns]);
  const visibleCsvHeaders = React.useMemo(
    () => visibleHeaders.filter(({ columnId }) => !hiddenColumns.has(columnId)),
    [visibleHeaders, hiddenColumns]
  );
  const visibleColumnCount = visibleMainColumns.length + visibleCsvHeaders.length;
  const hasActiveColumnVisibilityFilters = visibleColumnCount < (PREVIEW_COLUMN_DEFS.length + (csvPage?.headers.length ?? 0));
  const persistHiddenColumns = React.useCallback(async (nextHiddenColumns: Set<string>) => {
    try {
      await settingsApi.updateUsaSpendingColumnPreferences([...nextHiddenColumns]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to save USAspending column preferences';
      showError('Column Preferences Not Saved', message);
    }
  }, []);
  const updateHiddenColumns = React.useCallback((nextHiddenColumns: Set<string>) => {
    hiddenColumnsTouchedRef.current = true;
    setHiddenColumns(nextHiddenColumns);
    void persistHiddenColumns(nextHiddenColumns);
  }, [persistHiddenColumns]);
  const toggleColumn = React.useCallback((columnId: string) => {
    const next = new Set(hiddenColumns);
    if (next.has(columnId)) {
      next.delete(columnId);
    } else {
      next.add(columnId);
      if (sortColumnId === columnId) {
        setSortColumnId(null);
      }
    }
    updateHiddenColumns(next);
  }, [hiddenColumns, sortColumnId, updateHiddenColumns]);
  const showAllColumns = () => {
    updateHiddenColumns(new Set());
  };
  const normalizedCompanyFilter = filterCompany.trim().toLowerCase();
  const normalizedCikFilter = filterCik.trim().toLowerCase();
  const normalizedTickerFilter = filterTicker.trim().toLowerCase();
  const minConfidence = Number.parseInt(filterConfidenceMin, 10);
  const maxConfidence = Number.parseInt(filterConfidenceMax, 10);
  const hasActiveFilters = Boolean(
    normalizedCompanyFilter ||
      filterConfidenceMin ||
      filterConfidenceMax ||
      normalizedCikFilter ||
      normalizedTickerFilter ||
      filterSource
  );
  const matchSourceOptions = React.useMemo(() => {
    if (!csvPage) {
      return [];
    }
    const sources = new Set<string>();
    for (const rowMatch of csvPage.rowMatches) {
      const bestMatch = rowMatch[0];
      if (bestMatch?.sourceField) {
        sources.add(bestMatch.sourceField);
      }
    }
    return [...sources].sort();
  }, [csvPage]);
  const filteredCsvRows = React.useMemo(() => {
    if (!csvPage) {
      return [];
    }
    return csvPage.rows
      .map((row, rowIndex) => ({
        row,
        rowIndex,
        matches: csvPage.rowMatches[rowIndex] ?? [],
      }))
      .filter(({ matches }) => {
        const bestMatch = matches[0];
        if (!bestMatch) {
          return (
            !normalizedCompanyFilter &&
            !filterConfidenceMin &&
            !filterConfidenceMax &&
            !normalizedCikFilter &&
            !normalizedTickerFilter &&
            !filterSource
          );
        }
        if (normalizedCompanyFilter && !bestMatch.companyName.toLowerCase().includes(normalizedCompanyFilter)) {
          return false;
        }
        if (filterSource && bestMatch.sourceField !== filterSource) {
          return false;
        }
        if (normalizedCikFilter && !bestMatch.cik.toLowerCase().includes(normalizedCikFilter)) {
          return false;
        }
        if (normalizedTickerFilter && !(bestMatch.ticker ?? '').toLowerCase().includes(normalizedTickerFilter)) {
          return false;
        }
        if (Number.isFinite(minConfidence) && bestMatch.confidence < minConfidence) {
          return false;
        }
        if (Number.isFinite(maxConfidence) && maxConfidence >= 0 && bestMatch.confidence > maxConfidence) {
          return false;
        }
        return true;
      });
  }, [csvPage, normalizedCompanyFilter, normalizedCikFilter, normalizedTickerFilter, filterSource, minConfidence, maxConfidence, filterConfidenceMin, filterConfidenceMax]);
  const getSortValue = React.useCallback(
    (entry: { row: string[]; matches: UsaSpendingCsvPage['rowMatches'][number] }, columnId: string) => {
      const bestMatch = entry.matches[0];
      if (columnId.startsWith(CSV_COLUMN_PREFIX)) {
        const csvHeader = columnId.slice(CSV_COLUMN_PREFIX.length);
        const csvIndex = csvPage?.headers.indexOf(csvHeader) ?? -1;
        if (csvIndex < 0) {
          return '';
        }
        return entry.row[csvIndex] ?? '';
      }

      if (!bestMatch) {
        return '';
      }

      if (columnId === 'col:candidate') {
        return bestMatch.companyName;
      }
      if (columnId === 'col:confidence') {
        return bestMatch.confidence;
      }
      if (columnId === 'col:cik') {
        return bestMatch.cik;
      }
      if (columnId === 'col:ticker') {
        return bestMatch.ticker ?? '';
      }
      if (columnId === 'col:matchSource') {
        return bestMatch.sourceField;
      }
      return '';
    },
    [csvPage]
  );
  const sortedCsvRows = React.useMemo(() => {
    if (!sortColumnId) {
      return filteredCsvRows;
    }

    return [...filteredCsvRows].sort((left, right) => {
      const leftValue = getSortValue(left, sortColumnId);
      const rightValue = getSortValue(right, sortColumnId);

      const leftNumber = typeof leftValue === 'number' && Number.isFinite(leftValue) ? leftValue : Number.parseFloat(String(leftValue));
      const rightNumber = typeof rightValue === 'number' && Number.isFinite(rightValue) ? rightValue : Number.parseFloat(String(rightValue));

      let comparison = 0;
      if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
        comparison = leftNumber - rightNumber;
      } else {
        const leftText = String(leftValue).toLowerCase();
        const rightText = String(rightValue).toLowerCase();
        comparison = leftText.localeCompare(rightText, 'en');
      }

      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [filteredCsvRows, sortColumnId, sortDirection, getSortValue]);
  const sortStateLabel = (columnId: string) => {
    if (sortColumnId !== columnId) {
      return '↕';
    }
    return sortDirection === 'asc' ? '↑' : '↓';
  };
  const handleSortToggle = (columnId: string) => {
    setSortDirection((currentDirection) => {
      if (sortColumnId === columnId) {
        return currentDirection === 'asc' ? 'desc' : 'asc';
      }
      return 'asc';
    });
    setSortColumnId(columnId);
  };
  const hideColumn = (columnId: string) => {
    if (hiddenColumns.has(columnId)) {
      return;
    }
    const next = new Set(hiddenColumns);
    next.add(columnId);
    updateHiddenColumns(next);
    if (sortColumnId === columnId) {
      setSortColumnId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2">
            <Download className="w-8 h-8" />
            USAspending CSV Downloads
          </h1>
          <p className="text-gray-600">
            Queue custom award data downloads from USAspending.gov and save the generated CSV ZIP locally.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowCoverage((current) => !current)}
            className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${
              showCoverage
                ? 'border-[#1a1f36] bg-[#1a1f36] text-white'
                : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
          >
            <CalendarRange className="w-4 h-4" />
            Data Coverage
          </button>
          <button
            onClick={() => void refresh()}
            disabled={!jobId}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RefreshCw className="w-4 h-4" />
            Refresh
          </button>
          <span className="text-xs text-gray-500">Updated {latestUpdateDay}</span>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4 flex items-center gap-2">
          <CalendarDays className="w-5 h-5" />
          Award Date Range
        </h2>
        <div className="grid gap-4 md:grid-cols-[1fr_1fr_auto] md:items-end">
          <label className="space-y-2">
            <span className="text-sm text-gray-700">Start Date</span>
            <input
              ref={dateFromRef}
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm text-gray-700">End Date</span>
            <input
              ref={dateToRef}
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <button
            onClick={() => void handleStartDownload()}
            disabled={starting}
            className="inline-flex items-center justify-center gap-2 rounded-md bg-[#1a1f36] px-5 py-2 text-white hover:bg-[#252b47] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {starting ? <Loader className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Download CSV
          </button>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <button
            onClick={() => applyRange(fiscalYearRange)}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            Current FY
          </button>
          <button
            onClick={() => applyRange(getThisMonthRange())}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            This Month
          </button>
          <button
            onClick={() => applyRange(getYesterdayRange())}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            Yesterday
          </button>
          <button
            onClick={() => applyRange(getLookbackRange(7))}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            Last 7 Days
          </button>
          <button
            onClick={() => applyRange(getLookbackRange(30))}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            Last 30 Days
          </button>
        </div>
        <p className="mt-4 text-sm text-gray-500">
          Requests use USAspending action dates, all prime award types, and CSV output. Date ranges may span up to one year.
        </p>
      </div>

      {showCoverage && (
        <UsaSpendingCoverageHeatmap
          onJobQueued={(id) => {
            setJobId(id);
            setCsvPage(null);
            setCsvError(null);
            setPage(0);
            setColumnPage(0);
          }}
          refreshKey={job?.status === 'COMPLETED' ? `${job.id}:${job.completedAt ?? ''}` : ''}
        />
      )}

      {job && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="mb-1 flex items-center gap-2">
                {getStatusIcon(job)}
                Latest USAspending Job
              </h2>
              <p className="text-sm text-gray-600">{job.description}</p>
            </div>
            <span className="rounded-md bg-gray-100 px-2 py-1 text-xs font-mono text-gray-700">
              {job.status}
            </span>
          </div>

          {(job.status === 'PENDING' || job.status === 'IN_PROGRESS') && (
            <div className="mt-4">
              <Progress.Root className="relative h-2 w-full overflow-hidden rounded-full bg-gray-200">
                <Progress.Indicator
                  className="h-full bg-blue-500 transition-transform duration-300"
                  style={{ transform: `translateX(-${100 - progress}%)` }}
                />
              </Progress.Root>
              <p className="mt-2 text-sm text-gray-500">Waiting for USAspending to generate the CSV ZIP.</p>
            </div>
          )}

          {job.status === 'COMPLETED' && (
            <div className="mt-4 space-y-2 text-sm text-gray-700">
              {job.totalFiles >= 0 && <p>Rows reported by USAspending: {job.totalFiles.toLocaleString()}</p>}
              {job.outputPath && <p className="break-all">Saved file: {job.outputPath}</p>}
              {job.sourceUrl && <p className="break-all">Source URL: {job.sourceUrl}</p>}
            </div>
          )}

          {job.status === 'FAILED' && (
            <p className="mt-4 text-sm text-red-700">{job.error || 'USAspending download failed'}</p>
          )}
        </div>
      )}

      {job?.status === 'COMPLETED' && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2>CSV Preview</h2>
              <p className="text-sm text-gray-600">
                {csvPage?.fileName ? `Parsed from ${csvPage.fileName}` : 'Load rows from the generated USAspending CSV.'}
              </p>
            </div>
            <button
              onClick={() => void fetchCsvPage()}
              disabled={csvLoading}
              className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${csvLoading ? 'animate-spin' : ''}`} />
              Reload Rows
            </button>
          </div>

          {csvError && <p className="mt-4 text-sm text-red-700">{csvError}</p>}

          {csvLoading && !csvPage ? (
            <div className="py-8 text-sm text-gray-600">Loading CSV rows...</div>
          ) : csvPage ? (
            <div className="mt-4 space-y-4">
              <div className="rounded-md border border-gray-200 p-4">
                <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">Candidate Search</span>
                    <input
                      value={filterCompany}
                      onChange={(event) => setFilterCompany(event.target.value)}
                      placeholder="Company or awardee"
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">Min Confidence</span>
                    <input
                      value={filterConfidenceMin}
                      onChange={(event) => setFilterConfidenceMin(event.target.value)}
                      type="number"
                      min={0}
                      max={100}
                      placeholder="0"
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">Max Confidence</span>
                    <input
                      value={filterConfidenceMax}
                      onChange={(event) => setFilterConfidenceMax(event.target.value)}
                      type="number"
                      min={0}
                      max={100}
                      placeholder="100"
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">CIK</span>
                    <input
                      value={filterCik}
                      onChange={(event) => setFilterCik(event.target.value)}
                      placeholder="CIK"
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">Ticker</span>
                    <input
                      value={filterTicker}
                      onChange={(event) => setFilterTicker(event.target.value)}
                      placeholder="Ticker"
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm text-gray-700">Match Source</span>
                    <select
                      value={filterSource}
                      onChange={(event) => setFilterSource(event.target.value)}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">All sources</option>
                      {matchSourceOptions.map((source) => (
                        <option key={source} value={source}>
                          {source}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                {hasActiveFilters && (
                  <div className="mt-3 flex justify-end">
                    <button
                      onClick={() => {
                        setFilterCompany('');
                        setFilterConfidenceMin('');
                        setFilterConfidenceMax('');
                        setFilterCik('');
                        setFilterTicker('');
                        setFilterSource('');
                      }}
                      className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                    >
                      Clear Filters
                    </button>
                  </div>
                )}
              </div>
              <div className="rounded-md border border-gray-200 p-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <div className="text-sm text-gray-700">Show / Hide Columns</div>
                  {hasActiveColumnVisibilityFilters && (
                    <button
                      onClick={showAllColumns}
                      className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
                    >
                      Show All
                    </button>
                  )}
                </div>
                <div className="space-y-3">
                  <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
                    {PREVIEW_COLUMN_DEFS.map(({ id, label }) => (
                      <label key={id} className="flex items-center gap-2 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={!hiddenColumns.has(id)}
                          onChange={() => toggleColumn(id)}
                        />
                        {label}
                      </label>
                    ))}
                  </div>
                  <div className="grid max-h-56 gap-2 overflow-y-auto pr-1 sm:grid-cols-2 lg:grid-cols-3">
                    {allCsvColumns.map(({ header, columnId }) => (
                      <label key={columnId} className="flex items-center gap-2 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={!hiddenColumns.has(columnId)}
                          onChange={() => toggleColumn(columnId)}
                        />
                        <span className="truncate" title={header}>{header}</span>
                      </label>
                    ))}
                  </div>
                </div>
                {visibleColumnCount === 0 && (
                  <p className="mt-3 text-sm text-amber-700">
                    No columns are selected. Turn columns back on to render rows.
                  </p>
                )}
              </div>

              <div className="rounded-md border border-gray-200">
                <Table>
                  <TableHeader>
                    <TableRow>
                        {visibleMainColumns.map(({ id, label, sticky, baseWidth }) => (
                          <TableHead
                            key={id}
                            className={`${sticky ? 'sticky left-0 z-10' : ''} ${baseWidth} bg-gray-50 align-top [white-space:normal]`}
                          >
                            <div className="flex min-w-0 flex-col gap-1">
                              <button
                                onClick={() => void handleSortToggle(id)}
                                className="flex w-full min-w-0 items-start justify-between gap-2 text-left"
                                type="button"
                              >
                                <span className="min-w-0 truncate" title={label}>{label}</span>
                                <span className="shrink-0 text-xs text-gray-500">{sortStateLabel(id)}</span>
                              </button>
                              <button
                                type="button"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  hideColumn(id);
                                }}
                                className="self-start text-xs text-gray-500 underline underline-offset-4 hover:text-gray-700"
                              >
                                Hide
                              </button>
                            </div>
                          </TableHead>
                        ))}
                      {visibleCsvHeaders.map(({ header, columnId }) => (
                        <TableHead key={columnId} className="min-w-48 max-w-72 bg-gray-50 align-top [white-space:normal]">
                          <div className="flex min-w-0 flex-col gap-1">
                            <button
                              onClick={() => void handleSortToggle(columnId)}
                              className="flex w-full min-w-0 items-start justify-between gap-2 text-left"
                              type="button"
                            >
                              <span className="min-w-0 truncate" title={header}>{header}</span>
                              <span className="shrink-0 text-xs text-gray-500">{sortStateLabel(columnId)}</span>
                            </button>
                            <button
                              type="button"
                              onClick={(event) => {
                                event.stopPropagation();
                                hideColumn(columnId);
                              }}
                              className="self-start text-xs text-gray-500 underline underline-offset-4 hover:text-gray-700"
                            >
                              Hide
                            </button>
                          </div>
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sortedCsvRows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={Math.max(visibleColumnCount, 1)} className="py-8 text-center text-gray-500">
                          {csvPage.rows.length === 0
                            ? 'No rows returned for this date range.'
                            : 'No rows returned for the active filters.'}
                        </TableCell>
                      </TableRow>
                    ) : (
                      sortedCsvRows.map(({ row, rowIndex, matches }) => (
                        <TableRow key={`${csvPage.page}-${rowIndex}`}>
                          {(() => {
                            const bestMatch = matches[0];
                            if (!bestMatch) {
                              return (
                                <>
                                  {visibleMainColumns.map(({ id, baseWidth, sticky }) => {
                                    const cellClasses = `${sticky ? 'sticky left-0 z-10 ' : ''}${baseWidth} text-gray-500`;
                                    if (id === 'col:candidate') {
                                      return (
                                        <TableCell key={id} className={`${cellClasses} bg-white`}>
                                          No candidate
                                        </TableCell>
                                      );
                                    }
                                    return (
                                      <TableCell key={id} className={cellClasses}>
                                        -
                                      </TableCell>
                                    );
                                  })}
                                </>
                              );
                            }
                            const marketCapLabel = formatMarketCap(bestMatch.marketCap);
                            const companyTitle = marketCapLabel
                              ? `${bestMatch.companyName} · Market cap ${marketCapLabel}`
                              : bestMatch.companyName;
                            return (
                              <>
                                {visibleMainColumns.map(({ id, baseWidth, sticky }) => {
                                  if (id === 'col:candidate') {
                                    return (
                                      <TableCell key={id} className={`${sticky ? 'sticky left-0 z-10 ' : ''}${baseWidth} max-w-72 bg-white`}>
                                        <div className="space-y-1">
                                          <span className="block truncate" title={companyTitle}>
                                            {bestMatch.companyName}
                                          </span>
                                          {marketCapLabel && (
                                            <span className="block text-xs text-gray-500">Mkt cap {marketCapLabel}</span>
                                          )}
                                          {matches.length > 1 && (
                                            <span className="block text-xs text-gray-500">
                                              {matches.length - 1} more candidate{matches.length > 2 ? 's' : ''}
                                            </span>
                                          )}
                                        </div>
                                      </TableCell>
                                    );
                                  }
                                  if (id === 'col:confidence') {
                                    return (
                                      <TableCell key={id}>
                                        <span className={`rounded-md px-2 py-1 text-xs ${getConfidenceClass(bestMatch.confidence)}`}>
                                          {bestMatch.confidence}%
                                        </span>
                                      </TableCell>
                                    );
                                  }
                                  if (id === 'col:cik') {
                                    return <TableCell key={id} className="font-mono text-xs">{bestMatch.cik}</TableCell>;
                                  }
                                  if (id === 'col:ticker') {
                                    return <TableCell key={id} className="font-mono text-xs">{bestMatch.ticker || '-'}</TableCell>;
                                  }
                                  return (
                                    <TableCell key={id} className="max-w-44">
                                      <span className="block truncate" title={`${bestMatch.sourceField}: ${bestMatch.sourceValue}`}>
                                        {bestMatch.sourceField}
                                      </span>
                                    </TableCell>
                                  );
                                })}
                              </>
                            );
                          })()}
                          {visibleCsvHeaders.map(({ header, index, columnId }) => {
                            const value = row[index] ?? '';
                            const currency = isCurrencyColumn(header);
                            const displayValue = currency ? formatCurrencyCell(value) : value;
                            return (
                              <TableCell key={columnId} className={`max-w-72 ${currency ? 'text-right tabular-nums' : ''}`}>
                                <span className="block truncate" title={displayValue}>{displayValue}</span>
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>

              {csvPage.headers.length > COLUMN_PAGE_SIZE && (
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <span className="text-sm text-gray-600">
                    Showing columns {visibleColumnStart + 1} to {Math.min(visibleColumnStart + COLUMN_PAGE_SIZE, csvPage.headers.length)} of {csvPage.headers.length.toLocaleString()}
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setColumnPage((current) => Math.max(0, current - 1))}
                      disabled={columnPage === 0}
                      className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Previous Columns
                    </button>
                    <button
                      onClick={() => setColumnPage((current) => Math.min(columnPageCount - 1, current + 1))}
                      disabled={columnPage >= columnPageCount - 1}
                      className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Next Columns
                    </button>
                  </div>
                </div>
              )}

              {csvPage.totalRows > 0 ? (
                <Pagination
                  page={page}
                  totalPages={csvPage.totalPages}
                  totalElements={csvPage.totalRows}
                  size={size}
                  onPageChange={setPage}
                  onPageSizeChange={(nextSize) => {
                    setSize(nextSize);
                    setPage(0);
                  }}
                  pageSizeOptions={[10, 25, 50, 100]}
                />
              ) : (
                <p className="text-sm text-gray-600">0 CSV rows parsed.</p>
              )}
            </div>
          ) : (
            <div className="py-8 text-sm text-gray-600">CSV rows will appear here once the archive is parsed.</div>
          )}
        </div>
      )}
    </div>
  );
}
