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

  const {
    jobs,
    loading,
    error,
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

  useEffect(() => {
    refresh();
  }, [refresh]);

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
    try {
      await downloadBulk(type);
      const labelByType: Record<BulkDownloadType, string> = {
        BULK_SUBMISSIONS: 'All submissions archive',
        BULK_COMPANY_FACTS: 'Company facts XBRL archive',
      };
      showSuccess('Download Started', `${labelByType[type]} download has been queued`);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start bulk download';
      showError('Download Failed', message);
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

  const completedJobs = jobs.filter(j => j.status === 'COMPLETED');
  const totalFilesDownloaded = completedJobs.reduce((sum, j) => sum + (j.filesDownloaded || 0), 0);
  const lastUpdate = completedJobs.length > 0
    ? completedJobs.sort((a, b) => new Date(b.completedAt || 0).getTime() - new Date(a.completedAt || 0).getTime())[0]
    : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="flex items-center gap-2">
          <Download className="w-8 h-8" />
          Downloads & Bulk Data
        </h1>
        <button
          onClick={refresh}
          disabled={loading}
          className="px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-md flex items-center gap-2"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {error && (
        <ErrorMessage
          message={error}
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
          {lastUpdate ? (
            <>
              <span>Last updated: {formatTimestamp(lastUpdate.completedAt)}</span>
              <span className="ml-2">|</span>
              <span>{totalFilesDownloaded.toLocaleString()} files downloaded</span>
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
          Download comprehensive datasets from the SEC's bulk data repository.
        </p>
        <div className="space-y-3 mb-4">
          <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
            <div className="flex-1">
              <p>Company Facts (XBRL)</p>
              <p className="text-sm text-gray-600">Financial data in XBRL format for all companies</p>
            </div>
            <button
              onClick={() => void handleBulkDownload('BULK_COMPANY_FACTS')}
              disabled={loading}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              Download ZIP
            </button>
          </div>

          <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
            <div className="flex-1">
              <p>All Submissions Archive</p>
              <p className="text-sm text-gray-600">Complete archive of all SEC submissions</p>
            </div>
            <button
              onClick={() => void handleBulkDownload('BULK_SUBMISSIONS')}
              disabled={loading}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              Download ZIP
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
