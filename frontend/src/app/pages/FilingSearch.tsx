import React, { useCallback, useEffect } from 'react';
import { Search, X, Download, ExternalLink, Filter, Info } from 'lucide-react';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useFilings, useFormTypes } from '../hooks';
import { useExport } from '../hooks/useExport';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { useSearchStore } from '../store';
import { FilingSearchRequest, remoteEdgarApi, RemoteFilingSearchResult } from '../api';

export function FilingSearch() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
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
  const [searchMode, setSearchMode] = React.useState<'company' | 'ticker' | 'cik'>('company');
  const [includeRemote, setIncludeRemote] = React.useState(false);
  const [remoteMaxForms, setRemoteMaxForms] = React.useState(25);
  const [remoteLoading, setRemoteLoading] = React.useState(false);
  const [remoteError, setRemoteError] = React.useState<string | null>(null);
  const [remoteSearchResult, setRemoteSearchResult] = React.useState<RemoteFilingSearchResult | null>(null);

  const resolveSearchInput = useCallback((value: string) => {
    const trimmedValue = value.trim();

    if (!trimmedValue) {
      return { value: '', mode: 'company' as const };
    }

    if (/^\d+$/.test(trimmedValue)) {
      return { value: trimmedValue, mode: 'cik' as const };
    }

    if (/^[A-Za-z][A-Za-z0-9.-]{0,9}$/.test(trimmedValue)) {
      return { value: trimmedValue.toUpperCase(), mode: 'ticker' as const };
    }

    return { value: trimmedValue, mode: 'company' as const };
  }, []);

  // Build search request
  const buildSearchRequest = useCallback((overrides: Partial<FilingSearchRequest> = {}): FilingSearchRequest => {
    const trimmedSearchTerm = searchTerm.trim();

    return {
      companyName: trimmedSearchTerm && searchMode === 'company' ? trimmedSearchTerm : undefined,
      ticker: trimmedSearchTerm && searchMode === 'ticker' ? trimmedSearchTerm : undefined,
      cik: trimmedSearchTerm && searchMode === 'cik' ? trimmedSearchTerm : undefined,
      formTypes: selectedFormType ? [selectedFormType] : undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      keywords: keywords.length > 0 ? keywords : undefined,
      page: currentPage,
      size: pageSize,
      sortBy: sortField,
      sortDir: sortDirection,
      ...overrides,
    };
  }, [searchTerm, searchMode, selectedFormType, dateFrom, dateTo, keywords, currentPage, pageSize, sortField, sortDirection]);

  const buildRemoteSearchRequest = useCallback(() => {
    const trimmedSearchTerm = searchTerm.trim();
    const hasDateRange = Boolean(dateFrom && dateTo);

    return {
      companyName: trimmedSearchTerm && searchMode === 'company' ? trimmedSearchTerm : undefined,
      ticker: trimmedSearchTerm && searchMode === 'ticker' ? trimmedSearchTerm : undefined,
      cik: trimmedSearchTerm && searchMode === 'cik' ? trimmedSearchTerm : undefined,
      formType: selectedFormType || undefined,
      dateFrom: hasDateRange ? dateFrom : undefined,
      dateTo: hasDateRange ? dateTo : undefined,
      limit: hasDateRange ? 100 : remoteMaxForms,
    };
  }, [searchTerm, searchMode, selectedFormType, dateFrom, dateTo, remoteMaxForms]);

  const normalizeRemoteSearchError = useCallback((error: unknown): string => {
    const message = error instanceof Error ? error.message : 'Failed to search remote SEC filings';
    if (message.includes('404') && message.includes('/api/remote-edgar/')) {
      return 'Remote EDGAR backend endpoints are not available. Restart the backend so remote search endpoints are loaded.';
    }
    return message;
  }, []);

  const searchRemote = useCallback(async () => {
    if (!includeRemote) {
      setRemoteLoading(false);
      setRemoteError(null);
      setRemoteSearchResult(null);
      return;
    }

    const request = buildRemoteSearchRequest();
    if (!request.formType) {
      setRemoteError('Form Type is required for remote SEC search.');
      setRemoteSearchResult(null);
      return;
    }

    setRemoteLoading(true);
    setRemoteError(null);
    setRemoteSearchResult(null);
    try {
      const data = await remoteEdgarApi.searchFilings(request);
      setRemoteSearchResult(data);
    } catch (err) {
      setRemoteError(normalizeRemoteSearchError(err));
    } finally {
      setRemoteLoading(false);
    }
  }, [includeRemote, buildRemoteSearchRequest, normalizeRemoteSearchError]);

  // API hooks
  const { filings, loading, error, totalElements, totalPages, search, refresh } = useFilings();
  const { formTypes, loading: formTypesLoading } = useFormTypes();
  const { exportToCsv, exportToJson, loading: exportLoading } = useExport();

  useEffect(() => {
    const tickerParam = searchParams.get('ticker');
    const cikParam = searchParams.get('cik');
    const queryParam = searchParams.get('q');
    const rawValue = tickerParam || cikParam || queryParam || '';
    const resolvedSearch = resolveSearchInput(rawValue);
    const nextFormType = searchParams.get('formType') || '';
    const nextDateFrom = searchParams.get('dateFrom') || '';
    const nextDateTo = searchParams.get('dateTo') || '';
    const nextKeywords = searchParams.get('keywords')
      ? searchParams.get('keywords')!.split(',').map(keyword => keyword.trim()).filter(Boolean)
      : [];
    const nextIncludeRemote = searchParams.get('includeRemote') === '1';
    const nextRemoteMaxFormsRaw = Number(searchParams.get('remoteMaxForms') || '25');
    const nextRemoteMaxForms = Number.isFinite(nextRemoteMaxFormsRaw)
      ? Math.max(1, Math.min(500, nextRemoteMaxFormsRaw))
      : 25;

    setSearchTerm(resolvedSearch.value);
    setSearchMode(tickerParam ? 'ticker' : cikParam ? 'cik' : resolvedSearch.mode);
    setSelectedFormType(nextFormType);
    setDateFrom(nextDateFrom);
    setDateTo(nextDateTo);
    setKeywords(nextKeywords);
    setIncludeRemote(nextIncludeRemote);
    setRemoteMaxForms(nextRemoteMaxForms);
    setRemoteError(null);
    setRemoteSearchResult(null);
    setKeywordInput('');
    setCurrentPage(0);
    setSortField('filingDate');
    setSortDirection('desc');

    const shouldAutoSearch = searchParams.get('autoSearch') === '1';
    const hasIncomingFilters = Boolean(rawValue || nextFormType || nextDateFrom || nextDateTo || nextKeywords.length > 0);

    if (shouldAutoSearch && hasIncomingFilters) {
      setHasSearched(true);
      const localRequest = {
        companyName: resolvedSearch.value && !tickerParam && !cikParam && resolvedSearch.mode === 'company' ? resolvedSearch.value : undefined,
        ticker: tickerParam ? tickerParam.toUpperCase() : resolvedSearch.mode === 'ticker' ? resolvedSearch.value : undefined,
        cik: cikParam || (resolvedSearch.mode === 'cik' ? resolvedSearch.value : undefined),
        formTypes: nextFormType ? [nextFormType] : undefined,
        dateFrom: nextDateFrom || undefined,
        dateTo: nextDateTo || undefined,
        keywords: nextKeywords.length > 0 ? nextKeywords : undefined,
        page: 0,
        size: pageSize,
        sortBy: 'filingDate',
        sortDir: 'desc',
      };
      search(localRequest);
      if (nextIncludeRemote && nextFormType) {
        setRemoteLoading(true);
        void remoteEdgarApi.searchFilings({
          companyName: localRequest.companyName,
          ticker: localRequest.ticker,
          cik: localRequest.cik,
          formType: nextFormType,
          dateFrom: nextDateFrom || undefined,
          dateTo: nextDateTo || undefined,
          limit: nextDateFrom && nextDateTo ? 100 : nextRemoteMaxForms,
        })
          .then((data) => {
            setRemoteSearchResult(data);
            setRemoteError(null);
          })
          .catch((err) => {
            setRemoteError(normalizeRemoteSearchError(err));
            setRemoteSearchResult(null);
          })
          .finally(() => {
            setRemoteLoading(false);
          });
      }
    } else if (!hasIncomingFilters) {
      setHasSearched(false);
    }
  }, [searchParams, resolveSearchInput, search, pageSize, normalizeRemoteSearchError]);

  // Trigger search when page or sort changes after initial search
  useEffect(() => {
    if (hasSearched) {
      search(buildSearchRequest());
    }
  }, [currentPage, pageSize, sortField, sortDirection, hasSearched, search, buildSearchRequest]);

  const handleSearch = async () => {
    setCurrentPage(0);
    setHasSearched(true);
    const request = buildSearchRequest({ page: 0 });

    await Promise.allSettled([
      search(request),
      searchRemote(),
    ]);

    // Add to search history
    const searchDesc = searchTerm || selectedFormType || 'All filings';
    addSearch(searchDesc, includeRemote ? 'filing-search+remote' : 'filing-search');
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
    setSearchMode('company');
    setSelectedFormType('');
    setDateFrom('');
    setDateTo('');
    setKeywords([]);
    setIncludeRemote(false);
    setRemoteMaxForms(25);
    setRemoteError(null);
    setRemoteSearchResult(null);
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
              onChange={(e) => {
                const resolved = resolveSearchInput(e.target.value);
                setSearchTerm(resolved.value);
                setSearchMode(resolved.mode);
              }}
              onKeyPress={(e) => e.key === 'Enter' && void handleSearch()}
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

          <div className="md:col-span-2 border border-gray-200 rounded-md p-4 bg-gray-50">
            <div className="flex items-start gap-3">
              <input
                id="include-remote-search"
                type="checkbox"
                checked={includeRemote}
                onChange={(e) => {
                  setIncludeRemote(e.target.checked);
                  if (!e.target.checked) {
                    setRemoteError(null);
                    setRemoteSearchResult(null);
                  }
                }}
                className="mt-1 h-4 w-4 rounded border-gray-300"
              />
              <div className="flex-1">
                <label htmlFor="include-remote-search" className="block text-sm">
                  Also search remote SEC filings
                </label>
                <p className="mt-1 text-xs text-gray-600">
                  Remote search uses Company/Ticker/CIK, Form Type, and Date Range. Keywords remain local-only.
                </p>
              </div>
            </div>

            {includeRemote && !dateFrom && !dateTo && (
              <div className="mt-3 max-w-xs">
                <label className="block text-sm mb-2">Max Remote Forms</label>
                <input
                  type="number"
                  min={1}
                  max={500}
                  value={remoteMaxForms}
                  onChange={(e) => setRemoteMaxForms(Number(e.target.value || 25))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <p className="mt-1 text-xs text-gray-500">
                  Used only when From Date and To Date are both empty.
                </p>
              </div>
            )}
          </div>
        </div>

        <div className="flex gap-3 mt-6">
          <button
            onClick={() => void handleSearch()}
            disabled={loading || remoteLoading}
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2 disabled:opacity-50"
          >
            {loading || remoteLoading ? (
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
              {includeRemote ? 'Local Search Results' : 'Search Results'} ({totalElements.toLocaleString()} filings)
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

      {hasSearched && includeRemote && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="flex items-center gap-2">
              <Filter className="w-5 h-5" />
              Remote SEC Results ({remoteSearchResult?.returnedMatches.toLocaleString() || 0} filings)
            </h3>
            {remoteSearchResult && (
              <div className="text-sm text-gray-600">
                {remoteSearchResult.dateFrom} to {remoteSearchResult.dateTo}
              </div>
            )}
          </div>

          {remoteError && (
            <ErrorMessage
              message={remoteError}
              onRetry={() => {
                void searchRemote();
              }}
            />
          )}

          {!remoteError && remoteLoading && (
            <div className="py-12">
              <LoadingSpinner size="lg" text="Searching remote SEC filings..." />
            </div>
          )}

          {!remoteError && !remoteLoading && remoteSearchResult && (
            <div className="space-y-4">
              <div className="flex flex-wrap gap-4 text-sm text-gray-600">
                <span>{remoteSearchResult.totalMatches.toLocaleString()} matching remote filings</span>
                <span>{remoteSearchResult.uniqueCompanyCount.toLocaleString()} companies</span>
                <span>{remoteSearchResult.availableDateCount} / {remoteSearchResult.searchedDateCount} daily indexes found</span>
              </div>

              {remoteSearchResult.truncated && (
                <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                  {dateFrom && dateTo
                    ? 'Remote SEC results were truncated to the current preview limit.'
                    : `Remote SEC search stopped after finding ${remoteSearchResult.returnedMatches.toLocaleString()} forms. Increase Max Remote Forms or add dates to search deeper.`}
                </div>
              )}

              {remoteSearchResult.filings.length === 0 ? (
                <EmptyState
                  type="search"
                  message="No remote SEC filings match your search criteria."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 text-left text-sm">Company</th>
                        <th className="px-4 py-3 text-left text-sm">CIK</th>
                        <th className="px-4 py-3 text-left text-sm">Form</th>
                        <th className="px-4 py-3 text-left text-sm">Filing Date</th>
                        <th className="px-4 py-3 text-left text-sm">Accession #</th>
                        <th className="px-4 py-3 text-left text-sm">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {remoteSearchResult.filings.map((filing) => (
                        <tr key={`${filing.cik}-${filing.accessionNumber || filing.archivePath}`} className="hover:bg-gray-50">
                          <td className="px-4 py-3">
                            <div className="text-sm text-gray-900">{filing.companyName}</div>
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
                            <span className="font-mono text-xs text-gray-600">{filing.accessionNumber || '-'}</span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-3">
                              <a
                                href={filing.filingUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-blue-600 hover:underline text-sm"
                              >
                                Open SEC
                              </a>
                              <a
                                href={`https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${encodeURIComponent(filing.cik)}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-gray-500 hover:text-gray-700"
                              >
                                <ExternalLink className="w-4 h-4" />
                              </a>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
