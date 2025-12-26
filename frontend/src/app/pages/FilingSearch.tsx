import React, { useCallback, useEffect } from 'react';
import { Search, X, Download, ExternalLink, Filter, Info } from 'lucide-react';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useNavigate } from 'react-router-dom';
import { useFilings, useFormTypes } from '../hooks';
import { useExport } from '../hooks/useExport';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { useSearchStore } from '../store';
import { FilingSearchRequest } from '../api';

export function FilingSearch() {
  const navigate = useNavigate();
  const { addSearch } = useSearchStore();

  // Search state
  const [searchTerm, setSearchTerm] = React.useState('');
  const [selectedFormType, setSelectedFormType] = React.useState('');
  const [dateFrom, setDateFrom] = React.useState('');
  const [dateTo, setDateTo] = React.useState('');
  const [keywords, setKeywords] = React.useState<string[]>([]);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(25);
  const [sortField, setSortField] = React.useState<'filingDate' | 'wordHits'>('filingDate');
  const [sortDirection, setSortDirection] = React.useState<'asc' | 'desc'>('desc');
  const [hasSearched, setHasSearched] = React.useState(false);

  // Build search request
  const buildSearchRequest = useCallback((): FilingSearchRequest => ({
    companyName: searchTerm || undefined,
    ticker: searchTerm || undefined,
    cik: searchTerm || undefined,
    formType: selectedFormType || undefined,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    keywords: keywords.length > 0 ? keywords : undefined,
    page: currentPage,
    size: pageSize,
    sortBy: sortField,
    sortDirection: sortDirection,
  }), [searchTerm, selectedFormType, dateFrom, dateTo, keywords, currentPage, pageSize, sortField, sortDirection]);

  // API hooks
  const { filings, loading, error, totalElements, totalPages, search, refresh } = useFilings();
  const { formTypes, loading: formTypesLoading } = useFormTypes();
  const { exportToCsv, exportToJson, loading: exportLoading } = useExport();

  // Trigger search when page or sort changes after initial search
  useEffect(() => {
    if (hasSearched) {
      search(buildSearchRequest());
    }
  }, [currentPage, pageSize, sortField, sortDirection]);

  const handleSearch = () => {
    setCurrentPage(0);
    setHasSearched(true);
    const request = buildSearchRequest();
    search(request);

    // Add to search history
    const searchDesc = searchTerm || selectedFormType || 'All filings';
    addSearch({
      query: searchDesc,
      filters: {
        formType: selectedFormType || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      },
    });
  };

  const addKeyword = () => {
    if (keywordInput && !keywords.includes(keywordInput)) {
      setKeywords([...keywords, keywordInput]);
      setKeywordInput('');
    }
  };

  const removeKeyword = (keyword: string) => {
    setKeywords(keywords.filter(k => k !== keyword));
  };

  const clearFilters = () => {
    setSearchTerm('');
    setSelectedFormType('');
    setDateFrom('');
    setDateTo('');
    setKeywords([]);
    setHasSearched(false);
  };

  const handleSort = (field: 'filingDate' | 'wordHits') => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('desc');
    }
  };

  const handleExportCsv = async () => {
    try {
      await exportToCsv(undefined, buildSearchRequest());
    } catch (err) {
      console.error('Export to CSV failed:', err);
    }
  };

  const handleExportJson = async () => {
    try {
      await exportToJson(undefined, buildSearchRequest());
    } catch (err) {
      console.error('Export to JSON failed:', err);
    }
  };

  const maxWordHits = filings.length > 0
    ? Math.max(...filings.map(f => f.wordHits || 0), 1)
    : 1;

  return (
    <div className="space-y-6">
      {/* Search Form */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-6 flex items-center gap-2">
          <Search className="w-6 h-6" />
          Filing Search
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Company Lookup */}
          <div>
            <label className="block text-sm mb-2">Company / Ticker / CIK</label>
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="e.g., Apple, AAPL, 0000320193"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Form Type */}
          <div>
            <label className="block text-sm mb-2">Form Type</label>
            <select
              value={selectedFormType}
              onChange={(e) => setSelectedFormType(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              disabled={formTypesLoading}
            >
              <option value="">All Form Types</option>
              {formTypes.map(ft => (
                <option key={ft.code} value={ft.code}>{ft.code} - {ft.name}</option>
              ))}
            </select>
          </div>

          {/* Date Range */}
          <div>
            <label className="block text-sm mb-2">From Date</label>
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm mb-2">To Date</label>
            <input
              type="date"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Keywords */}
          <div className="md:col-span-2">
            <div className="flex items-center gap-2 mb-2">
              <label className="block text-sm">Keywords (comma-separated)</label>
              <div className="group relative">
                <Info className="w-4 h-4 text-gray-400 cursor-help" />
                <div className="hidden group-hover:block absolute left-0 top-6 bg-gray-900 text-white text-xs rounded p-2 w-64 z-10">
                  Enter keywords to search within filing content. Separate multiple keywords with commas.
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), addKeyword())}
                placeholder="e.g., revenue, acquisition, merger"
                className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                type="button"
                onClick={addKeyword}
                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300"
              >
                Add
              </button>
            </div>
            {keywords.length > 0 && (
              <div className="flex flex-wrap gap-2 mt-2">
                {keywords.map(keyword => (
                  <span key={keyword} className="inline-flex items-center gap-1 px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm">
                    {keyword}
                    <button onClick={() => removeKeyword(keyword)}>
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="flex gap-3 mt-6">
          <button
            onClick={handleSearch}
            disabled={loading}
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2 disabled:opacity-50"
          >
            {loading ? (
              <LoadingSpinner size="sm" />
            ) : (
              <Search className="w-4 h-4" />
            )}
            Search
          </button>
          <button
            onClick={clearFilters}
            className="px-6 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 transition-colors"
          >
            Clear Filters
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <ErrorMessage
          message={error}
          onRetry={() => search(buildSearchRequest())}
        />
      )}

      {/* Results */}
      {hasSearched && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="flex items-center gap-2">
              <Filter className="w-5 h-5" />
              Search Results ({totalElements.toLocaleString()} filings)
            </h3>
            <div className="flex items-center gap-3">
              <button
                onClick={handleExportCsv}
                disabled={exportLoading || filings.length === 0}
                className="px-3 py-1 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1 text-sm disabled:opacity-50"
              >
                <Download className="w-4 h-4" />
                CSV
              </button>
              <button
                onClick={handleExportJson}
                disabled={exportLoading || filings.length === 0}
                className="px-3 py-1 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1 text-sm disabled:opacity-50"
              >
                <Download className="w-4 h-4" />
                JSON
              </button>
            </div>
          </div>

          {/* Loading State */}
          {loading && (
            <div className="py-12">
              <LoadingSpinner size="lg" text="Searching filings..." />
            </div>
          )}

          {/* Empty State */}
          {!loading && filings.length === 0 && (
            <EmptyState
              type="search"
              message="No filings match your search criteria. Try adjusting your filters."
            />
          )}

          {/* Results Table */}
          {!loading && filings.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-4 py-3 text-left text-sm">Company</th>
                      <th className="px-4 py-3 text-left text-sm">CIK</th>
                      <th className="px-4 py-3 text-left text-sm">Form</th>
                      <th
                        className="px-4 py-3 text-left text-sm cursor-pointer hover:bg-gray-100"
                        onClick={() => handleSort('filingDate')}
                      >
                        <div className="flex items-center gap-1">
                          Filing Date
                          {sortField === 'filingDate' && (
                            <span className="text-xs">{sortDirection === 'asc' ? '↑' : '↓'}</span>
                          )}
                        </div>
                      </th>
                      <th className="px-4 py-3 text-left text-sm">Accession #</th>
                      <th
                        className="px-4 py-3 text-left text-sm cursor-pointer hover:bg-gray-100"
                        onClick={() => handleSort('wordHits')}
                      >
                        <div className="flex items-center gap-1">
                          Word Hits
                          {sortField === 'wordHits' && (
                            <span className="text-xs">{sortDirection === 'asc' ? '↑' : '↓'}</span>
                          )}
                        </div>
                      </th>
                      <th className="px-4 py-3 text-left text-sm">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {filings.map((filing) => (
                      <tr key={filing.id} className="hover:bg-gray-50">
                        <td className="px-4 py-3">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-gray-900">{filing.ticker || '-'}</span>
                            </div>
                            <div className="text-sm text-gray-600">{filing.companyName}</div>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span className="font-mono text-sm text-gray-700">{filing.cik}</span>
                        </td>
                        <td className="px-4 py-3">
                          <FormTypeBadge formType={filing.formType} />
                        </td>
                        <td className="px-4 py-3">
                          <span className="font-mono text-sm">{filing.filingDate}</span>
                        </td>
                        <td className="px-4 py-3">
                          <span className="font-mono text-xs text-gray-600">{filing.accessionNumber}</span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <div className="flex-1 bg-gray-200 rounded-full h-2">
                              <div
                                className="bg-green-500 h-2 rounded-full"
                                style={{ width: `${((filing.wordHits || 0) / maxWordHits) * 100}%` }}
                              />
                            </div>
                            <span className="text-sm w-8 text-right">{filing.wordHits || 0}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => navigate(`/filing/${filing.id}`)}
                              className="text-blue-600 hover:underline text-sm"
                            >
                              View
                            </button>
                            <button className="text-gray-500 hover:text-gray-700">
                              <Download className="w-4 h-4" />
                            </button>
                            {filing.filingUrl && (
                              <a
                                href={filing.filingUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-gray-500 hover:text-gray-700"
                              >
                                <ExternalLink className="w-4 h-4" />
                              </a>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <Pagination
                page={currentPage}
                totalPages={totalPages}
                totalElements={totalElements}
                size={pageSize}
                onPageChange={setCurrentPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setCurrentPage(0);
                }}
              />
            </>
          )}
        </div>
      )}
    </div>
  );
}
