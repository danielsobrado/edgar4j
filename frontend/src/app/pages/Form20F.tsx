import React, { useCallback } from 'react';
import { Search, Globe, Calendar, Building2, ExternalLink, RefreshCw, FileText, DollarSign, TrendingUp } from 'lucide-react';
import { useRecentForm20F, useForm20FSearch } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form20F as Form20FType } from '../api/types';

function FilingCard({ filing, onClick }: { filing: Form20FType; onClick: () => void }) {
  const hasFinancials = filing.keyFinancials && Object.keys(filing.keyFinancials).length > 0;

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
            <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded text-xs">{filing.formType}</span>
            {filing.isAmendment && (
              <span className="px-2 py-0.5 bg-yellow-100 text-yellow-700 rounded text-xs">Amended</span>
            )}
          </div>
          <h3 className="font-medium text-gray-900 line-clamp-1">{filing.companyName || `CIK: ${filing.cik}`}</h3>
        </div>
        <Globe className="w-5 h-5 text-indigo-500" />
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm mb-3">
        <div>
          <p className="text-gray-500">Filed</p>
          <p className="font-medium">{filing.filedDate}</p>
        </div>
        {filing.fiscalYear && (
          <div>
            <p className="text-gray-500">Fiscal Year</p>
            <p className="font-medium">{filing.fiscalYear}</p>
          </div>
        )}
      </div>

      <div className="flex items-center justify-between text-sm">
        <div className="flex items-center gap-3">
          {filing.securityExchange && (
            <span className="text-gray-600">{filing.securityExchange}</span>
          )}
          {hasFinancials && (
            <span className="text-green-600 flex items-center gap-1">
              <DollarSign className="w-3 h-3" />
              Financials
            </span>
          )}
        </div>
        {filing.sharesOutstanding && (
          <span className="text-gray-500 text-xs">
            {(filing.sharesOutstanding / 1_000_000).toFixed(1)}M shares
          </span>
        )}
      </div>
    </div>
  );
}

function formatFinancialValue(value: number): string {
  if (Math.abs(value) >= 1_000_000_000) {
    return `$${(value / 1_000_000_000).toFixed(2)}B`;
  } else if (Math.abs(value) >= 1_000_000) {
    return `$${(value / 1_000_000).toFixed(2)}M`;
  } else if (Math.abs(value) >= 1_000) {
    return `$${(value / 1_000).toFixed(2)}K`;
  }
  return `$${value.toFixed(2)}`;
}

function FilingDetail({ filing, onClose }: { filing: Form20FType; onClose: () => void }) {
  const secDocumentUrl = `https://www.sec.gov/Archives/edgar/data/${filing.cik.replace(/^0+/, '')}/${filing.accessionNumber.replace(/-/g, '')}/${filing.primaryDocument || 'index.html'}`;
  const hasFinancials = filing.keyFinancials && Object.keys(filing.keyFinancials).length > 0;
  const hasDeiData = filing.deiData && Object.keys(filing.deiData).length > 0;

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
              <span className="px-2 py-1 bg-indigo-100 text-indigo-700 rounded text-sm">{filing.formType}</span>
              {filing.isAmendment && (
                <span className="px-2 py-1 bg-yellow-100 text-yellow-700 rounded text-sm">Amended</span>
              )}
            </div>
            <p className="text-gray-500 text-sm">
              Annual Report (Foreign Private Issuer) | Filed: {filing.filedDate}
              {filing.fiscalYear && ` | FY ${filing.fiscalYear}`}
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
          {filing.securityExchange && (
            <div>
              <p className="text-gray-500">Exchange</p>
              <p>{filing.securityExchange}</p>
            </div>
          )}
          {filing.documentPeriodEndDate && (
            <div>
              <p className="text-gray-500">Period End Date</p>
              <p>{filing.documentPeriodEndDate}</p>
            </div>
          )}
        </div>

        {/* Fiscal Info */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          {filing.fiscalYear && (
            <div>
              <p className="text-gray-500">Fiscal Year</p>
              <p className="font-medium">{filing.fiscalYear}</p>
            </div>
          )}
          {filing.fiscalPeriod && (
            <div>
              <p className="text-gray-500">Fiscal Period</p>
              <p>{filing.fiscalPeriod}</p>
            </div>
          )}
          {filing.fiscalYearEndDate && (
            <div>
              <p className="text-gray-500">FY End</p>
              <p>{filing.fiscalYearEndDate}</p>
            </div>
          )}
          {filing.sharesOutstanding && (
            <div>
              <p className="text-gray-500">Shares Outstanding</p>
              <p>{filing.sharesOutstanding.toLocaleString()}</p>
            </div>
          )}
        </div>

        {/* Key Financials */}
        {hasFinancials && (
          <div>
            <h3 className="font-medium mb-3 flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-gray-400" />
              Key Financials
            </h3>
            <div className="bg-gray-50 rounded-lg p-4 grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {Object.entries(filing.keyFinancials!).map(([key, value]) => (
                <div key={key}>
                  <p className="text-gray-500 text-xs truncate" title={key}>{key}</p>
                  <p className="font-medium">{formatFinancialValue(value)}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* DEI Data */}
        {hasDeiData && (
          <div>
            <h3 className="font-medium mb-3 flex items-center gap-2">
              <FileText className="w-5 h-5 text-gray-400" />
              Document and Entity Information
            </h3>
            <div className="bg-gray-50 rounded-lg p-4 max-h-80 overflow-y-auto">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                {Object.entries(filing.deiData!).map(([key, value]) => (
                  <div key={key} className="flex flex-col">
                    <span className="text-gray-500 text-xs truncate" title={key}>{key}</span>
                    <span className="font-medium truncate" title={value}>{value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function Form20FPage() {
  const [searchType, setSearchType] = React.useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(12);
  const [selectedFiling, setSelectedFiling] = React.useState<Form20FType | null>(null);

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm20F(12);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
  } = useForm20FSearch();

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
            <Globe className="w-8 h-8 text-indigo-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 20-F - Annual Report</h1>
              <p className="text-gray-500 text-sm">Annual and transition reports of foreign private issuers</p>
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
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
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
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <span className="self-center text-gray-500">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </>
          ) : (
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={searchType === 'symbol' ? 'e.g., TSM, NIO, BABA' : 'e.g., 0001378946'}
              className="flex-1 min-w-[200px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
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
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent 20-F Filings'}
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
