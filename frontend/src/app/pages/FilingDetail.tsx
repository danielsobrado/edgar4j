import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { X, Download, ExternalLink, Copy, CheckCircle, ArrowLeft } from 'lucide-react';
import { FormTypeBadge } from '../components/FormTypeBadge';
import { useFiling } from '../hooks/useFilings';
import { LoadingPage } from '../components/common/LoadingSpinner';
import { ErrorPage } from '../components/common/ErrorMessage';
import { XbrlAnalysisPanel } from '../components/xbrl';
import { Form4PriceChart } from '../components/Form4PriceChart';
import { form4Api, Form4 } from '../api';

function formatCurrency(value?: number | null) {
  if (value == null) return '-';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value);
}

function formatNumber(value?: number | null) {
  if (value == null) return '-';
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 2,
  }).format(value);
}

function parseFilingItems(items?: string | string[]) {
  if (Array.isArray(items)) {
    return items
      .map((item) => item?.trim())
      .filter((item): item is string => Boolean(item));
  }

  if (!items) {
    return [];
  }

  return items
    .split(/[,;]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function FilingDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [copied, setCopied] = React.useState(false);
  const [parsedForm4, setParsedForm4] = React.useState<Form4 | null>(null);
  const [parsedForm4Loading, setParsedForm4Loading] = React.useState(false);
  const [parsedForm4Error, setParsedForm4Error] = React.useState<string | null>(null);

  const { filing, loading, error } = useFiling(id);

  React.useEffect(() => {
    if (!filing || filing.formType !== '4' || !filing.accessionNumber) {
      setParsedForm4(null);
      setParsedForm4Error(null);
      setParsedForm4Loading(false);
      return;
    }

    let cancelled = false;

    const loadParsedForm4 = async () => {
      setParsedForm4Loading(true);
      setParsedForm4Error(null);

      try {
        const existing = await form4Api.getByAccessionNumber(filing.accessionNumber);
        if (!cancelled) {
          setParsedForm4(existing);
          setParsedForm4Loading(false);
        }
        return;
      } catch {
        if (!filing.primaryDocument) {
          if (!cancelled) {
            setParsedForm4Error('Parsed Form 4 data is not available for this filing yet.');
            setParsedForm4Loading(false);
          }
          return;
        }
      }

      try {
        const parsed = await form4Api.downloadAndParse(
          filing.cik,
          filing.accessionNumber,
          filing.primaryDocument
        );
        if (!cancelled) {
          setParsedForm4(parsed);
        }
      } catch (err) {
        if (!cancelled) {
          setParsedForm4Error(err instanceof Error ? err.message : 'Failed to load parsed Form 4 data');
        }
      } finally {
        if (!cancelled) {
          setParsedForm4Loading(false);
        }
      }
    };

    void loadParsedForm4();

    return () => {
      cancelled = true;
    };
  }, [filing]);

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

  const filingItems = parseFilingItems(filing.items);

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
            {filingItems.length > 0 && filingItems.map((item, index) => (
              <span key={index} className="px-3 py-1 rounded-full text-sm bg-blue-100 text-blue-800">
                Item {item}
              </span>
            ))}
          </div>
        </div>

        {/* XBRL Analysis Panel */}
        {(filing.isXBRL || filing.isInlineXBRL) && (
          <div className="p-6 border-b border-gray-200">
            <XbrlAnalysisPanel
              filingUrl={secDocumentUrl}
              isXbrl={filing.isXBRL}
              isInlineXbrl={filing.isInlineXBRL}
            />
          </div>
        )}

        {/* Parsed Form 4 Data */}
        {filing.formType === '4' && (
          <div className="p-6 border-b border-gray-200">
            <div className="flex items-center justify-between mb-4 gap-4">
              <div>
                <h3>Parsed Form 4 Data</h3>
                <p className="text-sm text-gray-600 mt-1">
                  Structured insider transaction data extracted from the filing document.
                </p>
              </div>
            </div>

            {parsedForm4Loading && (
              <p className="text-sm text-gray-600">Loading parsed Form 4 data...</p>
            )}

            {!parsedForm4Loading && parsedForm4Error && (
              <p className="text-sm text-red-600">{parsedForm4Error}</p>
            )}

            {!parsedForm4Loading && parsedForm4 && (
              <div className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="rounded-lg bg-gray-50 p-4">
                    <p className="text-sm text-gray-600 mb-1">Issuer</p>
                    <p>{parsedForm4.issuerName || filing.companyName}</p>
                    <p className="text-sm text-gray-500 mt-1">{parsedForm4.tradingSymbol || '-'}</p>
                  </div>
                  <div className="rounded-lg bg-gray-50 p-4">
                    <p className="text-sm text-gray-600 mb-1">Reporting Owner</p>
                    <p>{parsedForm4.rptOwnerName || '-'}</p>
                    <p className="text-sm text-gray-500 mt-1">
                      {parsedForm4.officerTitle
                        || (parsedForm4.isDirector ? 'Director' : '')
                        || (parsedForm4.isOfficer ? 'Officer' : '')
                        || (parsedForm4.isTenPercentOwner ? '10% Owner' : '')
                        || parsedForm4.ownerType
                        || 'Unknown'}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  <div className="rounded-lg border border-gray-200 p-4">
                    <p className="text-sm text-gray-600 mb-1">Transactions</p>
                    <p>{parsedForm4.transactions?.length || 0}</p>
                  </div>
                  <div className="rounded-lg border border-gray-200 p-4">
                    <p className="text-sm text-gray-600 mb-1">Primary Transaction Date</p>
                    <p className="font-mono text-sm">{parsedForm4.transactionDate || '-'}</p>
                  </div>
                  <div className="rounded-lg border border-gray-200 p-4">
                    <p className="text-sm text-gray-600 mb-1">Primary Transaction Value</p>
                    <p>{formatCurrency(parsedForm4.transactionValue)}</p>
                  </div>
                  <div className="rounded-lg border border-gray-200 p-4">
                    <p className="text-sm text-gray-600 mb-1">Direction</p>
                    <p>{parsedForm4.acquiredDisposedCode === 'A' ? 'Buy / Acquire' : parsedForm4.acquiredDisposedCode === 'D' ? 'Sell / Dispose' : '-'}</p>
                  </div>
                </div>

                {parsedForm4.transactions && parsedForm4.transactions.length > 0 && (
                  <div>
                    <h4 className="mb-3">Parsed Transactions</h4>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50 border-b border-gray-200">
                          <tr>
                            <th className="text-left px-3 py-2">Type</th>
                            <th className="text-left px-3 py-2">Date</th>
                            <th className="text-left px-3 py-2">Security</th>
                            <th className="text-right px-3 py-2">Shares</th>
                            <th className="text-right px-3 py-2">Price</th>
                            <th className="text-right px-3 py-2">Value</th>
                            <th className="text-right px-3 py-2">Owned After</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                          {parsedForm4.transactions.map((transaction, index) => (
                            <tr key={`${transaction.accessionNumber || filing.accessionNumber}-${index}`}>
                              <td className="px-3 py-2">
                                {transaction.acquiredDisposedCode === 'A' ? 'Buy' : transaction.acquiredDisposedCode === 'D' ? 'Sell' : transaction.transactionType}
                              </td>
                              <td className="px-3 py-2 font-mono text-xs">{transaction.transactionDate || '-'}</td>
                              <td className="px-3 py-2">{transaction.securityTitle || '-'}</td>
                              <td className="px-3 py-2 text-right">{formatNumber(transaction.transactionShares)}</td>
                              <td className="px-3 py-2 text-right">{formatCurrency(transaction.transactionPricePerShare)}</td>
                              <td className="px-3 py-2 text-right">{formatCurrency(transaction.transactionValue)}</td>
                              <td className="px-3 py-2 text-right">{formatNumber(transaction.sharesOwnedFollowingTransaction)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {(parsedForm4.tradingSymbol || filing.ticker) && (
                  <Form4PriceChart
                    ticker={parsedForm4.tradingSymbol || filing.ticker || ''}
                    anchorDate={parsedForm4.transactionDate || filing.filingDate}
                    transactions={parsedForm4.transactions || []}
                  />
                )}
              </div>
            )}
          </div>
        )}

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
