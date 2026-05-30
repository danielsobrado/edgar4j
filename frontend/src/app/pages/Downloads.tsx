import React, { useEffect } from 'react';
import { Download, Clock, CheckCircle, Loader, HardDrive, XCircle, AlertCircle, RefreshCw } from 'lucide-react';
import * as Progress from '@radix-ui/react-progress';
import { useDownloads } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import type { DownloadJob } from '../api';
import { showError, showSuccess } from '../store/notificationStore';
import { POLLING } from '../config/constants';

type TickerDownloadType = 'TICKERS_ALL' | 'TICKERS_NYSE' | 'TICKERS_NASDAQ' | 'TICKERS_MF';
type BulkDownloadType = 'BULK_SUBMISSIONS' | 'BULK_COMPANY_FACTS';

const TICKER_TYPES: readonly TickerDownloadType[] = [
  'TICKERS_ALL',
  'TICKERS_NYSE',
  'TICKERS_NASDAQ',
  'TICKERS_MF',
];

export function Downloads() {
  const [cikInput, setCikInput] = React.useState('');
  const [bulkStartingType, setBulkStartingType] = React.useState<BulkDownloadType | null>(null);

  const {
    jobs,
    summary,
    loading,
    summaryLoading,
    error,
    summaryError,
    downloadTickers,
    downloadSubmissions,
    downloadBulk,
    cancelJob,
    refresh
  } = useDownloads();

  useEffect(() => {
    const hasInProgress = jobs.some(j => j.status === 'IN_PROGRESS' || j.status === 'PENDING');
    if (hasInProgress) {
      const interval = setInterval(refresh, POLLING.ACTIVE_JOBS_MS);
      return () => clearInterval(interval);
    }
  }, [jobs, refresh]);

  const handleDownloadTickers = async (type: TickerDownloadType = 'TICKERS_ALL') => {
    try {
      await downloadTickers(type);
      const labelByType: Record<TickerDownloadType, string> = {
        TICKERS_ALL: 'All tickers',
        TICKERS_NYSE: 'NYSE tickers',
        TICKERS_NASDAQ: 'NASDAQ tickers',
        TICKERS_MF: 'Mutual fund tickers',
      };
      showSuccess('Download Started', `${labelByType[type]} download has been queued`);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start ticker download';
      showError('Download Failed', message);
    }
  };

  const handleDownloadSubmissions = async () => {
    if (!cikInput.trim()) return;
    try {
      const cik = cikInput.trim();
      await downloadSubmissions(cik);
      showSuccess('Download Started', `Submissions download for CIK ${cik} has been queued`);
      setCikInput('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start submissions download';
      showError('Download Failed', message);
    }
  };

  const handleBulkDownload = async (type: BulkDownloadType) => {
    setBulkStartingType(type);
    try {
      const job = await downloadBulk(type);
      const labelByType: Record<BulkDownloadType, string> = {
        BULK_SUBMISSIONS: 'All submissions archive',
        BULK_COMPANY_FACTS: 'Company facts XBRL archive',
      };
      await refresh();
      showSuccess(
        'Download Queued',
        `${labelByType[type]} job ${job.id} has been queued. The archive will be saved on the backend.`
      );
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start bulk download';
      showError('Download Failed', message);
    } finally {
      setBulkStartingType(null);
    }
  };

  const handleCancelJob = async (jobId: string) => {
    try {
      await cancelJob(jobId);
      showSuccess('Job Cancelled', 'The download job has been cancelled');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to cancel job';
      showError('Cancel Failed', message);
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'IN_PROGRESS':
        return <Loader className="w-4 h-4 text-blue-500 animate-spin" />;
      case 'FAILED':
        return <XCircle className="w-4 h-4 text-red-500" />;
      case 'CANCELLED':
        return <AlertCircle className="w-4 h-4 text-gray-500" />;
      default:
        return <Clock className="w-4 h-4 text-gray-400" />;
    }
  };

  const getStatusText = (job: DownloadJob) => {
    switch (job.status) {
      case 'COMPLETED':
        return `Completed - ${job.filesDownloaded?.toLocaleString() || 0} files`;
      case 'IN_PROGRESS':
        return `In progress - ${job.progress || 0}%`;
      case 'FAILED':
        return `Failed: ${job.error || 'Unknown error'}`;
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return 'Pending';
    }
  };

  const formatTimestamp = (timestamp?: string) => {
    if (!timestamp) return '';
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  const isBulkJob = (job: DownloadJob) => job.type === 'BULK_SUBMISSIONS' || job.type === 'BULK_COMPANY_FACTS';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="flex items-center gap-2">
          <Download className="w-8 h-8" />
          Downloads & Bulk Data
        </h1>
        <button
          onClick={refresh}
          disabled={loading || summaryLoading}
          className="px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-md flex items-center gap-2"
        >
          <RefreshCw className={`w-4 h-4 ${loading || summaryLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {(error || summaryError) && (
        <ErrorMessage
          message={error || summaryError || 'Failed to load downloads'}
          onRetry={refresh}
        />
      )}

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Company Tickers</h2>
        <p className="text-gray-600 mb-4">
          Download the complete list of company tickers and CIK numbers from the SEC.
        </p>
        <div className="flex flex-wrap gap-3 mb-4">
          <button
            onClick={() => void handleDownloadTickers('TICKERS_ALL')}
            disabled={loading}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            All Tickers
          </button>
          <button
            onClick={() => void handleDownloadTickers('TICKERS_NYSE')}
            disabled={loading}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NYSE
          </button>
          <button
            onClick={() => void handleDownloadTickers('TICKERS_NASDAQ')}
            disabled={loading}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NASDAQ
          </button>
          <button
            onClick={() => void handleDownloadTickers('TICKERS_MF')}
            disabled={loading}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            Mutual Funds
          </button>
        </div>
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Clock className="w-4 h-4" />
          {summaryLoading ? (
            <span>Loading ticker summary...</span>
          ) : summary?.lastTickerUpdate ? (
            <>
              <span>Last updated: {formatTimestamp(summary.lastTickerUpdate)}</span>
              <span className="ml-2">|</span>
              <span>{summary.tickerRecordsImported.toLocaleString()} ticker records imported</span>
            </>
          ) : (
            <span>No data downloaded yet</span>
          )}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Bulk Submissions</h2>
        <p className="text-gray-600 mb-4">
          Download all filings for a specific company by CIK number.
        </p>
        <div className="flex gap-3 mb-4">
          <input
            type="text"
            value={cikInput}
            onChange={(e) => setCikInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && void handleDownloadSubmissions()}
            placeholder="Enter CIK (e.g., 0000320193)"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            onClick={() => void handleDownloadSubmissions()}
            disabled={!cikInput.trim() || loading}
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            Download
          </button>
        </div>
        <p className="text-sm text-gray-500">
          Note: Bulk downloads can be large. Download will include all historical filings for the specified company.
        </p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">SEC Bulk Data Files</h2>
        <p className="text-gray-600 mb-4">
          Queue large SEC archive downloads to run on the backend. Completed ZIPs are saved locally on the server.
        </p>
        <div className="space-y-3 mb-4">
          <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
            <div className="flex-1">
              <p>Company Facts (XBRL)</p>
              <p className="text-sm text-gray-600">Financial data in XBRL format for all companies</p>
            </div>
            <button
              onClick={() => void handleBulkDownload('BULK_COMPANY_FACTS')}
              disabled={bulkStartingType === 'BULK_COMPANY_FACTS'}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
            >
              {bulkStartingType === 'BULK_COMPANY_FACTS' ? (
                <Loader className="w-4 h-4 animate-spin" />
              ) : (
                <Download className="w-4 h-4" />
              )}
              Queue Download
            </button>
          </div>

          <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
            <div className="flex-1">
              <p>All Submissions Archive</p>
              <p className="text-sm text-gray-600">Complete archive of all SEC submissions</p>
            </div>
            <button
              onClick={() => void handleBulkDownload('BULK_SUBMISSIONS')}
              disabled={bulkStartingType === 'BULK_SUBMISSIONS'}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
            >
              {bulkStartingType === 'BULK_SUBMISSIONS' ? (
                <Loader className="w-4 h-4 animate-spin" />
              ) : (
                <Download className="w-4 h-4" />
              )}
              Queue Download
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2 p-3 bg-gray-50 rounded-lg">
          <HardDrive className="w-5 h-5 text-gray-600" />
          <div className="flex-1">
            <div className="flex items-center justify-between mb-1">
              <span className="text-sm text-gray-700">Estimated disk space needed</span>
              <span className="text-sm">~25 GB</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div className="bg-blue-500 h-2 rounded-full" style={{ width: '35%' }} />
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Download Status</h2>

        {loading && jobs.length === 0 ? (
          <div className="py-8">
            <LoadingSpinner size="md" text="Loading download jobs..." />
          </div>
        ) : jobs.length === 0 ? (
          <EmptyState
            type="downloads"
            message="No download jobs yet. Start a download to see job status here."
          />
        ) : (
          <div className="space-y-4">
            {jobs.map(job => {
              const canRetryTicker = TICKER_TYPES.includes(job.type as TickerDownloadType);
              const canRetryBulk = job.type === 'BULK_SUBMISSIONS' || job.type === 'BULK_COMPANY_FACTS';

              return (
                <div key={job.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <p>{job.type}</p>
                        {getStatusIcon(job.status)}
                      </div>
                      <p className="text-sm text-gray-600">{getStatusText(job)}</p>
                    </div>
                    <span className="text-xs text-gray-500 font-mono">
                      {formatTimestamp(job.startedAt)}
                    </span>
                  </div>

                  {job.status === 'IN_PROGRESS' && (
                    <div className="mt-2">
                      <Progress.Root className="relative overflow-hidden bg-gray-200 rounded-full w-full h-2">
                        <Progress.Indicator
                          className="bg-blue-500 h-full transition-transform duration-300"
                          style={{ transform: `translateX(-${100 - (job.progress || 0)}%)` }}
                        />
                      </Progress.Root>
                    </div>
                  )}

                  {job.status === 'FAILED' && isBulkJob(job) && (
                    <div className="mt-3 flex gap-2">
                      <div className="w-full rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                        {job.error || 'Bulk download failed'}
                      </div>
                    </div>
                  )}
                  {job.status === 'COMPLETED' && isBulkJob(job) && (
                    <div className="mt-3 flex gap-2">
                      <div className="w-full space-y-1 rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-700">
                        <p>{job.filesDownloaded?.toLocaleString() || 0} file{job.filesDownloaded === 1 ? '' : 's'} imported or saved.</p>
                        {job.outputPath && <p className="break-all">Saved ZIP: {job.outputPath}</p>}
                        {job.sourceUrl && <p className="break-all">Source URL: {job.sourceUrl}</p>}
                      </div>
                    </div>
                  )}

                  <div className="mt-3 flex gap-2">
                    {(job.status === 'IN_PROGRESS' || job.status === 'PENDING') && (
                      <button
                        onClick={() => void handleCancelJob(job.id)}
                        className="px-3 py-1 text-sm text-red-600 hover:bg-red-50 rounded-md"
                      >
                        Cancel
                      </button>
                    )}
                    {job.status === 'FAILED' && (canRetryTicker || canRetryBulk) && (
                      <button
                        onClick={() => {
                          if (canRetryTicker) {
                            void handleDownloadTickers(job.type as TickerDownloadType);
                            return;
                          }
                          if (canRetryBulk) {
                            void handleBulkDownload(job.type as BulkDownloadType);
                          }
                        }}
                        className="px-3 py-1 text-sm border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1"
                      >
                        <RefreshCw className="w-3 h-3" />
                        Retry
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
