import React, { useCallback } from 'react';
import { Search, FileText, Calendar, Building2, ExternalLink, RefreshCw, ChevronDown, ChevronUp, Tag } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useRecentForm8K, useForm8KSearch } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form8K as Form8KType, FORM_8K_ITEMS } from '../api/types';

function getItemDescription(itemNumber: string): string {
  return FORM_8K_ITEMS[itemNumber] || itemNumber;
}

function parseItems(itemsStr: string | undefined): string[] {
  if (!itemsStr) return [];
  return itemsStr.split(/[,;]/).map(s => s.trim()).filter(Boolean);
}

function ItemBadge({ item }: { item: string }) {
  const description = getItemDescription(item);
  const category = item.split('.')[0];

  const categoryColors: Record<string, string> = {
    '1': 'bg-red-100 text-red-800',
    '2': 'bg-orange-100 text-orange-800',
    '3': 'bg-yellow-100 text-yellow-800',
    '4': 'bg-green-100 text-green-800',
    '5': 'bg-blue-100 text-blue-800',
    '6': 'bg-purple-100 text-purple-800',
    '7': 'bg-pink-100 text-pink-800',
    '8': 'bg-gray-100 text-gray-800',
    '9': 'bg-indigo-100 text-indigo-800',
  };

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium ${categoryColors[category] || 'bg-gray-100 text-gray-800'}`}
      title={description}
    >
      {item}
    </span>
  );
}

function FilingCard({ filing, onClick }: { filing: Form8KType; onClick: () => void }) {
  const items = parseItems(filing.items);

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
            <span className="px-2 py-0.5 bg-gray-100 text-gray-700 rounded text-xs">{filing.formType}</span>
          </div>
          <h3 className="font-medium text-gray-900 line-clamp-1">{filing.companyName || `CIK: ${filing.cik}`}</h3>
        </div>
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

      {items.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {items.slice(0, 3).map((item, idx) => (
            <ItemBadge key={idx} item={item} />
          ))}
          {items.length > 3 && (
            <span className="text-xs text-gray-500">+{items.length - 3} more</span>
          )}
        </div>
      )}
    </div>
  );
}

function FilingDetail({ filing, onClose }: { filing: Form8KType; onClose: () => void }) {
  const [expandedSections, setExpandedSections] = React.useState<Set<number>>(new Set([0]));
  const items = parseItems(filing.items);

  const toggleSection = (idx: number) => {
    setExpandedSections(prev => {
      const next = new Set(prev);
      if (next.has(idx)) {
        next.delete(idx);
      } else {
        next.add(idx);
      }
      return next;
    });
  };

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
              <span className="px-2 py-1 bg-gray-100 rounded text-sm">{filing.formType}</span>
            </div>
            <p className="text-gray-500 text-sm">
              Filed: {filing.filedDate}
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

        {/* Items */}
        {items.length > 0 && (
          <div>
            <h3 className="font-medium mb-3 flex items-center gap-2">
              <Tag className="w-5 h-5 text-gray-400" />
              Items Reported
            </h3>
            <div className="space-y-2">
              {items.map((item, idx) => (
                <div key={idx} className="flex items-start gap-3 bg-gray-50 rounded-lg p-3">
                  <ItemBadge item={item} />
                  <span className="text-sm text-gray-700">{getItemDescription(item)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Item Sections */}
        {filing.itemSections && filing.itemSections.length > 0 && (
          <div>
            <h3 className="font-medium mb-3">Item Details</h3>
            <div className="space-y-2">
              {filing.itemSections.map((section, idx) => (
                <div key={idx} className="border border-gray-200 rounded-lg">
                  <button
                    onClick={() => toggleSection(idx)}
                    className="w-full flex items-center justify-between p-4 text-left hover:bg-gray-50"
                  >
                    <div className="flex items-center gap-3">
                      <ItemBadge item={section.itemNumber} />
                      <span className="font-medium">{section.title}</span>
                    </div>
                    {expandedSections.has(idx) ? (
                      <ChevronUp className="w-5 h-5 text-gray-400" />
                    ) : (
                      <ChevronDown className="w-5 h-5 text-gray-400" />
                    )}
                  </button>
                  {expandedSections.has(idx) && section.content && (
                    <div className="px-4 pb-4">
                      <p className="text-sm text-gray-700 whitespace-pre-wrap bg-gray-50 rounded p-3 max-h-60 overflow-y-auto">
                        {section.content}
                      </p>
                    </div>
                  )}
                </div>
              ))}
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

export function Form8KPage() {
  const navigate = useNavigate();
  const [searchType, setSearchType] = React.useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(12);
  const [selectedFiling, setSelectedFiling] = React.useState<Form8KType | null>(null);

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm8K(12);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
  } = useForm8KSearch();

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
            <FileText className="w-8 h-8 text-orange-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 8-K Current Reports</h1>
              <p className="text-gray-500 text-sm">Material events and corporate changes</p>
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
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-orange-500"
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
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
              <span className="self-center text-gray-500">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-orange-500"
              />
            </>
          ) : (
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={searchType === 'symbol' ? 'e.g., AAPL, TSLA' : 'e.g., 0000320193'}
              className="flex-1 min-w-[200px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-orange-500"
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
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent 8-K Filings'}
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
