import React from 'react';
import { Download, Clock, CheckCircle, Loader, HardDrive } from 'lucide-react';
import { downloadJobs } from '../data/mockData';
import * as Progress from '@radix-ui/react-progress';

export function Downloads() {
  const [cikInput, setCikInput] = React.useState('');
  
  const handleDownload = (type: string) => {
    console.log(`Downloading ${type}...`);
    alert(`Starting download: ${type}`);
  };
  
  return (
    <div className="space-y-6">
      <h1 className="flex items-center gap-2">
        <Download className="w-8 h-8" />
        Downloads & Bulk Data
      </h1>
      
      {/* Company Tickers */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Company Tickers</h2>
        <p className="text-gray-600 mb-4">
          Download the complete list of company tickers and CIK numbers from the SEC.
        </p>
        <div className="flex flex-wrap gap-3 mb-4">
          <button
            onClick={() => handleDownload('All Tickers')}
            className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            All Tickers
          </button>
          <button
            onClick={() => handleDownload('NYSE Tickers')}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NYSE
          </button>
          <button
            onClick={() => handleDownload('NASDAQ Tickers')}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            NASDAQ
          </button>
          <button
            onClick={() => handleDownload('Mutual Funds')}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            Mutual Funds
          </button>
        </div>
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Clock className="w-4 h-4" />
          <span>Last updated: 2024-12-25 07:00:00</span>
          <span className="ml-2">•</span>
          <span>8,342 companies</span>
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
            placeholder="Enter CIK (e.g., 0000320193)"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            onClick={() => handleDownload(`Bulk Submissions - CIK ${cikInput}`)}
            disabled={!cikInput}
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            Download
          </button>
        </div>
        <p className="text-sm text-gray-500">
          Note: Bulk downloads can be large (100MB+). Download will include all historical filings for the specified company.
        </p>
      </div>
      
      {/* Bulk Data */}
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
              onClick={() => handleDownload('Company Facts')}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
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
              onClick={() => handleDownload('Submissions Archive')}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
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
        <div className="space-y-4">
          {downloadJobs.map(job => (
            <div key={job.id} className="border border-gray-200 rounded-lg p-4">
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p>{job.type}</p>
                    {job.status === 'completed' && <CheckCircle className="w-4 h-4 text-green-500" />}
                    {job.status === 'in_progress' && <Loader className="w-4 h-4 text-blue-500 animate-spin" />}
                  </div>
                  <p className="text-sm text-gray-600">
                    {job.status === 'completed' && `Completed • ${job.size}`}
                    {job.status === 'in_progress' && `In progress • ${job.progress}%`}
                    {job.status === 'pending' && 'Pending'}
                  </p>
                </div>
                <span className="text-xs text-gray-500 font-mono">{job.timestamp}</span>
              </div>
              
              {job.status === 'in_progress' && (
                <div className="mt-2">
                  <Progress.Root className="relative overflow-hidden bg-gray-200 rounded-full w-full h-2">
                    <Progress.Indicator
                      className="bg-blue-500 h-full transition-transform duration-300"
                      style={{ transform: `translateX(-${100 - job.progress}%)` }}
                    />
                  </Progress.Root>
                </div>
              )}
              
              {job.status === 'completed' && (
                <div className="mt-3 flex gap-2">
                  <button className="px-3 py-1 text-sm border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1">
                    <Download className="w-3 h-3" />
                    Download Again
                  </button>
                  <button className="px-3 py-1 text-sm text-red-600 hover:bg-red-50 rounded-md">
                    Delete
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
