import React, { useCallback } from 'react';
import { Search, UserPlus, Calendar, Building2, ExternalLink, RefreshCw, User, Briefcase } from 'lucide-react';
import { useRecentForm3, useForm3Search } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form3 as Form3Type, Form4Transaction } from '../api/types';

function getOwnerTypeBadge(form: Form3Type) {
  if (form.ownerType) return form.ownerType;
  if (form.isDirector) return 'Director';
  if (form.isOfficer) return 'Officer';
  if (form.isTenPercentOwner) return '10% Owner';
  if (form.isOther) return 'Other';
  return 'Unknown';
}

function OwnerTypeBadge({ form }: { form: Form3Type }) {
  const ownerType = getOwnerTypeBadge(form);

  const colorMap: Record<string, string> = {
    'Director': 'bg-blue-100 text-blue-800',
    'Officer': 'bg-green-100 text-green-800',
    '10% Owner': 'bg-purple-100 text-purple-800',
    'Other': 'bg-gray-100 text-gray-800',
    'Unknown': 'bg-gray-100 text-gray-800',
  };

  return (
    <span className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium ${colorMap[ownerType] || 'bg-gray-100 text-gray-800'}`}>
      {ownerType}
    </span>
  );
}

function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null) return '-';
  return value.toLocaleString();
}

function formatCurrency(value: number | undefined): string {
  if (value === undefined || value === null) return '-';
  return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function FilingCard({ filing, onClick }: { filing: Form3Type; onClick: () => void }) {
  const holdingsCount = filing.holdings?.length || 0;

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
            <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs">Form 3</span>
          </div>
          <h3 className="font-medium text-gray-900 line-clamp-1">{filing.issuerName || `CIK: ${filing.cik}`}</h3>
        </div>
      </div>

      <div className="mb-3">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <User className="w-4 h-4" />
          <span className="line-clamp-1">{filing.rptOwnerName || 'Unknown Owner'}</span>
        </div>
        {filing.officerTitle && (
          <div className="flex items-center gap-2 text-sm text-gray-500 mt-1">
            <Briefcase className="w-4 h-4" />
            <span className="line-clamp-1">{filing.officerTitle}</span>
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm mb-3">
        <div>
          <p className="text-gray-500">Filed</p>
          <p className="font-medium">{filing.filedDate}</p>
        </div>
        <div>
          <p className="text-gray-500">Holdings</p>
          <p className="font-medium">{holdingsCount}</p>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <OwnerTypeBadge form={filing} />
      </div>
    </div>
  );
}

function HoldingsTable({ holdings }: { holdings: Form4Transaction[] }) {
  if (!holdings || holdings.length === 0) {
    return <p className="text-gray-500 text-sm">No holdings reported.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Security</th>
            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Shares</th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ownership</th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {holdings.map((holding, idx) => (
            <tr key={idx} className="hover:bg-gray-50">
              <td className="px-4 py-3 text-sm font-medium text-gray-900">{holding.securityTitle}</td>
              <td className="px-4 py-3 text-sm text-right">{formatNumber(holding.sharesOwnedFollowingTransaction)}</td>
              <td className="px-4 py-3 text-sm">
                <span className={`px-2 py-1 rounded text-xs ${holding.directOrIndirectOwnership === 'D' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                  {holding.directOrIndirectOwnership === 'D' ? 'Direct' : 'Indirect'}
                </span>
              </td>
              <td className="px-4 py-3 text-sm text-gray-600">
                {holding.transactionType === 'DERIVATIVE' ? 'Derivative' : 'Non-Derivative'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FilingDetail({ filing, onClose }: { filing: Form3Type; onClose: () => void }) {
  const secDocumentUrl = `https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${filing.cik}&type=3&dateb=&owner=include&count=40`;

  return (
    <div className="bg-white rounded-lg shadow-sm">
      <div className="border-b border-gray-200 p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 className="text-lg font-semibold">{filing.issuerName || `CIK: ${filing.cik}`}</h2>
              {filing.tradingSymbol && (
                <span className="text-blue-600 font-bold">{filing.tradingSymbol}</span>
              )}
              <span className="px-2 py-1 bg-green-100 text-green-700 rounded text-sm">Form 3</span>
            </div>
            <p className="text-gray-500 text-sm">
              Initial Statement of Beneficial Ownership | Filed: {filing.filedDate}
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
            <p className="text-gray-500">Issuer CIK</p>
            <p className="font-mono">{filing.cik}</p>
          </div>
          <div>
            <p className="text-gray-500">Accession Number</p>
            <p className="font-mono text-xs">{filing.accessionNumber}</p>
          </div>
          <div>
            <p className="text-gray-500">Period of Report</p>
            <p>{filing.periodOfReport || '-'}</p>
          </div>
          <div>
            <p className="text-gray-500">Document Type</p>
            <p>{filing.documentType || 'Form 3'}</p>
          </div>
        </div>

        {/* Reporting Owner */}
        <div>
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <User className="w-5 h-5 text-gray-400" />
            Reporting Owner
          </h3>
          <div className="bg-gray-50 rounded-lg p-4 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-medium">{filing.rptOwnerName || 'Unknown'}</span>
              <OwnerTypeBadge form={filing} />
            </div>
            {filing.rptOwnerCik && (
              <p className="text-sm text-gray-600">CIK: <span className="font-mono">{filing.rptOwnerCik}</span></p>
            )}
            {filing.officerTitle && (
              <p className="text-sm text-gray-600">Title: {filing.officerTitle}</p>
            )}
            <div className="flex gap-4 text-sm text-gray-600">
              {filing.isDirector && <span>Director</span>}
              {filing.isOfficer && <span>Officer</span>}
              {filing.isTenPercentOwner && <span>10% Owner</span>}
              {filing.isOther && <span>Other</span>}
            </div>
          </div>
        </div>

        {/* Holdings */}
        <div>
          <h3 className="font-medium mb-3">Initial Holdings ({filing.holdings?.length || 0})</h3>
          <HoldingsTable holdings={filing.holdings || []} />
        </div>
      </div>
    </div>
  );
}

export function Form3Page() {
  const [searchType, setSearchType] = React.useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(12);
  const [selectedFiling, setSelectedFiling] = React.useState<Form3Type | null>(null);

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm3(12);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
  } = useForm3Search();

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
            <UserPlus className="w-8 h-8 text-green-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 3 - Initial Ownership</h1>
              <p className="text-gray-500 text-sm">Initial statement of beneficial ownership of securities</p>
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
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
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
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
              />
              <span className="self-center text-gray-500">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
              />
            </>
          ) : (
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={searchType === 'symbol' ? 'e.g., AAPL, TSLA' : 'e.g., 0000320193'}
              className="flex-1 min-w-[200px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
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
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent Form 3 Filings'}
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
