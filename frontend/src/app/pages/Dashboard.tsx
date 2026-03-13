import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, TrendingUp, Building2, Clock, FileText, RefreshCw, ArrowUpRight, ArrowDownRight, ShoppingCart } from 'lucide-react';
import { useDashboard, useTopInsiderPurchases } from '../hooks';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { buildForm4SearchUrl } from '../utils';

export function Dashboard() {
  const navigate = useNavigate();
  const [quickSearch, setQuickSearch] = React.useState('');
  const { stats, recentSearches, recentFilings, loading, error, refresh } = useDashboard();
  const {
    purchases: topPurchases,
    loading: topLoading,
    error: topError,
    refresh: refreshTopPurchases,
  } = useTopInsiderPurchases(5);

  const buildQuickSearchUrl = (value: string) => {
    const trimmedValue = value.trim();
    const params = new URLSearchParams({
      autoSearch: '1',
      dateTo: new Date().toISOString().split('T')[0],
    });

    if (/^\d+$/.test(trimmedValue)) {
      params.set('cik', trimmedValue);
    } else if (/^[A-Za-z][A-Za-z0-9.-]{0,9}$/.test(trimmedValue)) {
      params.set('ticker', trimmedValue.toUpperCase());
    } else {
      params.set('q', trimmedValue);
    }

    return `/search?${params.toString()}`;
  };

  const handleQuickSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (quickSearch.trim()) {
      navigate(buildQuickSearchUrl(quickSearch));
    }
  };

  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString();
    } catch {
      return dateString;
    }
  };

  const formatDateTime = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return date.toLocaleString();
    } catch {
      return dateString;
    }
  };

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

      {error && (
        <ErrorMessage
          title="Failed to load dashboard"
          message={error}
          onRetry={refresh}
        />
      )}

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
            {loading ? (
              <LoadingSpinner size="sm" className="mt-2" />
            ) : (
              <p className="text-3xl mt-1">{(stats?.totalFilings ?? 0).toLocaleString()}</p>
            )}
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
            {loading ? (
              <LoadingSpinner size="sm" className="mt-2" />
            ) : (
              <p className="text-3xl mt-1">{(stats?.companiesTracked ?? 0).toLocaleString()}</p>
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-2">
            <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <Clock className="w-6 h-6 text-purple-600" />
            </div>
            <button
              onClick={refresh}
              className="p-1 hover:bg-gray-100 rounded"
              title="Refresh"
            >
              <RefreshCw className={`w-5 h-5 text-gray-400 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
          <div className="mt-4">
            <p className="text-gray-600 text-sm">Last Sync</p>
            {loading ? (
              <LoadingSpinner size="sm" className="mt-2" />
            ) : (
              <p className="text-lg mt-1 font-mono">
                {stats?.lastSync ? formatDateTime(stats.lastSync) : 'Never'}
              </p>
            )}
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
          {loading ? (
            <LoadingSpinner size="md" className="py-8" />
          ) : recentSearches.length === 0 ? (
            <EmptyState
              type="search"
              title="No recent searches"
              message="Your search history will appear here."
            />
          ) : (
            <div className="space-y-3">
              {recentSearches.map((search) => (
                <div
                  key={search.id}
                  className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0"
                >
                  <button
                    onClick={() => navigate(buildQuickSearchUrl(search.query))}
                    className="text-blue-600 hover:underline text-left"
                  >
                    {search.query}
                  </button>
                  <span className="text-sm text-gray-500 font-mono">
                    {formatDate(search.timestamp)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Activity Feed */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h3 className="mb-4 flex items-center gap-2">
            <FileText className="w-5 h-5" />
            Recent Filing Alerts
          </h3>
          {loading ? (
            <LoadingSpinner size="md" className="py-8" />
          ) : recentFilings.length === 0 ? (
            <EmptyState
              type="filings"
              title="No recent filings"
              message="Download company data to see filings here."
              action={{
                label: 'Go to Downloads',
                onClick: () => navigate('/downloads'),
              }}
            />
          ) : (
            <div className="space-y-3">
              {recentFilings.map((filing) => (
                <div
                  key={filing.id}
                  className="flex items-start gap-3 py-2 border-b border-gray-100 last:border-0"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <FormTypeBadge formType={filing.formType} />
                      <span className="text-gray-900">{filing.ticker || filing.cik}</span>
                    </div>
                    <p className="text-sm text-gray-600 truncate">{filing.companyName}</p>
                    <p className="text-xs text-gray-500 font-mono mt-1">
                      {formatDate(filing.filingDate)}
                    </p>
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
          )}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="mb-4 flex items-center gap-2">
          <ShoppingCart className="w-5 h-5" />
          <h3>Top Insider Purchases</h3>
          <div className="ml-auto flex items-center gap-3">
            <button
              onClick={refreshTopPurchases}
              className="p-1 hover:bg-gray-100 rounded"
              title="Refresh insider purchases"
            >
              <RefreshCw className={`w-4 h-4 text-gray-400 ${topLoading ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={() => navigate('/insider-purchases')}
              className="text-sm text-blue-600 hover:underline"
            >
              View All
            </button>
          </div>
        </div>

        {topError ? (
          <ErrorMessage
            title="Failed to load insider purchases"
            message={topError}
            onRetry={refreshTopPurchases}
          />
        ) : topLoading ? (
          <LoadingSpinner size="md" className="py-8" />
        ) : topPurchases.length === 0 ? (
          <EmptyState
            type="filings"
            title="No recent insider purchases"
            message="Insider purchase data will appear once Form 4 filings are synced."
          />
        ) : (
          <div className="space-y-2">
            {topPurchases.map((purchase, index) => (
              <div
                key={`${purchase.accessionNumber}-${index}`}
                className="flex items-center justify-between gap-3 py-2 border-b border-gray-100 last:border-0"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <span
                    className={`min-w-[72px] text-sm font-semibold flex items-center gap-0.5 ${
                      (purchase.percentChange ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'
                    }`}
                  >
                    {(purchase.percentChange ?? 0) >= 0 ? (
                      <ArrowUpRight className="w-3.5 h-3.5" />
                    ) : (
                      <ArrowDownRight className="w-3.5 h-3.5" />
                    )}
                    {purchase.percentChange != null
                      ? `${purchase.percentChange >= 0 ? '+' : ''}${purchase.percentChange.toFixed(1)}%`
                      : '-'}
                  </span>
                  <button
                    onClick={() => navigate(buildForm4SearchUrl({ ticker: purchase.ticker, cik: purchase.cik }))}
                    className="truncate text-left text-sm text-blue-600 hover:underline"
                    title={purchase.companyName ?? purchase.ticker}
                  >
                    {purchase.companyName ?? purchase.ticker}
                  </button>
                </div>
                <span className="text-xs text-gray-500 font-mono whitespace-nowrap">{purchase.ticker}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
