import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, TrendingUp, Building2, Clock, FileText } from 'lucide-react';
import { stats, recentSearches, filings } from '../data/mockData';
import { FormTypeBadge } from '../components/FormTypeBadge';

export function Dashboard() {
  const navigate = useNavigate();
  const [quickSearch, setQuickSearch] = React.useState('');
  
  const handleQuickSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (quickSearch) {
      navigate(`/search?q=${encodeURIComponent(quickSearch)}`);
    }
  };
  
  const recentFilings = filings.slice(0, 5);
  
  return (
    <div className="space-y-6">
      {/* Quick Search */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Quick Search</h2>
        <form onSubmit={handleQuickSearch} className="flex gap-2">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              value={quickSearch}
              onChange={(e) => setQuickSearch(e.target.value)}
              placeholder="Company name, ticker, or CIK..."
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            type="submit"
            className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors"
          >
            Search
          </button>
        </form>
      </div>
      
      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-2">
            <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
              <FileText className="w-6 h-6 text-blue-600" />
            </div>
            <TrendingUp className="w-5 h-5 text-green-500" />
          </div>
          <div className="mt-4">
            <p className="text-gray-600 text-sm">Total Filings Indexed</p>
            <p className="text-3xl mt-1">{stats.totalFilings.toLocaleString()}</p>
          </div>
        </div>
        
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-2">
            <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
              <Building2 className="w-6 h-6 text-green-600" />
            </div>
            <TrendingUp className="w-5 h-5 text-green-500" />
          </div>
          <div className="mt-4">
            <p className="text-gray-600 text-sm">Companies Tracked</p>
            <p className="text-3xl mt-1">{stats.companiesTracked.toLocaleString()}</p>
          </div>
        </div>
        
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-2">
            <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <Clock className="w-6 h-6 text-purple-600" />
            </div>
          </div>
          <div className="mt-4">
            <p className="text-gray-600 text-sm">Last Sync</p>
            <p className="text-lg mt-1 font-mono">{stats.lastSync}</p>
          </div>
        </div>
      </div>
      
      {/* Two Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Searches */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h3 className="mb-4 flex items-center gap-2">
            <Clock className="w-5 h-5" />
            Recent Searches
          </h3>
          <div className="space-y-3">
            {recentSearches.map((search, index) => (
              <div key={index} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
                <button
                  onClick={() => navigate(`/search?q=${encodeURIComponent(search.query)}`)}
                  className="text-blue-600 hover:underline"
                >
                  {search.query}
                </button>
                <span className="text-sm text-gray-500 font-mono">{search.timestamp.split(' ')[0]}</span>
              </div>
            ))}
          </div>
        </div>
        
        {/* Activity Feed */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h3 className="mb-4 flex items-center gap-2">
            <FileText className="w-5 h-5" />
            Recent Filing Alerts
          </h3>
          <div className="space-y-3">
            {recentFilings.map((filing) => (
              <div key={filing.id} className="flex items-start gap-3 py-2 border-b border-gray-100 last:border-0">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <FormTypeBadge formType={filing.formType} />
                    <span className="text-gray-900">{filing.ticker}</span>
                  </div>
                  <p className="text-sm text-gray-600 truncate">{filing.companyName}</p>
                  <p className="text-xs text-gray-500 font-mono mt-1">{filing.filingDate}</p>
                </div>
                <button
                  onClick={() => navigate(`/filing/${filing.id}`)}
                  className="text-sm text-blue-600 hover:underline whitespace-nowrap"
                >
                  View
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
