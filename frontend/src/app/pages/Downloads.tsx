import React, { useEffect } from 'react';
import { Download, Clock, CheckCircle, Loader, HardDrive, XCircle, AlertCircle, RefreshCw } from 'lucide-react';
import * as Progress from '@radix-ui/react-progress';
import { useDownloads } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { DownloadJobResponse } from '../api';

export function Downloads() {
  const [cikInput, setCikInput] = React.useState('');

  const {
    jobs,
    loading,
    error,
    downloadTickers,
    downloadSubmissions,
    cancelJob,
    refresh
  } = useDownloads();

  // Auto-refresh jobs every 5 seconds if there are in-progress jobs
  useEffect(() => {
    const hasInProgress = jobs.some(j => j.status === 'IN_PROGRESS' || j.status === 'PENDING');
    if (hasInProgress) {
      const interval = setInterval(refresh, 5000);
      return () => clearInterval(interval);
    }
  }, [jobs, refresh]);

  // Load jobs on mount
  useEffect(() => {
    refresh();
  }, []);

  const handleDownloadTickers = async (exchange?: string) => {
    try {
      await downloadTickers(exchange);
    } catch (err) {
      console.error('Failed to start ticker download:', err);
    }
  };

  const handleDownloadSubmissions = async () => {
    if (!cikInput.trim()) return;
    try {
      await downloadSubmissions(cikInput.trim());
      setCikInput('');
    } catch (err) {
      console.error('Failed to start submissions download:', err);
    }
  };

  const handleCancelJob = async (jobId: string) => {
    try {
      await cancelJob(jobId);
    } catch (err) {
      console.error('Failed to cancel job:', err);
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

  const getStatusText = (job: DownloadJobResponse) => {
    switch (job.status) {
      case 'COMPLETED':
        return `Completed • ${job.recordsProcessed?.toLocaleString() || 0} records`;
      case 'IN_PROGRESS':
        return `In progress • ${job.progress || 0}%`;
      case 'FAILED':
        return `Failed: ${job.errorMessage || 'Unknown error'}`;
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

  // Calculate statistics from completed jobs
  const completedJobs = jobs.filter(j => j.status === 'COMPLETED');
  const totalRecords = completedJobs.reduce((sum, j) => sum + (j.recordsProcessed || 0), 0);
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

      {/* Error Message */}
      {error && (
        <ErrorMessage
          message={error}
          onRetry={refresh}
        />
      )}

      {/* Company Tickers */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Company Tickers</h2>
        <p className="text-gray-600 mb-4">
          Download the complete list of company tickers and CIK numbers from the SEC.
        </p>
        <div className="flex flex-wrap gap-3 mb-4">
          <button
            onClick={() => handleDownloadTickers()}
            disabled={loading}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            All Tickers
          </button>
          <button
            onClick={() => handleDownloadTickers('NYSE')}
            disabled={loading}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NYSE
          </button>
          <button
            onClick={() => handleDownloadTickers('NASDAQ')}
            disabled={loading}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 disabled:opacity-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NASDAQ
          </button>
          <button
            onClick={() => handleDownloadTickers('MUTUAL_FUND')}
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
              <span className="ml-2">•</span>
              <span>{totalRecords.toLocaleString()} companies</span>
            </>
          ) : (
            <span>No data downloaded yet</span>
          )}
        </div>
      </div>

      {/* Bulk Submissions */}
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
            onKeyPress={(e) => e.key === 'Enter' && handleDownloadSubmissions()}
            placeholder="Enter CIK (e.g., 0000320193)"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            onClick={handleDownloadSubmissions}
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

      {/* SEC Bulk Data */}
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
              onClick={() => handleDownloadTickers('XBRL')}
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
              onClick={() => handleDownloadSubmissions('ALL')}
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

      {/* Download Status */}
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
            {jobs.map(job => (
              <div key={job.id} className="border border-gray-200 rounded-lg p-4">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <p>{job.type}</p>
                      {getStatusIcon(job.status)}
                    </div>
                    <p className="text-sm text-gray-600">{getStatusText(job)}</p>
                    {job.parameters && (
                      <p className="text-xs text-gray-500 font-mono mt-1">
                        {typeof job.parameters === 'string'
                          ? job.parameters
                          : JSON.stringify(job.parameters)}
                      </p>
                    )}
                  </div>
                  <span className="text-xs text-gray-500 font-mono">
                    {formatTimestamp(job.startedAt || job.createdAt)}
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
                      onClick={() => handleCancelJob(job.id)}
                      className="px-3 py-1 text-sm text-red-600 hover:bg-red-50 rounded-md"
                    >
                      Cancel
                    </button>
                  )}
                  {job.status === 'FAILED' && (
                    <button
                      onClick={() => job.type === 'TICKERS'
                        ? handleDownloadTickers(job.parameters?.exchange)
                        : handleDownloadSubmissions(job.parameters?.cik || '')}
                      className="px-3 py-1 text-sm border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1"
                    >
                      <RefreshCw className="w-3 h-3" />
                      Retry
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
