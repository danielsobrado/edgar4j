import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, MapPin, Calendar, FileText, TrendingUp, Briefcase } from 'lucide-react';
import { companies, filings } from '../data/mockData';
import { FormTypeBadge } from '../components/FormTypeBadge';

export function Companies() {
  const navigate = useNavigate();
  const [selectedCompany, setSelectedCompany] = React.useState(companies[0]);
  
  const companyFilings = filings.filter(f => f.companyId === selectedCompany.id);
  
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Company List */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4 flex items-center gap-2">
          <Building2 className="w-5 h-5" />
          Companies
        </h2>
        <div className="space-y-2">
          {companies.map(company => (
            <button
              key={company.id}
              onClick={() => setSelectedCompany(company)}
              className={`w-full text-left p-3 rounded-lg transition-colors ${
                selectedCompany.id === company.id
                  ? 'bg-[#1a1f36] text-white'
                  : 'hover:bg-gray-100'
              }`}
            >
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className={selectedCompany.id === company.id ? 'text-white' : 'text-gray-900'}>
                      {company.ticker}
                    </span>
                  </div>
                  <p className={`text-sm ${selectedCompany.id === company.id ? 'text-gray-300' : 'text-gray-600'}`}>
                    {company.name}
                  </p>
                </div>
              </div>
            </button>
          ))}
        </div>
      </div>
      
      {/* Company Details */}
      <div className="lg:col-span-2 space-y-6">
        {/* Header Card */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <h1>{selectedCompany.name}</h1>
                <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full">{selectedCompany.ticker}</span>
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
              <div>
                <p className="text-sm text-gray-600 mb-1">SIC Code</p>
                <p className="font-mono">{selectedCompany.sicCode}</p>
                <p className="text-sm text-gray-600">{selectedCompany.sicDescription}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 mb-1">State of Incorporation</p>
                <p>{selectedCompany.stateOfIncorporation}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 mb-1">Fiscal Year End</p>
                <p className="font-mono">{selectedCompany.fiscalYearEnd}</p>
              </div>
            </div>
          </div>
          
          <div className="bg-white rounded-lg shadow-sm p-6">
            <h3 className="mb-4 flex items-center gap-2">
              <MapPin className="w-5 h-5" />
              Addresses
            </h3>
            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-600 mb-1">Business Address</p>
                <p className="text-sm">{selectedCompany.businessAddress}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 mb-1">Mailing Address</p>
                <p className="text-sm">{selectedCompany.mailingAddress}</p>
              </div>
            </div>
          </div>
        </div>
        
        {/* Filing History */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="flex items-center gap-2">
              <Calendar className="w-5 h-5" />
              Recent Filings ({companyFilings.length})
            </h3>
            <button
              onClick={() => navigate(`/search?cik=${selectedCompany.cik}`)}
              className="text-sm text-blue-600 hover:underline"
            >
              View All
            </button>
          </div>
          
          {companyFilings.length > 0 ? (
            <div className="space-y-3">
              {companyFilings.slice(0, 5).map(filing => (
                <div key={filing.id} className="flex items-start justify-between py-3 border-b border-gray-100 last:border-0">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <FormTypeBadge formType={filing.formType} />
                      <span className="font-mono text-sm text-gray-600">{filing.filingDate}</span>
                    </div>
                    <p className="text-sm text-gray-700">{filing.documentDescription}</p>
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
            <button className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50">
              Track Company
            </button>
            <button className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50">
              Export Data
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
