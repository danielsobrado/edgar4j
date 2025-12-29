import React, { useCallback } from 'react';
import { Search, Globe, Calendar, Building2, ExternalLink, RefreshCw, FileText } from 'lucide-react';
import { useRecentForm6K, useForm6KSearch } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form6K as Form6KType } from '../api/types';

function FilingCard({ filing, onClick }: { filing: Form6KType; onClick: () => void }) {
  const exhibitsCount = filing.exhibits?.length || 0;
  const hasReportText = filing.reportText && filing.reportText.length > 0;

  return (
    <div
      className="bg-white rounded-lg shadow-sm p-4 hover:shadow-md transition-shadow cursor-pointer border border-gray-100"
      onClick={onClick}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            {filing.tradingSymbol && (
              <span className="font-bold text-blue-600">{filing.tradingSymbol}</span>
            )}
            <span className="px-2 py-0.5 bg-cyan-100 text-cyan-700 rounded text-xs">{filing.formType}</span>
          </div>
          <h3 className="font-medium text-gray-900 line-clamp-1">{filing.companyName || `CIK: ${filing.cik}`}</h3>
        </div>
        <Globe className="w-5 h-5 text-cyan-500" />
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm mb-3">
        <div>
          <p className="text-gray-500">Filed</p>
          <p className="font-medium">{filing.filedDate}</p>
        </div>
        {filing.reportDate && (
          <div>
            <p className="text-gray-500">Report Date</p>
            <p className="font-medium">{filing.reportDate}</p>
          </div>
        )}
      </div>

      <div className="flex items-center justify-between text-sm">
        <div className="flex items-center gap-3">
          {exhibitsCount > 0 && (
            <span className="text-gray-600">{exhibitsCount} exhibit{exhibitsCount !== 1 ? 's' : ''}</span>
          )}
          {hasReportText && (
            <span className="text-green-600 flex items-center gap-1">
              <FileText className="w-3 h-3" />
              Content
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function FilingDetail({ filing, onClose }: { filing: Form6KType; onClose: () => void }) {
  const secDocumentUrl = `https://www.sec.gov/Archives/edgar/data/${filing.cik.replace(/^0+/, '')}/${filing.accessionNumber.replace(/-/g, '')}/${filing.primaryDocument || 'index.html'}`;

  return (
    <div className="bg-white rounded-lg shadow-sm">
      <div className="border-b border-gray-200 p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 className="text-lg font-semibold">{filing.companyName || `CIK: ${filing.cik}`}</h2>
              {filing.tradingSymbol && (
                <span className="text-blue-600 font-bold">{filing.tradingSymbol}</span>
              )}
              <span className="px-2 py-1 bg-cyan-100 text-cyan-700 rounded text-sm">{filing.formType}</span>
            </div>
            <p className="text-gray-500 text-sm">
              Foreign Private Issuer Report | Filed: {filing.filedDate}
              {filing.reportDate && ` | Report Date: ${filing.reportDate}`}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <a
              href={secDocumentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-600 hover:underline flex items-center gap-1 text-sm"
            >
              <ExternalLink className="w-4 h-4" />
              View on SEC
            </a>
            <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
              Close
            </button>
          </div>
        </div>
      </div>

      <div className="p-6 space-y-6">
        {/* Filing Info */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <p className="text-gray-500">CIK</p>
            <p className="font-mono">{filing.cik}</p>
          </div>
          <div>
            <p className="text-gray-500">Accession Number</p>
            <p className="font-mono text-xs">{filing.accessionNumber}</p>
          </div>
          <div>
            <p className="text-gray-500">Primary Document</p>
            <p className="font-mono text-xs">{filing.primaryDocument || '-'}</p>
          </div>
          <div>
            <p className="text-gray-500">Form Type</p>
            <p>{filing.formType}</p>
          </div>
        </div>

        {/* Report Text */}
        {filing.reportText && (
          <div>
            <h3 className="font-medium mb-3 flex items-center gap-2">
              <FileText className="w-5 h-5 text-gray-400" />
              Report Content
            </h3>
            <div className="bg-gray-50 rounded-lg p-4 max-h-80 overflow-y-auto">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{filing.reportText}</p>
            </div>
          </div>
        )}

        {/* Exhibits */}
        {filing.exhibits && filing.exhibits.length > 0 && (
          <div>
            <h3 className="font-medium mb-3">Exhibits ({filing.exhibits.length})</h3>
            <div className="bg-gray-50 rounded-lg divide-y divide-gray-200">
              {filing.exhibits.map((exhibit, idx) => (
                <div key={idx} className="p-3 flex items-center justify-between">
                  <div>
                    <span className="font-mono text-sm font-medium">{exhibit.exhibitNumber}</span>
                    {exhibit.description && (
                      <p className="text-sm text-gray-600">{exhibit.description}</p>
                    )}
                  </div>
                  {exhibit.document && (
                    <a
                      href={`https://www.sec.gov/Archives/edgar/data/${filing.cik.replace(/^0+/, '')}/${filing.accessionNumber.replace(/-/g, '')}/${exhibit.document}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:underline text-sm flex items-center gap-1"
                    >
                      <ExternalLink className="w-3 h-3" />
                      View
                    </a>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function Form6KPage() {
  const [searchType, setSearchType] = React.useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(12);
  const [selectedFiling, setSelectedFiling] = React.useState<Form6KType | null>(null);

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm6K(12);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
  } = useForm6KSearch();

  const handleSearch = useCallback(() => {
    setHasSearched(true);
    setCurrentPage(0);

    switch (searchType) {
      case 'symbol':
        if (searchTerm.trim()) {
          searchBySymbol(searchTerm.toUpperCase(), 0, pageSize);
        }
        break;
      case 'cik':
        if (searchTerm.trim()) {
          searchByCik(searchTerm, 0, pageSize);
        }
        break;
      case 'date':
        if (startDate && endDate) {
          searchByDateRange(startDate, endDate, 0, pageSize);
        }
        break;
    }
  }, [searchType, searchTerm, startDate, endDate, pageSize, searchBySymbol, searchByCik, searchByDateRange]);

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);
    switch (searchType) {
      case 'symbol':
        searchBySymbol(searchTerm.toUpperCase(), page, pageSize);
        break;
      case 'cik':
        searchByCik(searchTerm, page, pageSize);
        break;
      case 'date':
        searchByDateRange(startDate, endDate, page, pageSize);
        break;
    }
  }, [searchType, searchTerm, startDate, endDate, pageSize, searchBySymbol, searchByCik, searchByDateRange]);

  const loading = recentLoading || searchLoading;
  const error = recentError || searchError;
  const displayFilings = hasSearched ? searchResults : recentFilings;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Globe className="w-8 h-8 text-cyan-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 6-K - Foreign Private Issuer</h1>
              <p className="text-gray-500 text-sm">Reports of foreign private issuers pursuant to Rule 13a-16 or 15d-16</p>
            </div>
          </div>
          <button
            onClick={refresh}
            className="p-2 hover:bg-gray-100 rounded-full"
            title="Refresh"
          >
            <RefreshCw className="w-5 h-5 text-gray-600" />
          </button>
        </div>

        {/* Search */}
        <div className="flex gap-3 flex-wrap">
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as 'symbol' | 'cik' | 'date')}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-cyan-500"
          >
            <option value="symbol">Ticker Symbol</option>
            <option value="cik">CIK</option>
            <option value="date">Date Range</option>
          </select>

          {searchType === 'date' ? (
            <>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-cyan-500"
              />
              <span className="self-center text-gray-500">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-cyan-500"
              />
            </>
          ) : (
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={searchType === 'symbol' ? 'e.g., TSM, BABA' : 'e.g., 0001378946'}
              className="flex-1 min-w-[200px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-cyan-500"
            />
          )}

          <button
            onClick={handleSearch}
            disabled={loading || (searchType !== 'date' && !searchTerm.trim()) || (searchType === 'date' && (!startDate || !endDate))}
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2 disabled:opacity-50"
          >
            <Search className="w-4 h-4" />
            Search
          </button>
        </div>
      </div>

      {/* Error */}
      {error && <ErrorMessage message={error} />}

      {/* Selected Filing Detail */}
      {selectedFiling && (
        <FilingDetail filing={selectedFiling} onClose={() => setSelectedFiling(null)} />
      )}

      {/* Filings List */}
      {!selectedFiling && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h2 className="font-semibold mb-4">
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent 6-K Filings'}
          </h2>

          {loading && (
            <div className="py-12">
              <LoadingSpinner size="lg" text="Loading filings..." />
            </div>
          )}

          {!loading && displayFilings.length === 0 && (
            <EmptyState
              type="search"
              message={hasSearched ? 'No filings match your search criteria.' : 'No recent filings available.'}
            />
          )}

          {!loading && displayFilings.length > 0 && (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {displayFilings.map((filing) => (
                  <FilingCard
                    key={filing.id}
                    filing={filing}
                    onClick={() => setSelectedFiling(filing)}
                  />
                ))}
              </div>

              {hasSearched && totalPages > 1 && (
                <div className="mt-6">
                  <Pagination
                    page={currentPage}
                    totalPages={totalPages}
                    totalElements={totalElements}
                    size={pageSize}
                    onPageChange={handlePageChange}
                    onPageSizeChange={(size) => {
                      setPageSize(size);
                      setCurrentPage(0);
                    }}
                  />
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
