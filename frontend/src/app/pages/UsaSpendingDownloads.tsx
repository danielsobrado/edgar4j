import React from 'react';
import { CalendarDays, CheckCircle, Clock, Download, Loader, RefreshCw, XCircle } from 'lucide-react';
import * as Progress from '@radix-ui/react-progress';
import { downloadsApi, DownloadJob, UsaSpendingCsvPage } from '../api';
import { useDownloadJob } from '../hooks';
import { showError, showSuccess } from '../store/notificationStore';
import { Pagination } from '../components/common/Pagination';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import { toDisplayDate } from '../utils';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const COLUMN_PAGE_SIZE = 30;

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
  const dateFromRef = React.useRef<HTMLInputElement>(null);
  const dateToRef = React.useRef<HTMLInputElement>(null);
  const rangeInitializedFromLatestJobRef = React.useRef(false);
  const { job, refresh } = useDownloadJob(jobId, 5000);

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
    .map((header, index) => ({ header, index }))
    .slice(visibleColumnStart, visibleColumnStart + COLUMN_PAGE_SIZE) ?? [];
  const columnPageCount = csvPage ? Math.ceil(csvPage.headers.length / COLUMN_PAGE_SIZE) : 0;

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
        <div className="relative left-1/2 w-screen max-w-[100vw] -translate-x-1/2 bg-white p-6 shadow-sm sm:px-6 lg:px-8">
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
              <div className="rounded-md border border-gray-200">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="sticky left-0 z-10 min-w-72 bg-gray-50">
                        Candidate EDGAR Match
                      </TableHead>
                      <TableHead className="min-w-28 bg-gray-50">
                        Confidence
                      </TableHead>
                      <TableHead className="min-w-32 bg-gray-50">
                        CIK
                      </TableHead>
                      <TableHead className="min-w-24 bg-gray-50">
                        Ticker
                      </TableHead>
                      <TableHead className="min-w-44 bg-gray-50">
                        Match Source
                      </TableHead>
                      {visibleHeaders.map(({ header, index }) => (
                        <TableHead key={`${header}-${index}`} className="max-w-72 bg-gray-50">
                          <span className="block truncate" title={header}>{header}</span>
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {csvPage.rows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={Math.max(visibleHeaders.length + 5, 1)} className="py-8 text-center text-gray-500">
                          No rows returned for this date range.
                        </TableCell>
                      </TableRow>
                    ) : (
                      csvPage.rows.map((row, rowIndex) => (
                        <TableRow key={`${csvPage.page}-${rowIndex}`}>
                          {(() => {
                            const matches = csvPage.rowMatches?.[rowIndex] ?? [];
                            const bestMatch = matches[0];
                            if (!bestMatch) {
                              return (
                                <>
                                  <TableCell className="sticky left-0 z-10 min-w-72 bg-white text-gray-500">No candidate</TableCell>
                                  <TableCell className="text-gray-500">-</TableCell>
                                  <TableCell className="text-gray-500">-</TableCell>
                                  <TableCell className="text-gray-500">-</TableCell>
                                  <TableCell className="text-gray-500">-</TableCell>
                                </>
                              );
                            }
                            const marketCapLabel = formatMarketCap(bestMatch.marketCap);
                            const companyTitle = marketCapLabel
                              ? `${bestMatch.companyName} · Market cap ${marketCapLabel}`
                              : bestMatch.companyName;
                            return (
                              <>
                                <TableCell className="sticky left-0 z-10 min-w-72 max-w-72 bg-white">
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
                                <TableCell>
                                  <span className={`rounded-md px-2 py-1 text-xs ${getConfidenceClass(bestMatch.confidence)}`}>
                                    {bestMatch.confidence}%
                                  </span>
                                </TableCell>
                                <TableCell className="font-mono text-xs">{bestMatch.cik}</TableCell>
                                <TableCell className="font-mono text-xs">{bestMatch.ticker || '-'}</TableCell>
                                <TableCell className="max-w-44">
                                  <span className="block truncate" title={`${bestMatch.sourceField}: ${bestMatch.sourceValue}`}>
                                    {bestMatch.sourceField}
                                  </span>
                                </TableCell>
                              </>
                            );
                          })()}
                          {visibleHeaders.map(({ header, index }) => {
                            const value = row[index] ?? '';
                            const currency = isCurrencyColumn(header);
                            const displayValue = currency ? formatCurrencyCell(value) : value;
                            return (
                              <TableCell key={`${header}-${index}`} className={`max-w-72 ${currency ? 'text-right tabular-nums' : ''}`}>
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
