import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { X, Download, ExternalLink, Copy, CheckCircle } from 'lucide-react';
import { filings } from '../data/mockData';
import { FormTypeBadge } from '../components/FormTypeBadge';

export function FilingDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [copied, setCopied] = React.useState(false);
  
  const filing = filings.find(f => f.id === id);
  
  if (!filing) {
    return (
      <div className="bg-white rounded-lg shadow-sm p-6">
        <p>Filing not found</p>
        <button
          onClick={() => navigate('/search')}
          className="mt-4 text-blue-600 hover:underline"
        >
          Back to Search
        </button>
      </div>
    );
  }
  
  const copyAccessionNumber = () => {
    navigator.clipboard.writeText(filing.accessionNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  
  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-sm">
        {/* Header */}
        <div className="border-b border-gray-200 p-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <h1>{filing.companyName}</h1>
                <span className="text-gray-600">({filing.ticker})</span>
                <FormTypeBadge formType={filing.formType} />
              </div>
              <p className="text-gray-600">{filing.documentDescription}</p>
            </div>
            <button
              onClick={() => navigate(-1)}
              className="p-2 hover:bg-gray-100 rounded-full"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>
        
        {/* Metadata */}
        <div className="p-6 border-b border-gray-200">
          <h3 className="mb-4">Filing Information</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <p className="text-sm text-gray-600 mb-1">CIK</p>
              <p className="font-mono">{filing.cik}</p>
            </div>
            <div>
              <p className="text-sm text-gray-600 mb-1">Accession Number</p>
              <div className="flex items-center gap-2">
                <p className="font-mono text-sm">{filing.accessionNumber}</p>
                <button
                  onClick={copyAccessionNumber}
                  className="p-1 hover:bg-gray-100 rounded"
                  title="Copy accession number"
                >
                  {copied ? (
                    <CheckCircle className="w-4 h-4 text-green-500" />
                  ) : (
                    <Copy className="w-4 h-4 text-gray-500" />
                  )}
                </button>
              </div>
            </div>
            <div>
              <p className="text-sm text-gray-600 mb-1">Filing Date</p>
              <p className="font-mono">{filing.filingDate}</p>
            </div>
            {filing.reportDate && (
              <div>
                <p className="text-sm text-gray-600 mb-1">Report Date</p>
                <p className="font-mono">{filing.reportDate}</p>
              </div>
            )}
            <div>
              <p className="text-sm text-gray-600 mb-1">Primary Document</p>
              <p className="font-mono text-sm">{filing.primaryDocument}</p>
            </div>
            <div>
              <p className="text-sm text-gray-600 mb-1">Keyword Matches</p>
              <p>{filing.wordHits} hits</p>
            </div>
          </div>
        </div>
        
        {/* Tags */}
        <div className="p-6 border-b border-gray-200">
          <h3 className="mb-3">Document Tags</h3>
          <div className="flex gap-2">
            <span className={`px-3 py-1 rounded-full text-sm ${
              filing.isXBRL ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
            }`}>
              {filing.isXBRL ? '✓' : '✗'} XBRL
            </span>
            <span className={`px-3 py-1 rounded-full text-sm ${
              filing.isInlineXBRL ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
            }`}>
              {filing.isInlineXBRL ? '✓' : '✗'} Inline XBRL
            </span>
          </div>
        </div>
        
        {/* Content Preview */}
        <div className="p-6 border-b border-gray-200">
          <h3 className="mb-3">Content Preview</h3>
          <div className="bg-gray-50 rounded-lg p-4 font-mono text-sm overflow-x-auto">
            <p className="text-gray-700 whitespace-pre-wrap">
              {`UNITED STATES
SECURITIES AND EXCHANGE COMMISSION
Washington, D.C. 20549

FORM ${filing.formType}

${filing.documentDescription}

For the fiscal year ended ${filing.reportDate || filing.filingDate}

Commission file number: 001-00000

${filing.companyName}
(Exact name of registrant as specified in its charter)

${filing.cik}
(CIK Number)

[Content preview with highlighted keywords would appear here...]

This is a sample preview of the filing content. In a production system, 
this would display the actual filing text with keyword highlights.`}
            </p>
          </div>
        </div>
        
        {/* Actions */}
        <div className="p-6">
          <h3 className="mb-3">Actions</h3>
          <div className="flex flex-wrap gap-3">
            <button className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] flex items-center gap-2">
              <Download className="w-4 h-4" />
              Download Original
            </button>
            <button className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2">
              <ExternalLink className="w-4 h-4" />
              Open on SEC.gov
            </button>
            <button
              onClick={copyAccessionNumber}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
            >
              {copied ? (
                <>
                  <CheckCircle className="w-4 h-4" />
                  Copied!
                </>
              ) : (
                <>
                  <Copy className="w-4 h-4" />
                  Copy Accession Number
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
