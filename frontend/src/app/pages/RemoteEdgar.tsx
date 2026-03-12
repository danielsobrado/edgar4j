import React, { useState } from 'react';
import { Search } from 'lucide-react';
import {
  downloadsApi,
  FORM_TYPES,
  remoteEdgarApi,
  RemoteFilingSearchResult,
  RemoteSubmission,
  RemoteTicker,
} from '../api';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';

type RemotePeriodPreset = '1W' | '1M' | '1Y' | 'CUSTOM';

const REMOTE_PERIOD_OPTIONS: Array<{ value: RemotePeriodPreset; label: string }> = [
  { value: '1W', label: 'Last 1 week' },
  { value: '1M', label: 'Last 1 month' },
  { value: '1Y', label: 'Last 1 year' },
  { value: 'CUSTOM', label: 'Custom range' },
];

function padDatePart(value: number): string {
  return value.toString().padStart(2, '0');
}

function formatDateInput(date: Date): string {
  return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}`;
}

function getPresetRange(preset: Exclude<RemotePeriodPreset, 'CUSTOM'>): { dateFrom: string; dateTo: string } {
  const today = new Date();
  const from = new Date(today);

  switch (preset) {
    case '1W':
      from.setDate(from.getDate() - 6);
      break;
    case '1M':
      from.setMonth(from.getMonth() - 1);
      break;
    case '1Y':
      from.setFullYear(from.getFullYear() - 1);
      break;
  }

  return {
    dateFrom: formatDateInput(from),
    dateTo: formatDateInput(today),
  };
}

export function RemoteEdgar() {
  const initialRemoteRange = getPresetRange('1M');

  const [tickerQuery, setTickerQuery] = useState('');
  const [tickerSource, setTickerSource] = useState<'all' | 'exchanges' | 'mf'>('all');
  const [tickerLimit, setTickerLimit] = useState(50);
  const [tickers, setTickers] = useState<RemoteTicker[]>([]);
  const [tickersLoading, setTickersLoading] = useState(false);
  const [tickersError, setTickersError] = useState<string | null>(null);
  const [syncingCik, setSyncingCik] = useState<string | null>(null);
  const [tickerActionMessage, setTickerActionMessage] = useState<string | null>(null);
  const [tickerActionError, setTickerActionError] = useState<string | null>(null);

  const [remoteFormType, setRemoteFormType] = useState('13F');
  const [remotePeriodPreset, setRemotePeriodPreset] = useState<RemotePeriodPreset>('1M');
  const [remoteDateFrom, setRemoteDateFrom] = useState(initialRemoteRange.dateFrom);
  const [remoteDateTo, setRemoteDateTo] = useState(initialRemoteRange.dateTo);
  const [remoteSearchLimit, setRemoteSearchLimit] = useState(100);
  const [remoteFilingSearchResult, setRemoteFilingSearchResult] = useState<RemoteFilingSearchResult | null>(null);
  const [remoteFilingSearchLoading, setRemoteFilingSearchLoading] = useState(false);
  const [remoteFilingSearchError, setRemoteFilingSearchError] = useState<string | null>(null);
  const [syncingRemoteFilings, setSyncingRemoteFilings] = useState(false);
  const [remoteFilingActionMessage, setRemoteFilingActionMessage] = useState<string | null>(null);
  const [remoteFilingActionError, setRemoteFilingActionError] = useState<string | null>(null);

  const [cik, setCik] = useState('');
  const [filingsLimit, setFilingsLimit] = useState(25);
  const [submission, setSubmission] = useState<RemoteSubmission | null>(null);
  const [submissionLoading, setSubmissionLoading] = useState(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [submissionFormFilter, setSubmissionFormFilter] = useState('ALL');
  const [syncingSubmission, setSyncingSubmission] = useState(false);
  const [submissionActionMessage, setSubmissionActionMessage] = useState<string | null>(null);
  const [submissionActionError, setSubmissionActionError] = useState<string | null>(null);

  const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

  const waitForDownloadJob = async (jobId: string) => {
    for (let attempt = 0; attempt < 30; attempt += 1) {
      const job = await downloadsApi.getJobById(jobId);
      if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
        return job;
      }
      await wait(1000);
    }

    return null;
  };

  const normalizeRemoteError = (error: unknown, endpoint: 'tickers' | 'submissions' | 'filings'): string => {
    const message = error instanceof Error ? error.message : `Failed to fetch remote ${endpoint}`;
    const notFoundPath = endpoint === 'tickers'
      ? '/api/remote-edgar/tickers'
      : endpoint === 'submissions'
        ? '/api/remote-edgar/submissions/{cik}'
        : '/api/remote-edgar/filings/search';

    if (message.includes('404') && message.includes('/api/remote-edgar/')) {
      return `Backend endpoint ${notFoundPath} was not found. Restart the backend so the new remote EDGAR controller is loaded.`;
    }

    return message;
  };

  const applyRemotePreset = (preset: RemotePeriodPreset) => {
    setRemotePeriodPreset(preset);
    if (preset === 'CUSTOM') {
      return;
    }
    const nextRange = getPresetRange(preset);
    setRemoteDateFrom(nextRange.dateFrom);
    setRemoteDateTo(nextRange.dateTo);
  };

  const searchTickers = async () => {
    setTickersLoading(true);
    setTickersError(null);
    setTickerActionError(null);
    setTickerActionMessage(null);
    try {
      const data = await remoteEdgarApi.getTickers({
        source: tickerSource,
        search: tickerQuery.trim() || undefined,
        limit: tickerLimit,
      });
      setTickers(data);
    } catch (error) {
      setTickersError(normalizeRemoteError(error, 'tickers'));
    } finally {
      setTickersLoading(false);
    }
  };

  const searchRemoteFilings = async () => {
    if (!remoteFormType.trim()) {
      setRemoteFilingSearchError('Form type is required');
      return;
    }
    if (!remoteDateFrom || !remoteDateTo) {
      setRemoteFilingSearchError('Both from and to dates are required');
      return;
    }

    setRemoteFilingSearchLoading(true);
    setRemoteFilingSearchError(null);
    setRemoteFilingActionError(null);
    setRemoteFilingActionMessage(null);
    setRemoteFilingSearchResult(null);
    try {
      const data = await remoteEdgarApi.searchFilings({
        formType: remoteFormType.trim(),
        dateFrom: remoteDateFrom,
        dateTo: remoteDateTo,
        limit: remoteSearchLimit,
      });
      setRemoteFilingSearchResult(data);
    } catch (error) {
      setRemoteFilingSearchError(normalizeRemoteError(error, 'filings'));
    } finally {
      setRemoteFilingSearchLoading(false);
    }
  };

  const loadSubmissionByCik = async (rawCik: string) => {
    const normalizedCik = rawCik.trim();
    if (!normalizedCik) {
      setSubmissionError('CIK is required');
      return;
    }

    setCik(normalizedCik);
    setSubmissionLoading(true);
    setSubmissionError(null);
    setSubmissionActionError(null);
    setSubmissionActionMessage(null);
    try {
      const data = await remoteEdgarApi.getSubmissionByCik(normalizedCik, filingsLimit);
      setSubmission(data);
      setSubmissionFormFilter('ALL');
    } catch (error) {
      setSubmissionError(normalizeRemoteError(error, 'submissions'));
    } finally {
      setSubmissionLoading(false);
    }
  };

  const loadSubmission = async () => {
    await loadSubmissionByCik(cik);
  };

  const handleExploreTicker = async (ticker: RemoteTicker) => {
    await loadSubmissionByCik(ticker.cik);
  };

  const handleSyncLocal = async (ticker: RemoteTicker) => {
    setSyncingCik(ticker.cik);
    setTickerActionError(null);
    setTickerActionMessage(null);
    try {
      const job = await downloadsApi.downloadSubmissions(ticker.cik);
      setTickerActionMessage(`Sync queued for ${ticker.ticker} (${ticker.cik}). Waiting for job ${job.id}...`);

      const completedJob = await waitForDownloadJob(job.id);
      if (!completedJob) {
        setTickerActionMessage(`Sync queued for ${ticker.ticker} (${ticker.cik}). Job ${job.id} is still running. Check Downloads for progress.`);
        return;
      }

      if (completedJob.status === 'COMPLETED') {
        setTickerActionMessage(`Sync completed for ${ticker.ticker} (${ticker.cik}). Imported ${completedJob.filesDownloaded} filing records.`);
        return;
      }

      setTickerActionError(completedJob.error || `Sync ${completedJob.status.toLowerCase()} for ${ticker.ticker} (${ticker.cik}).`);
    } catch (error) {
      setTickerActionError(error instanceof Error ? error.message : 'Failed to queue local sync');
    } finally {
      setSyncingCik(null);
    }
  };

  const handleRemoteFilingSyncLocal = async () => {
    if (!remoteFilingSearchResult || remoteFilingSearchResult.totalMatches === 0) {
      setRemoteFilingActionError('No remote filings are loaded to sync.');
      return;
    }

    setSyncingRemoteFilings(true);
    setRemoteFilingActionError(null);
    setRemoteFilingActionMessage(null);
    try {
      const job = await downloadsApi.downloadRemoteFilings({
        formType: remoteFormType.trim(),
        dateFrom: remoteDateFrom,
        dateTo: remoteDateTo,
      });
      setRemoteFilingActionMessage(`Sync queued for ${remoteFormType.trim()} filings from ${remoteDateFrom} to ${remoteDateTo}. Waiting for job ${job.id}...`);

      const completedJob = await waitForDownloadJob(job.id);
      if (!completedJob) {
        setRemoteFilingActionMessage(`Sync queued for ${remoteFormType.trim()} filings from ${remoteDateFrom} to ${remoteDateTo}. Job ${job.id} is still running. Check Downloads for progress.`);
        return;
      }

      if (completedJob.status === 'COMPLETED') {
        const companySummary = completedJob.totalFiles > 0
          ? ` across ${completedJob.totalFiles} companies`
          : '';
        setRemoteFilingActionMessage(`Sync completed for ${remoteFormType.trim()} filings from ${remoteDateFrom} to ${remoteDateTo}. Imported ${completedJob.filesDownloaded} filing records${companySummary}.`);
        return;
      }

      setRemoteFilingActionError(completedJob.error || `Sync ${completedJob.status.toLowerCase()} for ${remoteFormType.trim()} filings.`);
    } catch (error) {
      setRemoteFilingActionError(error instanceof Error ? error.message : 'Failed to queue remote filing sync');
    } finally {
      setSyncingRemoteFilings(false);
    }
  };

  const getSecCompanyUrl = (companyCik: string) =>
    `https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${encodeURIComponent(companyCik)}`;

  const getSecSubmissionJsonUrl = (companyCik: string) =>
    `https://data.sec.gov/submissions/CIK${companyCik.padStart(10, '0')}.json`;

  const normalizeCikForArchive = (companyCik: string) => companyCik.replace(/^0+(?!$)/, '');

  const getSecFilingDocumentUrl = (companyCik: string, accessionNumber?: string, primaryDocument?: string) => {
    if (!accessionNumber || !primaryDocument) {
      return null;
    }
    const cleanedAccession = accessionNumber.replace(/-/g, '');
    const archiveCik = normalizeCikForArchive(companyCik);
    return `https://www.sec.gov/Archives/edgar/data/${archiveCik}/${cleanedAccession}/${primaryDocument}`;
  };

  const getSecFilingFolderUrl = (companyCik: string, accessionNumber?: string) => {
    if (!accessionNumber) {
      return null;
    }
    const cleanedAccession = accessionNumber.replace(/-/g, '');
    const archiveCik = normalizeCikForArchive(companyCik);
    return `https://www.sec.gov/Archives/edgar/data/${archiveCik}/${cleanedAccession}/`;
  };

  const handleSubmissionSyncLocal = async () => {
    if (!submission?.cik) {
      setSubmissionActionError('No submission loaded to sync.');
      return;
    }
    setSyncingSubmission(true);
    setSubmissionActionError(null);
    setSubmissionActionMessage(null);
    try {
      const job = await downloadsApi.downloadSubmissions(submission.cik);
      setSubmissionActionMessage(`Sync queued for ${submission.companyName} (${submission.cik}). Waiting for job ${job.id}...`);

      const completedJob = await waitForDownloadJob(job.id);
      if (!completedJob) {
        setSubmissionActionMessage(`Sync queued for ${submission.companyName} (${submission.cik}). Job ${job.id} is still running. Check Downloads for progress.`);
        return;
      }

      if (completedJob.status === 'COMPLETED') {
        setSubmissionActionMessage(`Sync completed for ${submission.companyName} (${submission.cik}). Imported ${completedJob.filesDownloaded} filing records.`);
        return;
      }

      setSubmissionActionError(completedJob.error || `Sync ${completedJob.status.toLowerCase()} for ${submission.companyName} (${submission.cik}).`);
    } catch (error) {
      setSubmissionActionError(error instanceof Error ? error.message : 'Failed to queue local sync');
    } finally {
      setSyncingSubmission(false);
    }
  };

  const availableFormTypes = submission
    ? Array.from(
      new Set(
        submission.recentFilings
          .map((filing) => filing.formType)
          .filter((form): form is string => Boolean(form && form.trim()))
      )
    )
    : [];

  const filteredRecentFilings = submission
    ? submission.recentFilings.filter(
      (filing) => submissionFormFilter === 'ALL' || filing.formType === submissionFormFilter
    )
    : [];

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4 flex items-center gap-2">
          <Search className="w-5 h-5" />
          Remote EDGAR Explorer
        </h2>
        <p className="text-sm text-gray-600">
          Query SEC endpoints live without relying on local MongoDB sync.
        </p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <h3 className="text-lg">Remote Tickers</h3>
        <form
          className="grid grid-cols-1 md:grid-cols-4 gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            searchTickers();
          }}
        >
          <input
            type="text"
            value={tickerQuery}
            onChange={(e) => setTickerQuery(e.target.value)}
            placeholder="Search ticker, name, or CIK"
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <select
            value={tickerSource}
            onChange={(e) => setTickerSource(e.target.value as 'all' | 'exchanges' | 'mf')}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="all">All</option>
            <option value="exchanges">Exchanges</option>
            <option value="mf">Mutual Funds</option>
          </select>
          <input
            type="number"
            min={1}
            max={500}
            value={tickerLimit}
            onChange={(e) => setTickerLimit(Number(e.target.value || 50))}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <button
            type="submit"
            disabled={tickersLoading}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50"
          >
            Search Tickers
          </button>
        </form>

        {tickersError && <ErrorMessage message={tickersError} onRetry={searchTickers} />}
        {tickerActionError && (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
            {tickerActionError}
          </div>
        )}
        {tickerActionMessage && (
          <div className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-md px-3 py-2">
            {tickerActionMessage}
          </div>
        )}
        {tickersLoading && <LoadingSpinner size="md" text="Loading remote tickers..." />}

        {!tickersLoading && tickers.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-3 py-2">Ticker</th>
                  <th className="text-left px-3 py-2">Company</th>
                  <th className="text-left px-3 py-2">CIK</th>
                  <th className="text-left px-3 py-2">Exchange</th>
                  <th className="text-left px-3 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {tickers.map((ticker) => (
                  <tr
                    key={`${ticker.cik}-${ticker.ticker}`}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => handleExploreTicker(ticker)}
                    title="Explore ticker (load remote submissions by CIK)"
                  >
                    <td className="px-3 py-2 font-mono">{ticker.ticker}</td>
                    <td className="px-3 py-2">{ticker.name}</td>
                    <td className="px-3 py-2 font-mono">{ticker.cik}</td>
                    <td className="px-3 py-2">{ticker.exchange || '-'}</td>
                    <td className="px-3 py-2">
                      <div className="flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={() => handleExploreTicker(ticker)}
                          className="px-2 py-1 text-xs rounded bg-[#1a1f36] text-white hover:bg-[#252b47]"
                          title="Load remote submissions for this ticker"
                        >
                          Explore
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSyncLocal(ticker)}
                          disabled={syncingCik === ticker.cik}
                          className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                          title="Queue local submissions sync for this CIK"
                        >
                          {syncingCik === ticker.cik ? 'Syncing...' : 'Sync Local'}
                        </button>
                        <a
                          href={getSecCompanyUrl(ticker.cik)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                          title="Open SEC company filings page"
                        >
                          Open SEC
                        </a>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-2">
          <div>
            <h3 className="text-lg">Remote Filing Search</h3>
            <p className="text-sm text-gray-600">
              Search SEC daily master indexes live by form and date range, then queue a local sync for all matching companies.
            </p>
          </div>
          {remoteFilingSearchResult && (
            <button
              type="button"
              onClick={() => void handleRemoteFilingSyncLocal()}
              disabled={syncingRemoteFilings || remoteFilingSearchResult.totalMatches === 0}
              className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50 disabled:cursor-not-allowed"
              title="Queue a local submissions sync for every company matching this remote search"
            >
              {syncingRemoteFilings ? 'Syncing...' : `Sync ${remoteFilingSearchResult.uniqueCompanyCount} Companies`}
            </button>
          )}
        </div>

        <form
          className="grid grid-cols-1 md:grid-cols-5 gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void searchRemoteFilings();
          }}
        >
          <div className="md:col-span-2">
            <input
              type="text"
              value={remoteFormType}
              onChange={(e) => setRemoteFormType(e.target.value)}
              placeholder="e.g. 13F, 4, 10-K, SC 13D"
              list="remote-form-type-options"
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            />
            <datalist id="remote-form-type-options">
              {FORM_TYPES.map((formType) => (
                <option key={formType.value} value={formType.value} />
              ))}
            </datalist>
          </div>
          <select
            value={remotePeriodPreset}
            onChange={(e) => applyRemotePreset(e.target.value as RemotePeriodPreset)}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            {REMOTE_PERIOD_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            max={500}
            value={remoteSearchLimit}
            onChange={(e) => setRemoteSearchLimit(Number(e.target.value || 100))}
            className="px-3 py-2 border border-gray-300 rounded-md"
            title="Preview row limit"
          />
          <button
            type="submit"
            disabled={remoteFilingSearchLoading}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50"
          >
            Search Filings
          </button>
          <input
            type="date"
            value={remoteDateFrom}
            onChange={(e) => {
              setRemotePeriodPreset('CUSTOM');
              setRemoteDateFrom(e.target.value);
            }}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <input
            type="date"
            value={remoteDateTo}
            onChange={(e) => {
              setRemotePeriodPreset('CUSTOM');
              setRemoteDateTo(e.target.value);
            }}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <div className="md:col-span-3 flex items-center text-sm text-gray-500">
            Preview rows are capped by the limit above. Sync uses the full search criteria, not only the preview rows.
          </div>
        </form>

        {remoteFilingSearchError && <ErrorMessage message={remoteFilingSearchError} onRetry={searchRemoteFilings} />}
        {remoteFilingActionError && (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
            {remoteFilingActionError}
          </div>
        )}
        {remoteFilingActionMessage && (
          <div className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-md px-3 py-2">
            {remoteFilingActionMessage}
          </div>
        )}
        {remoteFilingSearchLoading && <LoadingSpinner size="md" text="Scanning SEC daily master indexes..." />}

        {!remoteFilingSearchLoading && remoteFilingSearchResult && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
              <div className="border border-gray-200 rounded-md p-3">
                <div className="text-xs text-gray-500">Matching filings</div>
                <div className="text-lg">{remoteFilingSearchResult.totalMatches.toLocaleString()}</div>
              </div>
              <div className="border border-gray-200 rounded-md p-3">
                <div className="text-xs text-gray-500">Matching companies</div>
                <div className="text-lg">{remoteFilingSearchResult.uniqueCompanyCount.toLocaleString()}</div>
              </div>
              <div className="border border-gray-200 rounded-md p-3">
                <div className="text-xs text-gray-500">Daily indexes found</div>
                <div className="text-lg">
                  {remoteFilingSearchResult.availableDateCount} / {remoteFilingSearchResult.searchedDateCount}
                </div>
              </div>
              <div className="border border-gray-200 rounded-md p-3">
                <div className="text-xs text-gray-500">Preview rows</div>
                <div className="text-lg">
                  {remoteFilingSearchResult.returnedMatches.toLocaleString()}
                  {remoteFilingSearchResult.truncated ? ' (truncated)' : ''}
                </div>
              </div>
            </div>

            {remoteFilingSearchResult.unavailableDateCount > 0 && (
              <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                {remoteFilingSearchResult.unavailableDateCount} daily index date(s) were unavailable and were skipped. This is normal for weekends, market holidays, or missing SEC index files.
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-3 py-2">Filing Date</th>
                    <th className="text-left px-3 py-2">Form</th>
                    <th className="text-left px-3 py-2">Company</th>
                    <th className="text-left px-3 py-2">CIK</th>
                    <th className="text-left px-3 py-2">Accession</th>
                    <th className="text-left px-3 py-2">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {remoteFilingSearchResult.filings.length > 0 ? (
                    remoteFilingSearchResult.filings.map((filing) => (
                      <tr key={`${filing.cik}-${filing.accessionNumber || filing.archivePath}`}>
                        <td className="px-3 py-2">{filing.filingDate}</td>
                        <td className="px-3 py-2">{filing.formType}</td>
                        <td className="px-3 py-2">{filing.companyName}</td>
                        <td className="px-3 py-2 font-mono">{filing.cik}</td>
                        <td className="px-3 py-2 font-mono text-xs">{filing.accessionNumber || '-'}</td>
                        <td className="px-3 py-2">
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => void loadSubmissionByCik(filing.cik)}
                              className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                            >
                              Explore
                            </button>
                            <a
                              href={filing.filingUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                            >
                              Open SEC
                            </a>
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={6} className="px-3 py-4 text-sm text-gray-500 text-center">
                        No remote filings matched the selected form and date range.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <h3 className="text-lg">Remote Submissions by CIK</h3>
        <form
          className="grid grid-cols-1 md:grid-cols-4 gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            loadSubmission();
          }}
        >
          <input
            type="text"
            value={cik}
            onChange={(e) => setCik(e.target.value)}
            placeholder="e.g. 0000789019"
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <input
            type="number"
            min={1}
            max={200}
            value={filingsLimit}
            onChange={(e) => setFilingsLimit(Number(e.target.value || 25))}
            className="px-3 py-2 border border-gray-300 rounded-md"
          />
          <div />
          <button
            type="submit"
            disabled={submissionLoading}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50"
          >
            Load Submission
          </button>
        </form>

        {submissionError && <ErrorMessage message={submissionError} onRetry={loadSubmission} />}
        {submissionActionError && (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
            {submissionActionError}
          </div>
        )}
        {submissionActionMessage && (
          <div className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-md px-3 py-2">
            {submissionActionMessage}
          </div>
        )}
        {submissionLoading && <LoadingSpinner size="md" text="Loading submission..." />}

        {!submissionLoading && submission && (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={handleSubmissionSyncLocal}
                disabled={syncingSubmission}
                className="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                title="Queue local submissions sync for this CIK"
              >
                {syncingSubmission ? 'Syncing...' : 'Sync Local'}
              </button>
              <a
                href={getSecCompanyUrl(submission.cik)}
                target="_blank"
                rel="noopener noreferrer"
                className="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                title="Open SEC company filings page"
              >
                Open SEC
              </a>
              <a
                href={getSecSubmissionJsonUrl(submission.cik)}
                target="_blank"
                rel="noopener noreferrer"
                className="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                title="Open SEC submissions JSON"
              >
                Open JSON
              </a>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <div className="text-xs text-gray-500">Company</div>
                <div>{submission.companyName}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">CIK</div>
                <div className="font-mono">{submission.cik}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">Tickers</div>
                <div>{submission.tickers?.join(', ') || '-'}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">SIC</div>
                <div>{submission.sic || '-'} {submission.sicDescription ? `- ${submission.sicDescription}` : ''}</div>
              </div>
            </div>

            <div>
              <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 mb-2">
                <div className="text-sm">
                  Recent filings ({filteredRecentFilings.length} / {submission.recentFilingsCount})
                </div>
                <div className="flex items-center gap-2">
                  <label htmlFor="submission-form-filter" className="text-sm text-gray-600">Form filter</label>
                  <select
                    id="submission-form-filter"
                    value={submissionFormFilter}
                    onChange={(e) => setSubmissionFormFilter(e.target.value)}
                    className="px-2 py-1 border border-gray-300 rounded-md text-sm"
                  >
                    <option value="ALL">All Forms</option>
                    {availableFormTypes.map((form) => (
                      <option key={form} value={form}>{form}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="text-left px-3 py-2">Form</th>
                      <th className="text-left px-3 py-2">Filing Date</th>
                      <th className="text-left px-3 py-2">Accession</th>
                      <th className="text-left px-3 py-2">Primary Document</th>
                      <th className="text-left px-3 py-2">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {filteredRecentFilings.length > 0 ? (
                      filteredRecentFilings.map((filing) => (
                        <tr key={filing.accessionNumber || `${filing.formType}-${filing.filingDate}`}>
                          <td className="px-3 py-2">{filing.formType || '-'}</td>
                          <td className="px-3 py-2">{filing.filingDate || '-'}</td>
                          <td className="px-3 py-2 font-mono text-xs">{filing.accessionNumber || '-'}</td>
                          <td className="px-3 py-2">{filing.primaryDocument || '-'}</td>
                          <td className="px-3 py-2">
                            <div className="flex flex-wrap gap-2">
                              {getSecFilingDocumentUrl(submission.cik, filing.accessionNumber, filing.primaryDocument) ? (
                                <a
                                  href={getSecFilingDocumentUrl(submission.cik, filing.accessionNumber, filing.primaryDocument) || '#'}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                                  title="Open filing primary document on SEC"
                                >
                                  Open Doc
                                </a>
                              ) : (
                                <span className="px-2 py-1 text-xs rounded border border-gray-200 text-gray-400">Open Doc</span>
                              )}
                              {getSecFilingFolderUrl(submission.cik, filing.accessionNumber) ? (
                                <a
                                  href={getSecFilingFolderUrl(submission.cik, filing.accessionNumber) || '#'}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-50"
                                  title="Open filing folder on SEC"
                                >
                                  Open Folder
                                </a>
                              ) : (
                                <span className="px-2 py-1 text-xs rounded border border-gray-200 text-gray-400">Open Folder</span>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={5} className="px-3 py-4 text-sm text-gray-500 text-center">
                          No filings found for selected form filter.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
