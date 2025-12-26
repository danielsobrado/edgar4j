import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Building2, MapPin, Calendar, FileText, Briefcase, Search } from 'lucide-react';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useCompanies, useCompanyFilings } from '../hooks';
import { LoadingSpinner, LoadingPage } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { CompanyResponse } from '../api';

export function Companies() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // State
  const [selectedCompany, setSelectedCompany] = React.useState<CompanyResponse | null>(null);
  const [searchTerm, setSearchTerm] = React.useState('');
  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(20);

  // API hooks
  const {
    companies,
    loading: companiesLoading,
    error: companiesError,
    totalElements,
    totalPages,
    search,
    refresh
  } = useCompanies();

  const {
    filings: companyFilings,
    loading: filingsLoading,
    error: filingsError,
    fetchByCompany
  } = useCompanyFilings();

  // Load companies on mount and when page changes
  useEffect(() => {
    search({
      name: searchTerm || undefined,
      page: currentPage,
      size: pageSize,
    });
  }, [currentPage, pageSize]);

  // Select first company when list loads
  useEffect(() => {
    if (companies.length > 0 && !selectedCompany) {
      const cikParam = searchParams.get('cik');
      if (cikParam) {
        const found = companies.find(c => c.cik === cikParam);
        if (found) {
          setSelectedCompany(found);
          return;
        }
      }
      setSelectedCompany(companies[0]);
    }
  }, [companies, searchParams]);

  // Load filings when company is selected
  useEffect(() => {
    if (selectedCompany) {
      fetchByCompany(selectedCompany.cik, 0, 10);
    }
  }, [selectedCompany]);

  const handleSearch = () => {
    setCurrentPage(0);
    search({
      name: searchTerm || undefined,
      page: 0,
      size: pageSize,
    });
  };

  const handleCompanySelect = (company: CompanyResponse) => {
    setSelectedCompany(company);
  };

  if (companiesLoading && companies.length === 0) {
    return <LoadingPage text="Loading companies..." />;
  }

  if (companiesError && companies.length === 0) {
    return (
      <div className="p-6">
        <ErrorMessage
          title="Failed to load companies"
          message={companiesError}
          onRetry={refresh}
        />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Company List */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4 flex items-center gap-2">
          <Building2 className="w-5 h-5" />
          Companies ({totalElements.toLocaleString()})
        </h2>

        {/* Search input */}
        <div className="mb-4">
          <div className="flex gap-2">
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="Search companies..."
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={handleSearch}
              className="px-3 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47]"
            >
              <Search className="w-4 h-4" />
            </button>
          </div>
        </div>

        {companies.length === 0 ? (
          <EmptyState
            type="companies"
            message="No companies found. Try downloading company data from the Downloads page."
            action={{
              label: 'Go to Downloads',
              onClick: () => navigate('/downloads'),
            }}
          />
        ) : (
          <>
            <div className="space-y-2 max-h-[500px] overflow-y-auto">
              {companies.map(company => (
                <button
                  key={company.cik}
                  onClick={() => handleCompanySelect(company)}
                  className={`w-full text-left p-3 rounded-lg transition-colors ${
                    selectedCompany?.cik === company.cik
                      ? 'bg-[#1a1f36] text-white'
                      : 'hover:bg-gray-100'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className={selectedCompany?.cik === company.cik ? 'text-white' : 'text-gray-900'}>
                          {company.ticker || company.cik}
                        </span>
                      </div>
                      <p className={`text-sm ${selectedCompany?.cik === company.cik ? 'text-gray-300' : 'text-gray-600'}`}>
                        {company.name}
                      </p>
                    </div>
                  </div>
                </button>
              ))}
            </div>

            {/* Pagination for company list */}
            {totalPages > 1 && (
              <div className="mt-4 pt-4 border-t">
                <Pagination
                  page={currentPage}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  size={pageSize}
                  onPageChange={setCurrentPage}
                  pageSizeOptions={[10, 20, 50]}
                />
              </div>
            )}
          </>
        )}
      </div>

      {/* Company Details */}
      <div className="lg:col-span-2 space-y-6">
        {!selectedCompany ? (
          <div className="bg-white rounded-lg shadow-sm p-6">
            <EmptyState
              type="companies"
              title="No company selected"
              message="Select a company from the list to view details."
            />
          </div>
        ) : (
          <>
            {/* Header Card */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h1>{selectedCompany.name}</h1>
                    {selectedCompany.ticker && (
                      <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full">
                        {selectedCompany.ticker}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 text-gray-600">
                    <span className="font-mono text-sm">CIK: {selectedCompany.cik}</span>
                  </div>
                </div>
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}`)}
                  className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] flex items-center gap-2"
                >
                  <FileText className="w-4 h-4" />
                  Search Filings
                </button>
              </div>
            </div>

            {/* Info Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="bg-white rounded-lg shadow-sm p-6">
                <h3 className="mb-4 flex items-center gap-2">
                  <Briefcase className="w-5 h-5" />
                  Company Information
                </h3>
                <div className="space-y-3">
                  {selectedCompany.sicCode && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">SIC Code</p>
                      <p className="font-mono">{selectedCompany.sicCode}</p>
                      {selectedCompany.sicDescription && (
                        <p className="text-sm text-gray-600">{selectedCompany.sicDescription}</p>
                      )}
                    </div>
                  )}
                  {selectedCompany.stateOfIncorporation && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">State of Incorporation</p>
                      <p>{selectedCompany.stateOfIncorporation}</p>
                    </div>
                  )}
                  {selectedCompany.fiscalYearEnd && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">Fiscal Year End</p>
                      <p className="font-mono">{selectedCompany.fiscalYearEnd}</p>
                    </div>
                  )}
                  {selectedCompany.exchange && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">Exchange</p>
                      <p>{selectedCompany.exchange}</p>
                    </div>
                  )}
                </div>
              </div>

              <div className="bg-white rounded-lg shadow-sm p-6">
                <h3 className="mb-4 flex items-center gap-2">
                  <MapPin className="w-5 h-5" />
                  Addresses
                </h3>
                <div className="space-y-3">
                  {selectedCompany.businessAddress && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">Business Address</p>
                      <p className="text-sm">{selectedCompany.businessAddress}</p>
                    </div>
                  )}
                  {selectedCompany.mailingAddress && (
                    <div>
                      <p className="text-sm text-gray-600 mb-1">Mailing Address</p>
                      <p className="text-sm">{selectedCompany.mailingAddress}</p>
                    </div>
                  )}
                  {!selectedCompany.businessAddress && !selectedCompany.mailingAddress && (
                    <p className="text-sm text-gray-500">No address information available</p>
                  )}
                </div>
              </div>
            </div>

            {/* Filing History */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="flex items-center gap-2">
                  <Calendar className="w-5 h-5" />
                  Recent Filings
                </h3>
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}`)}
                  className="text-sm text-blue-600 hover:underline"
                >
                  View All
                </button>
              </div>

              {filingsLoading ? (
                <div className="py-8">
                  <LoadingSpinner size="md" text="Loading filings..." />
                </div>
              ) : filingsError ? (
                <ErrorMessage
                  message={filingsError}
                  onRetry={() => fetchByCompany(selectedCompany.cik, 0, 10)}
                />
              ) : companyFilings.length > 0 ? (
                <div className="space-y-3">
                  {companyFilings.slice(0, 5).map(filing => (
                    <div key={filing.id} className="flex items-start justify-between py-3 border-b border-gray-100 last:border-0">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <FormTypeBadge formType={filing.formType} />
                          <span className="font-mono text-sm text-gray-600">{filing.filingDate}</span>
                        </div>
                        {filing.documentDescription && (
                          <p className="text-sm text-gray-700">{filing.documentDescription}</p>
                        )}
                        <p className="text-xs text-gray-500 font-mono mt-1">{filing.accessionNumber}</p>
                      </div>
                      <button
                        onClick={() => navigate(`/filing/${filing.id}`)}
                        className="ml-4 text-sm text-blue-600 hover:underline"
                      >
                        View
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-gray-500 text-center py-4">No filings found</p>
              )}
            </div>

            {/* Quick Actions */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h3 className="mb-4">Quick Actions</h3>
              <div className="flex flex-wrap gap-3">
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}&formType=10-K`)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
                >
                  View 10-K Reports
                </button>
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}&formType=10-Q`)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
                >
                  View 10-Q Reports
                </button>
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}&formType=4`)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
                >
                  View Form 4s
                </button>
                <button
                  onClick={() => navigate(`/search?cik=${selectedCompany.cik}&formType=8-K`)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
                >
                  View 8-K Reports
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
