import React from 'react';
import { Search, X, Download, ExternalLink, Filter, Info } from 'lucide-react';
import { filings, formTypes, companies } from '../data/mockData';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useNavigate } from 'react-router-dom';

export function FilingSearch() {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = React.useState('');
  const [selectedFormType, setSelectedFormType] = React.useState('');
  const [dateFrom, setDateFrom] = React.useState('');
  const [dateTo, setDateTo] = React.useState('');
  const [keywords, setKeywords] = React.useState<string[]>([]);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [userAgent, setUserAgent] = React.useState('edgar4j/1.0 (contact@example.com)');
  const [pageSize, setPageSize] = React.useState(25);
  const [currentPage, setCurrentPage] = React.useState(1);
  const [sortField, setSortField] = React.useState<'filingDate' | 'wordHits'>('filingDate');
  const [sortDirection, setSortDirection] = React.useState<'asc' | 'desc'>('desc');
  
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
  };
  
  // Filter and sort filings
  const filteredFilings = React.useMemo(() => {
    let results = [...filings];
    
    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      results = results.filter(f => 
        f.companyName.toLowerCase().includes(search) ||
        f.ticker.toLowerCase().includes(search) ||
        f.cik.includes(search)
      );
    }
    
    if (selectedFormType) {
      results = results.filter(f => f.formType === selectedFormType);
    }
    
    if (dateFrom) {
      results = results.filter(f => f.filingDate >= dateFrom);
    }
    
    if (dateTo) {
      results = results.filter(f => f.filingDate <= dateTo);
    }
    
    // Sort
    results.sort((a, b) => {
      const aVal = a[sortField];
      const bVal = b[sortField];
      const multiplier = sortDirection === 'asc' ? 1 : -1;
      return aVal > bVal ? multiplier : -multiplier;
    });
    
    return results;
  }, [searchTerm, selectedFormType, dateFrom, dateTo, sortField, sortDirection]);
  
  // Pagination
  const totalPages = Math.ceil(filteredFilings.length / pageSize);
  const paginatedFilings = filteredFilings.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );
  
  const maxWordHits = Math.max(...filteredFilings.map(f => f.wordHits), 1);
  
  const handleSort = (field: 'filingDate' | 'wordHits') => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('desc');
    }
  };
  
  const exportData = (format: 'csv' | 'json') => {
    console.log(`Exporting ${filteredFilings.length} filings as ${format.toUpperCase()}`);
    alert(`Export as ${format.toUpperCase()} - ${filteredFilings.length} records`);
  };
  
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
            >
              <option value="">All Form Types</option>
              {formTypes.map(ft => (
                <option key={ft.value} value={ft.value}>{ft.label}</option>
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
          
          {/* User Agent */}
          <div className="md:col-span-2">
            <div className="flex items-center gap-2 mb-2">
              <label className="block text-sm">User-Agent <span className="text-red-500">*</span></label>
              <div className="group relative">
                <Info className="w-4 h-4 text-gray-400 cursor-help" />
                <div className="hidden group-hover:block absolute left-0 top-6 bg-gray-900 text-white text-xs rounded p-2 w-96 z-10">
                  SEC requires all automated requests to include a User-Agent header identifying the requester.
                  Format: ApplicationName/Version (contact email)
                </div>
              </div>
            </div>
            <input
              type="text"
              value={userAgent}
              onChange={(e) => setUserAgent(e.target.value)}
              placeholder="YourApp/1.0 (your@email.com)"
              className="w-full px-3 py-2 border border-gray-300 rounded-md font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
        
        <div className="flex gap-3 mt-6">
          <button className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2">
            <Search className="w-4 h-4" />
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
      
      {/* Results */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="flex items-center gap-2">
            <Filter className="w-5 h-5" />
            Search Results ({filteredFilings.length} filings)
          </h3>
          <div className="flex items-center gap-3">
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setCurrentPage(1);
              }}
              className="px-3 py-1 border border-gray-300 rounded-md text-sm"
            >
              <option value={10}>10 per page</option>
              <option value={25}>25 per page</option>
              <option value={50}>50 per page</option>
              <option value={100}>100 per page</option>
            </select>
            <button
              onClick={() => exportData('csv')}
              className="px-3 py-1 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1 text-sm"
            >
              <Download className="w-4 h-4" />
              CSV
            </button>
            <button
              onClick={() => exportData('json')}
              className="px-3 py-1 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-1 text-sm"
            >
              <Download className="w-4 h-4" />
              JSON
            </button>
          </div>
        </div>
        
        {/* Results Table */}
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
              {paginatedFilings.map((filing) => (
                <tr key={filing.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-gray-900">{filing.ticker}</span>
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
                          style={{ width: `${(filing.wordHits / maxWordHits) * 100}%` }}
                        />
                      </div>
                      <span className="text-sm w-8 text-right">{filing.wordHits}</span>
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
                      <button className="text-gray-500 hover:text-gray-700">
                        <ExternalLink className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        
        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-200">
            <div className="text-sm text-gray-600">
              Showing {(currentPage - 1) * pageSize + 1} to {Math.min(currentPage * pageSize, filteredFilings.length)} of {filteredFilings.length} results
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                className="px-3 py-1 border border-gray-300 rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Previous
              </button>
              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                const page = i + 1;
                return (
                  <button
                    key={page}
                    onClick={() => setCurrentPage(page)}
                    className={`px-3 py-1 border rounded-md text-sm ${
                      currentPage === page
                        ? 'bg-[#1a1f36] text-white border-[#1a1f36]'
                        : 'border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    {page}
                  </button>
                );
              })}
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                className="px-3 py-1 border border-gray-300 rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
