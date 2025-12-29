import React, { useCallback } from 'react';
import { Search, Users, Shield, AlertTriangle, ExternalLink, RefreshCw, TrendingUp, Building } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useRecentForm13DG, useForm13DGSearch, useOwnershipSnapshot } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form13DG as Form13DGType, BeneficialOwnershipSnapshot } from '../api/types';

function formatNumber(value: number): string {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(2)}B`;
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(2)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(2)}K`;
  }
  return value.toLocaleString();
}

function formatPercent(value: number | undefined): string {
  if (value === undefined) return '-';
  return `${value.toFixed(2)}%`;
}

function ScheduleTypeBadge({ type }: { type: string }) {
  const is13D = type === '13D' || type === 'SCHEDULE 13D';
  return (
    <span className={`px-2 py-1 rounded text-xs font-medium ${
      is13D ? 'bg-orange-100 text-orange-800' : 'bg-green-100 text-green-800'
    }`}>
      {is13D ? '13D (Activist)' : '13G (Passive)'}
    </span>
  );
}

function FilingCard({ filing, onClick }: { filing: Form13DGType; onClick: () => void }) {
  const is13D = filing.scheduleType === '13D' || filing.scheduleType === 'SCHEDULE 13D';

  return (
    <div
      className="bg-white rounded-lg shadow-sm p-4 hover:shadow-md transition-shadow cursor-pointer border border-gray-100"
      onClick={onClick}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1">
          <h3 className="font-semibold text-gray-900">{filing.filingPersonName}</h3>
          <p className="text-sm text-gray-500">Filing on: {filing.issuerName}</p>
        </div>
        <ScheduleTypeBadge type={filing.scheduleType} />
      </div>
      <div className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <p className="text-gray-500">Ownership</p>
          <p className="font-medium text-lg">{formatPercent(filing.percentOfClass)}</p>
        </div>
        <div>
          <p className="text-gray-500">Shares</p>
          <p className="font-medium">{formatNumber(filing.sharesBeneficiallyOwned || 0)}</p>
        </div>
        <div>
          <p className="text-gray-500">Event Date</p>
          <p className="font-medium">{filing.eventDate}</p>
        </div>
        <div>
          <p className="text-gray-500">CUSIP</p>
          <p className="font-medium font-mono text-xs">{filing.cusip}</p>
        </div>
      </div>
      {is13D && filing.purposeOfTransaction && (
        <div className="mt-3 pt-3 border-t border-gray-100">
          <p className="text-xs text-gray-500">Purpose</p>
          <p className="text-sm line-clamp-2">{filing.purposeOfTransaction}</p>
        </div>
      )}
    </div>
  );
}

function FilingDetail({ filing, onClose }: { filing: Form13DGType; onClose: () => void }) {
  const is13D = filing.scheduleType === '13D' || filing.scheduleType === 'SCHEDULE 13D';

  return (
    <div className="bg-white rounded-lg shadow-sm">
      <div className="border-b border-gray-200 p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 className="text-lg font-semibold">{filing.filingPersonName}</h2>
              <ScheduleTypeBadge type={filing.scheduleType} />
            </div>
            <p className="text-gray-500">
              Beneficial ownership of {filing.issuerName} ({filing.cusip})
            </p>
          </div>
          <div className="flex items-center gap-3">
            <a
              href={`https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${filing.filingPersonCik || filing.issuerCik}&type=SC%2013&dateb=&owner=include&count=40`}
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
        {/* Ownership Stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-gray-500 text-sm">Percent of Class</p>
            <p className="text-2xl font-bold text-blue-600">{formatPercent(filing.percentOfClass)}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-gray-500 text-sm">Shares Owned</p>
            <p className="text-2xl font-bold">{formatNumber(filing.sharesBeneficiallyOwned || 0)}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-gray-500 text-sm">Sole Voting Power</p>
            <p className="text-2xl font-bold">{formatNumber(filing.votingPowerSole || 0)}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-gray-500 text-sm">Shared Voting Power</p>
            <p className="text-2xl font-bold">{formatNumber(filing.votingPowerShared || 0)}</p>
          </div>
        </div>

        {/* Filing Details */}
        <div>
          <h3 className="font-medium mb-3">Filing Information</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div>
              <p className="text-gray-500">Accession Number</p>
              <p className="font-mono">{filing.accessionNumber}</p>
            </div>
            <div>
              <p className="text-gray-500">Event Date</p>
              <p>{filing.eventDate}</p>
            </div>
            <div>
              <p className="text-gray-500">Filed Date</p>
              <p>{filing.filedDate}</p>
            </div>
            <div>
              <p className="text-gray-500">Security Title</p>
              <p>{filing.securityTitle || 'Common Stock'}</p>
            </div>
            {filing.amendmentNumber && (
              <div>
                <p className="text-gray-500">Amendment</p>
                <p>#{filing.amendmentNumber} ({filing.amendmentType})</p>
              </div>
            )}
            {filing.citizenshipOrOrganization && (
              <div>
                <p className="text-gray-500">Citizenship/Organization</p>
                <p>{filing.citizenshipOrOrganization}</p>
              </div>
            )}
          </div>
        </div>

        {/* Address */}
        {filing.filingPersonAddress && (
          <div>
            <h3 className="font-medium mb-3">Filer Address</h3>
            <div className="text-sm text-gray-700">
              {filing.filingPersonAddress.street1 && <p>{filing.filingPersonAddress.street1}</p>}
              {filing.filingPersonAddress.street2 && <p>{filing.filingPersonAddress.street2}</p>}
              <p>
                {filing.filingPersonAddress.city}, {filing.filingPersonAddress.stateOrCountry} {filing.filingPersonAddress.zipCode}
              </p>
            </div>
          </div>
        )}

        {/* Dispositive Power */}
        <div>
          <h3 className="font-medium mb-3">Dispositive Power</h3>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Sole</p>
              <p className="font-medium">{formatNumber(filing.dispositivePowerSole || 0)}</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Shared</p>
              <p className="font-medium">{formatNumber(filing.dispositivePowerShared || 0)}</p>
            </div>
          </div>
        </div>

        {/* 13D Specific Fields */}
        {is13D && (
          <>
            {filing.purposeOfTransaction && (
              <div>
                <h3 className="font-medium mb-3 flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5 text-orange-500" />
                  Purpose of Transaction
                </h3>
                <p className="text-sm text-gray-700 bg-orange-50 rounded-lg p-4">
                  {filing.purposeOfTransaction}
                </p>
              </div>
            )}
            {filing.sourceOfFunds && filing.sourceOfFunds.length > 0 && (
              <div>
                <h3 className="font-medium mb-3">Source of Funds</h3>
                <div className="flex gap-2 flex-wrap">
                  {filing.sourceOfFunds.map((source, idx) => (
                    <span key={idx} className="px-3 py-1 bg-gray-100 rounded-full text-sm">
                      {source}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </>
        )}

        {/* Additional Reporting Persons */}
        {filing.additionalReportingPersons && filing.additionalReportingPersons.length > 0 && (
          <div>
            <h3 className="font-medium mb-3">Additional Reporting Persons</h3>
            <div className="space-y-3">
              {filing.additionalReportingPersons.map((person, idx) => (
                <div key={idx} className="bg-gray-50 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <p className="font-medium">{person.name}</p>
                    <span className="text-sm text-gray-500">{formatPercent(person.percentOfClass)}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    <p><span className="text-gray-500">Shares:</span> {formatNumber(person.sharesBeneficiallyOwned || 0)}</p>
                    <p><span className="text-gray-500">Organization:</span> {person.citizenshipOrOrganization || '-'}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Signature */}
        {filing.signatureName && (
          <div className="border-t border-gray-200 pt-4">
            <p className="text-sm text-gray-500">
              Signed by {filing.signatureName}
              {filing.signatureTitle && `, ${filing.signatureTitle}`}
              {filing.signatureDate && ` on ${filing.signatureDate}`}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

export function Form13DGPage() {
  const navigate = useNavigate();
  const [searchType, setSearchType] = React.useState<'filer' | 'issuer' | 'cusip' | 'schedule'>('filer');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [hasSearched, setHasSearched] = React.useState(false);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(12);
  const [selectedFiling, setSelectedFiling] = React.useState<Form13DGType | null>(null);
  const [filterType, setFilterType] = React.useState<'all' | '13D' | '13G'>('all');

  const { filings: recentFilings, loading: recentLoading, error: recentError, refresh } = useRecentForm13DG(12);
  const {
    filings: searchResults,
    loading: searchLoading,
    error: searchError,
    totalElements,
    totalPages,
    searchByFilerName,
    searchByIssuerName,
    searchByCusip,
    searchByScheduleType,
    getActivistFilings,
  } = useForm13DGSearch();

  const handleSearch = useCallback(() => {
    setHasSearched(true);
    setCurrentPage(0);

    if (searchType === 'schedule') {
      if (filterType === 'all') {
        // Show recent - no specific search
        setHasSearched(false);
        return;
      }
      searchByScheduleType(filterType, 0, pageSize);
      return;
    }

    if (!searchTerm.trim()) return;

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
    }
  }, [searchType, searchTerm, filterType, pageSize, searchByFilerName, searchByIssuerName, searchByCusip, searchByScheduleType]);

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
      case 'schedule':
        if (filterType !== 'all') {
          searchByScheduleType(filterType, page, pageSize);
        }
        break;
    }
  }, [searchType, searchTerm, filterType, pageSize, searchByFilerName, searchByIssuerName, searchByCusip, searchByScheduleType]);

  const handleActivistFilter = useCallback(() => {
    setHasSearched(true);
    setCurrentPage(0);
    getActivistFilings(0, pageSize);
  }, [getActivistFilings, pageSize]);

  const loading = recentLoading || searchLoading;
  const error = recentError || searchError;
  const displayFilings = hasSearched ? searchResults : recentFilings;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Users className="w-8 h-8 text-purple-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 13D/G Filings</h1>
              <p className="text-gray-500 text-sm">Beneficial ownership disclosures (5%+ ownership)</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleActivistFilter}
              className="px-4 py-2 border border-orange-300 text-orange-700 rounded-md hover:bg-orange-50 flex items-center gap-2 text-sm"
            >
              <AlertTriangle className="w-4 h-4" />
              Activist Only (13D)
            </button>
            <button
              onClick={refresh}
              className="p-2 hover:bg-gray-100 rounded-full"
              title="Refresh"
            >
              <RefreshCw className="w-5 h-5 text-gray-600" />
            </button>
          </div>
        </div>

        {/* Search */}
        <div className="flex gap-3">
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as 'filer' | 'issuer' | 'cusip' | 'schedule')}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
          >
            <option value="filer">Filer Name</option>
            <option value="issuer">Issuer Name</option>
            <option value="cusip">CUSIP</option>
            <option value="schedule">Schedule Type</option>
          </select>
          {searchType === 'schedule' ? (
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as 'all' | '13D' | '13G')}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
            >
              <option value="all">All Schedules</option>
              <option value="13D">13D (Activist)</option>
              <option value="13G">13G (Passive)</option>
            </select>
          ) : (
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={
                searchType === 'filer' ? 'e.g., Carl Icahn, Pershing Square' :
                searchType === 'issuer' ? 'e.g., Apple, Tesla' :
                'e.g., 037833100'
              }
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
          )}
          <button
            onClick={handleSearch}
            disabled={loading || (searchType !== 'schedule' && !searchTerm.trim())}
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
            {hasSearched ? `Search Results (${totalElements.toLocaleString()} filings)` : 'Recent 13D/G Filings'}
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
