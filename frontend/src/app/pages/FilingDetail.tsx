import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { X, Download, ExternalLink, Copy, CheckCircle, ArrowLeft } from 'lucide-react';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useFiling } from '../hooks/useFilings';
import { LoadingPage } from '../components/common/LoadingSpinner';
import { ErrorPage } from '../components/common/ErrorMessage';

export function FilingDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [copied, setCopied] = React.useState(false);

  const { filing, loading, error } = useFiling(id);

  if (loading) {
    return <LoadingPage text="Loading filing details..." />;
  }

  if (error) {
    return (
      <ErrorPage
        title="Failed to load filing"
        message={error}
        onRetry={() => window.location.reload()}
      />
    );
  }

  if (!filing) {
    return (
      <div className="bg-white rounded-lg shadow-sm p-6">
        <p>Filing not found</p>
        <button
          onClick={() => navigate('/search')}
          className="mt-4 text-blue-600 hover:underline flex items-center gap-2"
        >
          <ArrowLeft className="w-4 h-4" />
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

  const secFilingUrl = `https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${filing.cik}&type=${filing.formType}&dateb=&owner=include&count=40`;
  const secDocumentUrl = filing.filingUrl || `https://www.sec.gov/Archives/edgar/data/${filing.cik.replace(/^0+/, '')}/${filing.accessionNumber.replace(/-/g, '')}/${filing.primaryDocument || 'index.html'}`;

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-sm">
        {/* Header */}
        <div className="border-b border-gray-200 p-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <h1>{filing.companyName}</h1>
                {filing.ticker && (
                  <span className="text-gray-600">({filing.ticker})</span>
                )}
                <FormTypeBadge formType={filing.formType} />
              </div>
              {filing.documentDescription && (
                <p className="text-gray-600">{filing.documentDescription}</p>
              )}
            </div>
            <button
              onClick={() => navigate(-1)}
              className="p-2 hover:bg-gray-100 rounded-full"
              title="Close"
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
            {filing.primaryDocument && (
              <div>
                <p className="text-sm text-gray-600 mb-1">Primary Document</p>
                <p className="font-mono text-sm">{filing.primaryDocument}</p>
              </div>
            )}
            {filing.wordHits !== undefined && filing.wordHits > 0 && (
              <div>
                <p className="text-sm text-gray-600 mb-1">Keyword Matches</p>
                <p>{filing.wordHits} hits</p>
              </div>
            )}
            {filing.fileSize && (
              <div>
                <p className="text-sm text-gray-600 mb-1">File Size</p>
                <p>{filing.fileSize}</p>
              </div>
            )}
          </div>
        </div>

        {/* Tags */}
        <div className="p-6 border-b border-gray-200">
          <h3 className="mb-3">Document Tags</h3>
          <div className="flex gap-2 flex-wrap">
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
            {filing.items && filing.items.length > 0 && filing.items.map((item, index) => (
              <span key={index} className="px-3 py-1 rounded-full text-sm bg-blue-100 text-blue-800">
                Item {item}
              </span>
            ))}
          </div>
        </div>

        {/* Content Preview */}
        {filing.contentPreview && (
          <div className="p-6 border-b border-gray-200">
            <h3 className="mb-3">Content Preview</h3>
            <div className="bg-gray-50 rounded-lg p-4 font-mono text-sm overflow-x-auto max-h-96 overflow-y-auto">
              <p className="text-gray-700 whitespace-pre-wrap">{filing.contentPreview}</p>
            </div>
          </div>
        )}

        {/* Related Documents */}
        {filing.documents && filing.documents.length > 0 && (
          <div className="p-6 border-b border-gray-200">
            <h3 className="mb-3">Related Documents ({filing.documents.length})</h3>
            <div className="space-y-2 max-h-60 overflow-y-auto">
              {filing.documents.map((doc, index) => (
                <div key={index} className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded">
                  <div className="flex-1">
                    <p className="font-mono text-sm">{doc.filename}</p>
                    <p className="text-xs text-gray-500">{doc.description || doc.type}</p>
                  </div>
                  {doc.url && (
                    <a
                      href={doc.url}
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

        {/* Actions */}
        <div className="p-6">
          <h3 className="mb-3">Actions</h3>
          <div className="flex flex-wrap gap-3">
            <a
              href={secDocumentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              Download Original
            </a>
            <a
              href={secFilingUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50 flex items-center gap-2"
            >
              <ExternalLink className="w-4 h-4" />
              Open on SEC.gov
            </a>
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
