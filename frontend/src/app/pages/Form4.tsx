import React, { useCallback, useState } from 'react';
import { Search, RefreshCw, User, Building2, ExternalLink, TrendingUp, TrendingDown, DollarSign } from 'lucide-react';
import { useRecentForm4, useForm4Search } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form4, Form4Transaction } from '../api/types';

function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null) return '-';
  return value.toLocaleString();
}

function formatCurrency(value: number | undefined): string {
  if (value === undefined || value === null) return '-';
  return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch {
    return dateStr;
  }
}

function formatDateTime(dateStr: string | undefined): string {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    return date.toLocaleString('en-US', {
      month: '2-digit',
      day: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    });
  } catch {
    return dateStr;
  }
}

function getOwnerType(filing: Form4): string {
  if (filing.isDirector) return 'Director';
  if (filing.isOfficer) return 'Officer';
  if (filing.isTenPercentOwner) return '10% Owner';
  if (filing.isOther) return 'Other';
  return filing.ownerType || 'Unknown';
}

function getTransactionType(acquiredDisposedCode: string | undefined): string {
  if (acquiredDisposedCode === 'A') return 'Purchase';
  if (acquiredDisposedCode === 'D') return 'Sale';
  return 'Other';
}

function getTransactionCodeDescription(code: string | undefined): string {
  if (!code) return '-';
  const codeMap: Record<string, string> = {
    'P': 'Open Market Purchase',
    'S': 'Open Market Sale',
    'A': 'Grant/Award',
    'M': 'Exercise of Derivative',
    'F': 'Payment via Withholding',
    'G': 'Gift',
    'J': 'Other',
    'L': 'Small Acquisition',
    'W': 'Will/Laws of Descent',
    'Z': 'Voting Trust',
    'K': 'Equity Swap',
    'U': 'Tender of Shares',
  };
  return codeMap[code] || code;
}

function OwnershipBadge({ filing }: { filing: Form4 }) {
  const directOrIndirect = filing.transactions?.[0]?.directOrIndirectOwnership;
  const nature = filing.transactions?.[0]?.natureOfOwnership;
  
  const parts: string[] = [];
  if (directOrIndirect === 'D') parts.push('Direct');
  if (directOrIndirect === 'I') parts.push('Indirect');
  if (nature) parts.push(nature);
  
  return (
    <span className="text-xs text-gray-500">
      {parts.length > 0 ? `(${parts.join(', ')})` : '-'}
    </span>
  );
}

function TransactionRow({ filing }: { filing: Form4 }) {
  const transaction = filing.transactions?.[0] || {
    transactionDate: filing.transactionDate,
    transactionShares: filing.transactionShares,
    transactionPricePerShare: filing.transactionPricePerShare,
    transactionValue: filing.transactionValue,
    acquiredDisposedCode: filing.acquiredDisposedCode,
    sharesOwnedFollowingTransaction: undefined,
    directOrIndirectOwnership: undefined,
    natureOfOwnership: undefined,
    securityTitle: filing.securityTitle,
  };

  const transactionType = getTransactionType(transaction.acquiredDisposedCode);
  const isSale = transactionType === 'Sale';

  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <td className="px-4 py-3 text-sm text-gray-900 whitespace-nowrap">
        {formatDate(transaction.transactionDate)}
      </td>
      <td className="px-4 py-3 text-sm text-gray-500 whitespace-nowrap">
        {formatDateTime(transaction.transactionDate)}
      </td>
      <td className="px-4 py-3 text-sm font-medium text-gray-900">
        {filing.issuerName || '-'}
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">
        {filing.tradingSymbol || '-'}
      </td>
      <td className="px-4 py-3 text-sm text-gray-900 max-w-[200px] truncate">
        {filing.rptOwnerName || '-'}
        <div className="text-xs text-gray-500 font-normal">
          {getOwnerType(filing)}
        </div>
      </td>
      <td className="px-4 py-3 text-sm text-right text-gray-900">
        {formatNumber(transaction.transactionShares)}
      </td>
      <td className="px-4 py-3 text-sm text-right text-gray-900">
        {transaction.transactionPricePerShare ? formatCurrency(transaction.transactionPricePerShare) : '$0'}
      </td>
      <td className="px-4 py-3 text-sm text-right text-gray-900">
        {transaction.transactionValue ? formatCurrency(transaction.transactionValue) : '$0'}
      </td>
      <td className="px-4 py-3 text-sm text-right text-gray-900">
        {formatNumber(transaction.sharesOwnedFollowingTransaction)}
        <OwnershipBadge filing={filing} />
      </td>
      <td className="px-4 py-3 text-center">
        <a
          href={`https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${filing.cik}&type=4&dateb=&owner=include&count=40`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 hover:text-blue-800 text-sm font-medium"
        >
          View
        </a>
      </td>
    </tr>
  );
}

function FilingsTable({ filings }: { filings: Form4[] }) {
  if (!filings || filings.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        No transactions found
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Transaction<br/>Date
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Reported<br/>DateTime
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Company
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Symbol
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Insider<br/>Relationship
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              Shares<br/>Traded
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              Average<br/>Price
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              Total<br/>Amount
            </th>
            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              Shares<br/>Owned
            </th>
            <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
              Filing
            </th>
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {filings.map((filing) => (
            <TransactionRow key={filing.id || filing.accessionNumber} filing={filing} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Form4Page() {
  const [searchType, setSearchType] = useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [showDateRange, setShowDateRange] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
    searchBySymbolAndDateRange,
  } = useForm4Search();

  // Auto-search on mount with default symbol or empty
  React.useEffect(() => {
    // Load recent transactions by searching with no symbol but default params
    searchBySymbol('', 0, pageSize);
  }, []);

  const handleSearch = useCallback(() => {
    setCurrentPage(0);

    switch (searchType) {
      case 'symbol':
        if (searchTerm.trim()) {
          if (showDateRange && startDate && endDate) {
            searchBySymbolAndDateRange(searchTerm.toUpperCase(), startDate, endDate, 0, pageSize);
          } else {
            searchBySymbol(searchTerm.toUpperCase(), 0, pageSize);
          }
        } else {
          // Empty search = recent filings
          searchBySymbol('', 0, pageSize);
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
  }, [searchType, searchTerm, startDate, endDate, showDateRange, pageSize, searchBySymbol, searchByCik, searchByDateRange, searchBySymbolAndDateRange]);

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);

    switch (searchType) {
      case 'symbol':
        if (searchTerm.trim()) {
          if (showDateRange && startDate && endDate) {
            searchBySymbolAndDateRange(searchTerm.toUpperCase(), startDate, endDate, page, pageSize);
          } else {
            searchBySymbol(searchTerm.toUpperCase(), page, pageSize);
          }
        } else {
          searchBySymbol('', page, pageSize);
        }
        break;
      case 'cik':
        searchByCik(searchTerm, page, pageSize);
        break;
      case 'date':
        searchByDateRange(startDate, endDate, page, pageSize);
        break;
    }
  }, [searchType, searchTerm, startDate, endDate, showDateRange, pageSize, searchBySymbol, searchByCik, searchByDateRange, searchBySymbolAndDateRange]);

  const loading = searchLoading;
  const error = searchError;
  const displayFilings = searchResults;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <TrendingUp className="w-8 h-8 text-red-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 4 - Insider Transactions</h1>
              <p className="text-gray-500 text-sm">Insider trading transactions and holdings</p>
            </div>
          </div>
          <button
            onClick={() => handleSearch()}
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
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <span className="self-center text-gray-500">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </>
          ) : (
            <>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                placeholder={searchType === 'symbol' ? 'e.g., X, AAPL, TSLA' : 'e.g., 0000320193'}
                className="flex-1 min-w-[200px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              
              {searchType === 'symbol' && (
                <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={showDateRange}
                    onChange={(e) => setShowDateRange(e.target.checked)}
                    className="rounded border-gray-300"
                  />
                  Date Range
                </label>
              )}
              
              {showDateRange && searchType === 'symbol' && (
                <>
                  <input
                    type="date"
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    placeholder="Start"
                    className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <span className="self-center text-gray-500">to</span>
                  <input
                    type="date"
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    placeholder="End"
                    className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </>
              )}
            </>
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

      {/* Filings Table */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-lg">
            {searchTerm || searchType === 'date'
              ? `Search Results (${totalElements.toLocaleString()} transactions)` 
              : 'Recent Insider Transactions'}
          </h2>
          <span className="text-sm text-gray-500">
            {displayFilings.length} transactions shown
          </span>
        </div>

        {loading && (
          <div className="py-12">
            <LoadingSpinner size="lg" text="Loading transactions..." />
          </div>
        )}

        {!loading && displayFilings.length === 0 && (
          <EmptyState
            type="search"
            message={searchTerm || searchType === 'date' ? 'No transactions match your search criteria.' : 'No recent transactions available.'}
          />
        )}

        {!loading && displayFilings.length > 0 && (
          <>
            <FilingsTable filings={displayFilings} />

            {totalPages > 1 && (
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
    </div>
  );
}
