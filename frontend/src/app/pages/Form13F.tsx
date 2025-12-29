import React, { useCallback } from 'react';
import { Search, TrendingUp, Building2, Briefcase, ExternalLink, RefreshCw, ChevronDown, ChevronUp } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useRecentForm13F, useForm13FSearch } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form13F as Form13FType, Form13FHolding } from '../api/types';

function formatCurrency(value: number): string {
  if (value >= 1_000_000_000) {
    return `$${(value / 1_000_000_000).toFixed(2)}B`;
  }
  if (value >= 1_000_000) {
    return `$${(value / 1_000_000).toFixed(2)}M`;
  }
  if (value >= 1_000) {
    return `$${(value / 1_000).toFixed(2)}K`;
  }
  return `$${value.toLocaleString()}`;
}

function formatNumber(value: number): string {
  return value.toLocaleString();
}

function HoldingsTable({ holdings }: { holdings: Form13FHolding[] }) {
  const [sortField, setSortField] = React.useState<'value' | 'shares' | 'issuer'>('value');
  const [sortDir, setSortDir] = React.useState<'asc' | 'desc'>('desc');

  const sortedHoldings = React.useMemo(() => {
    return [...holdings].sort((a, b) => {
      let cmp = 0;
      if (sortField === 'value') {
        cmp = a.value - b.value;
      } else if (sortField === 'shares') {
        cmp = a.sharesOrPrincipalAmount - b.sharesOrPrincipalAmount;
      } else {
        cmp = a.nameOfIssuer.localeCompare(b.nameOfIssuer);
      }
      return sortDir === 'desc' ? -cmp : cmp;
    });
  }, [holdings, sortField, sortDir]);

  const handleSort = (field: 'value' | 'shares' | 'issuer') => {
    if (sortField === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  };

  const SortIcon = ({ field }: { field: string }) => {
    if (sortField !== field) return null;
    return sortDir === 'desc' ? <ChevronDown className="w-4 h-4" /> : <ChevronUp className="w-4 h-4" />;
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            <th
              className="px-4 py-3 text-left text-sm cursor-pointer hover:bg-gray-100"
              onClick={() => handleSort('issuer')}
            >
              <div className="flex items-center gap-1">
                Issuer <SortIcon field="issuer" />
              </div>
            </th>
            <th className="px-4 py-3 text-left text-sm">CUSIP</th>
            <th className="px-4 py-3 text-left text-sm">Class</th>
            <th
              className="px-4 py-3 text-right text-sm cursor-pointer hover:bg-gray-100"
              onClick={() => handleSort('value')}
            >
              <div className="flex items-center justify-end gap-1">
                Value (000s) <SortIcon field="value" />
              </div>
            </th>
            <th
              className="px-4 py-3 text-right text-sm cursor-pointer hover:bg-gray-100"
              onClick={() => handleSort('shares')}
            >
              <div className="flex items-center justify-end gap-1">
                Shares <SortIcon field="shares" />
              </div>
            </th>
            <th className="px-4 py-3 text-center text-sm">Put/Call</th>
            <th className="px-4 py-3 text-center text-sm">Discretion</th>
            <th className="px-4 py-3 text-right text-sm">Voting (S/Sh/N)</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {sortedHoldings.map((holding, idx) => (
            <tr key={`${holding.cusip}-${idx}`} className="hover:bg-gray-50">
              <td className="px-4 py-3">
                <span className="font-medium">{holding.nameOfIssuer}</span>
              </td>
              <td className="px-4 py-3">
                <span className="font-mono text-sm">{holding.cusip}</span>
              </td>
              <td className="px-4 py-3 text-sm">{holding.titleOfClass}</td>
              <td className="px-4 py-3 text-right font-mono">
                {formatCurrency(holding.value * 1000)}
              </td>
              <td className="px-4 py-3 text-right font-mono">
                {formatNumber(holding.sharesOrPrincipalAmount)}
                <span className="text-gray-500 text-xs ml-1">{holding.sharesOrPrincipalAmountType}</span>
              </td>
              <td className="px-4 py-3 text-center">
                {holding.putCall && (
                  <span className={`px-2 py-1 rounded text-xs ${
                    holding.putCall === 'PUT' ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'
                  }`}>
                    {holding.putCall}
                  </span>
                )}
              </td>
              <td className="px-4 py-3 text-center">
                <span className={`px-2 py-1 rounded text-xs ${
                  holding.investmentDiscretion === 'SOLE' ? 'bg-blue-100 text-blue-800' :
                  holding.investmentDiscretion === 'SHARED' ? 'bg-purple-100 text-purple-800' :
                  'bg-gray-100 text-gray-800'
                }`}>
                  {holding.investmentDiscretion}
                </span>
              </td>
              <td className="px-4 py-3 text-right font-mono text-sm">
                {formatNumber(holding.votingAuthoritySole)}/
                {formatNumber(holding.votingAuthorityShared)}/
                {formatNumber(holding.votingAuthorityNone)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FilingCard({ filing, onClick }: { filing: Form13FType; onClick: () => void }) {
  return (
    <div
      className="bg-white rounded-lg shadow-sm p-4 hover:shadow-md transition-shadow cursor-pointer border border-gray-100"
      onClick={onClick}
    >
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="font-semibold text-gray-900">{filing.filerName}</h3>
          <p className="text-sm text-gray-500 font-mono">CIK: {filing.cik}</p>
        </div>
        <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs font-medium">
          {filing.formType}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <p className="text-gray-500">Report Period</p>
          <p className="font-medium">{filing.reportPeriod}</p>
        </div>
        <div>
          <p className="text-gray-500">Filed</p>
          <p className="font-medium">{filing.filedDate}</p>
        </div>
        <div>
          <p className="text-gray-500">Holdings</p>
          <p className="font-medium">{filing.holdingsCount.toLocaleString()}</p>
        </div>
        <div>
          <p className="text-gray-500">Total Value</p>
          <p className="font-medium">{formatCurrency(filing.totalValue * 1000)}</p>
        </div>
      </div>
    </div>
  );
}

export function Form13FPage() {
  const navigate = useNavigate();
  const [searchType, setSearchType] = React.useState<'filer' | 'issuer' | 'cusip' | 'quarter'>('filer');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(10);
  const [selectedFiling, setSelectedFiling] = React.useState<Form13FType | null>(null);

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm13F(10);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByFilerName,
    searchByIssuerName,
    searchByCusip,
    searchByQuarter,
  } = useForm13FSearch();

  const handleSearch = useCallback(() => {
    if (!searchTerm.trim()) return;

    setHasSearched(true);
    setCurrentPage(0);

    switch (searchType) {
      case 'filer':
        searchByFilerName(searchTerm, 0, pageSize);
        break;
      case 'issuer':
        searchByIssuerName(searchTerm, 0, pageSize);
        break;
      case 'cusip':
        searchByCusip(searchTerm, 0, pageSize);
        break;
      case 'quarter':
        searchByQuarter(searchTerm, 0, pageSize);
        break;
    }
  }, [searchType, searchTerm, pageSize, searchByFilerName, searchByIssuerName, searchByCusip, searchByQuarter]);

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);
    switch (searchType) {
      case 'filer':
        searchByFilerName(searchTerm, page, pageSize);
        break;
      case 'issuer':
        searchByIssuerName(searchTerm, page, pageSize);
        break;
      case 'cusip':
        searchByCusip(searchTerm, page, pageSize);
        break;
      case 'quarter':
        searchByQuarter(searchTerm, page, pageSize);
        break;
    }
  }, [searchType, searchTerm, pageSize, searchByFilerName, searchByIssuerName, searchByCusip, searchByQuarter]);

  const loading = recentLoading || searchLoading;
  const error = recentError || searchError;
  const displayFilings = hasSearched ? searchResults : recentFilings;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Briefcase className="w-8 h-8 text-blue-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 13F Holdings</h1>
              <p className="text-gray-500 text-sm">Institutional investment manager holdings reports</p>
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
        <div className="flex gap-3">
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as 'filer' | 'issuer' | 'cusip' | 'quarter')}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="filer">Filer Name</option>
            <option value="issuer">Issuer Name</option>
            <option value="cusip">CUSIP</option>
            <option value="quarter">Quarter (e.g., 2024-Q4)</option>
          </select>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            placeholder={
              searchType === 'filer' ? 'e.g., Berkshire, Vanguard' :
              searchType === 'issuer' ? 'e.g., Apple, Microsoft' :
              searchType === 'cusip' ? 'e.g., 037833100' :
              'e.g., 2024-Q4'
            }
            className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            onClick={handleSearch}
            disabled={loading || !searchTerm.trim()}
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
        <div className="bg-white rounded-lg shadow-sm">
          <div className="border-b border-gray-200 p-6">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-semibold">{selectedFiling.filerName}</h2>
                <p className="text-gray-500 text-sm">
                  Report Period: {selectedFiling.reportPeriod} | Filed: {selectedFiling.filedDate}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <a
                  href={`https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${selectedFiling.cik}&type=13F&dateb=&owner=include&count=40`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-600 hover:underline flex items-center gap-1 text-sm"
                >
                  <ExternalLink className="w-4 h-4" />
                  View on SEC
                </a>
                <button
                  onClick={() => setSelectedFiling(null)}
                  className="text-gray-500 hover:text-gray-700"
                >
                  Close
                </button>
              </div>
            </div>
            <div className="flex gap-6 mt-4">
              <div className="flex items-center gap-2">
                <Building2 className="w-5 h-5 text-gray-400" />
                <span className="text-sm">
                  <span className="text-gray-500">Holdings:</span>{' '}
                  <span className="font-medium">{selectedFiling.holdingsCount.toLocaleString()}</span>
                </span>
              </div>
              <div className="flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-gray-400" />
                <span className="text-sm">
                  <span className="text-gray-500">Total Value:</span>{' '}
                  <span className="font-medium">{formatCurrency(selectedFiling.totalValue * 1000)}</span>
                </span>
              </div>
            </div>
          </div>
          <div className="p-6">
            <h3 className="font-medium mb-4">Holdings ({selectedFiling.holdings?.length || 0})</h3>
            {selectedFiling.holdings && selectedFiling.holdings.length > 0 ? (
              <HoldingsTable holdings={selectedFiling.holdings} />
            ) : (
              <EmptyState type="data" message="No holdings data available" />
            )}
          </div>
        </div>
      )}

      {/* Filings List */}
      {!selectedFiling && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h2 className="font-semibold mb-4">
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent 13F Filings'}
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
