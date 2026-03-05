import React, { useCallback, useState } from 'react';
import { Search, RefreshCw, TrendingUp, TrendingDown } from 'lucide-react';
import { useForm4Search } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Form4 } from '../api/types';

// ─── helpers ────────────────────────────────────────────────────────────────

/** Format a date string as dd-mm-yyyy */
function toDisplayDate(raw: string | undefined): string {
  if (!raw) return '-';
  try {
    // raw may be "2026-02-24" (ISO date) or a full ISO timestamp
    const d = new Date(raw);
    if (isNaN(d.getTime())) return raw;
    const dd = String(d.getUTCDate()).padStart(2, '0');
    const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
    const yyyy = d.getUTCFullYear();
    return `${dd}-${mm}-${yyyy}`;
  } catch {
    return raw;
  }
}

/** Format a date string as dd-mm-yyyy HH:mm */
function toDisplayDateTime(raw: string | undefined): string {
  if (!raw) return '-';
  try {
    const d = new Date(raw);
    if (isNaN(d.getTime())) return raw;
    const date = toDisplayDate(raw);
    const hh = String(d.getUTCHours()).padStart(2, '0');
    const min = String(d.getUTCMinutes()).padStart(2, '0');
    return `${date} ${hh}:${min}`;
  } catch {
    return raw;
  }
}

function formatNumber(v: number | undefined): string {
  if (v == null) return '-';
  return v.toLocaleString();
}

function formatCurrency(v: number | undefined): string {
  if (v == null) return '-';
  return `$${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function isBuy(filing: Form4): boolean {
  const code = filing.transactions?.[0]?.acquiredDisposedCode ?? filing.acquiredDisposedCode;
  return code === 'A';
}

function getRelationship(filing: Form4): string {
  if (filing.officerTitle) return filing.officerTitle;
  if (filing.isDirector) return 'Director';
  if (filing.isOfficer) return 'Officer';
  if (filing.isTenPercentOwner) return '10% Owner';
  if (filing.isOther) return 'Other';
  return filing.ownerType ?? 'Unknown';
}

function firstTxValue<K extends keyof NonNullable<Form4['transactions']>[number]>(
  filing: Form4,
  key: K
): NonNullable<Form4['transactions']>[number][K] | undefined {
  return filing.transactions?.[0]?.[key];
}

// ─── BuySellBadge ────────────────────────────────────────────────────────────

function BuySellBadge({ buy }: { buy: boolean }) {
  return buy ? (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-green-100 text-green-800">
      <TrendingUp className="w-3 h-3" /> BUY
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-800">
      <TrendingDown className="w-3 h-3" /> SELL
    </span>
  );
}

// ─── TransactionRow ──────────────────────────────────────────────────────────

function TransactionRow({ f }: { f: Form4 }) {
  const tx = f.transactions?.[0];
  const buy = isBuy(f);

  const sharesTraded = tx?.transactionShares ?? f.transactionShares;
  const avgPrice     = tx?.transactionPricePerShare ?? f.transactionPricePerShare;
  const totalAmt     = tx?.transactionValue ?? f.transactionValue
                       ?? ((sharesTraded != null && avgPrice != null) ? sharesTraded * avgPrice : undefined);
  const sharesOwned  = tx?.sharesOwnedFollowingTransaction;

  return (
    <tr className="hover:bg-gray-50 transition-colors">
      {/* Transaction Date dd-mm-yyyy */}
      <td className="px-3 py-3 text-sm text-gray-900 whitespace-nowrap font-medium">
        {toDisplayDate(tx?.transactionDate ?? f.transactionDate)}
      </td>

      {/* Reported dd-mm-yyyy + Buy/Sell badge */}
      <td className="px-3 py-3 text-sm whitespace-nowrap">
        <div className="flex flex-col gap-1">
          <span className="text-gray-600">{toDisplayDate(f.periodOfReport ?? f.transactionDate)}</span>
          <BuySellBadge buy={buy} />
        </div>
      </td>

      {/* DateTime */}
      <td className="px-3 py-3 text-sm text-gray-500 whitespace-nowrap">
        {toDisplayDateTime(tx?.transactionDate ?? f.transactionDate)}
      </td>

      {/* Company */}
      <td className="px-3 py-3 text-sm text-gray-900 max-w-[180px] truncate">
        {f.issuerName ?? '-'}
      </td>

      {/* Symbol */}
      <td className="px-3 py-3 text-sm font-semibold text-blue-700">
        {f.tradingSymbol ?? '-'}
      </td>

      {/* Insider + Relationship */}
      <td className="px-3 py-3 text-sm text-gray-900 max-w-[180px]">
        <div className="font-medium truncate">{f.rptOwnerName ?? '-'}</div>
        <div className="text-xs text-gray-500">{getRelationship(f)}</div>
      </td>

      {/* Shares Traded */}
      <td className="px-3 py-3 text-sm text-right text-gray-900">
        <span className={buy ? 'text-green-700' : 'text-red-700'}>
          {sharesTraded != null ? (buy ? '+' : '-') + formatNumber(Math.abs(sharesTraded)) : '-'}
        </span>
      </td>

      {/* Average Price */}
      <td className="px-3 py-3 text-sm text-right text-gray-900">
        {avgPrice != null ? formatCurrency(avgPrice) : '-'}
      </td>

      {/* Total Amount */}
      <td className="px-3 py-3 text-sm text-right font-medium">
        <span className={buy ? 'text-green-700' : 'text-red-700'}>
          {totalAmt != null ? formatCurrency(totalAmt) : '-'}
        </span>
      </td>

      {/* Shares Owned */}
      <td className="px-3 py-3 text-sm text-right text-gray-700">
        {sharesOwned != null ? formatNumber(sharesOwned) : '-'}
      </td>
    </tr>
  );
}

// ─── Table ────────────────────────────────────────────────────────────────────

const COL_HEADERS = [
  { label: 'Transaction\nDate',       align: 'left'  },
  { label: 'Reported\n(dd-mm-yyyy)',  align: 'left'  },
  { label: 'DateTime',                align: 'left'  },
  { label: 'Company',                 align: 'left'  },
  { label: 'Symbol',                  align: 'left'  },
  { label: 'Insider\nRelationship',   align: 'left'  },
  { label: 'Shares\nTraded',          align: 'right' },
  { label: 'Average\nPrice',          align: 'right' },
  { label: 'Total\nAmount',           align: 'right' },
  { label: 'Shares\nOwned',           align: 'right' },
] as const;

function FilingsTable({ filings }: { filings: Form4[] }) {
  if (!filings.length) return null;

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50">
          <tr>
            {COL_HEADERS.map(({ label, align }) => (
              <th
                key={label}
                className={`px-3 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-pre-line ${
                  align === 'right' ? 'text-right' : 'text-left'
                }`}
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-100">
          {filings.map((f) => (
            <TransactionRow key={f.id ?? f.accessionNumber} f={f} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function Form4Page() {
  const [searchType, setSearchType]   = useState<'symbol' | 'cik' | 'date'>('symbol');
  const [searchTerm, setSearchTerm]   = useState('');
  const [startDate, setStartDate]     = useState('');
  const [endDate, setEndDate]         = useState('');
  const [showDateRange, setShowDateRange] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize]       = useState(20);

  const {
    filings,
    loading,
    error,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
    searchBySymbolAndDateRange,
  } = useForm4Search();

  const runSearch = useCallback((page: number) => {
    switch (searchType) {
      case 'symbol':
        if (!searchTerm.trim()) return;
        if (showDateRange && startDate && endDate) {
          searchBySymbolAndDateRange(searchTerm.toUpperCase(), startDate, endDate, page, pageSize);
        } else {
          searchBySymbol(searchTerm.toUpperCase(), page, pageSize);
        }
        break;
      case 'cik':
        if (!searchTerm.trim()) return;
        searchByCik(searchTerm, page, pageSize);
        break;
      case 'date':
        if (!startDate || !endDate) return;
        searchByDateRange(startDate, endDate, page, pageSize);
        break;
    }
  }, [searchType, searchTerm, startDate, endDate, showDateRange, pageSize,
      searchBySymbol, searchByCik, searchByDateRange, searchBySymbolAndDateRange]);

  const handleSearch = useCallback(() => {
    setCurrentPage(0);
    runSearch(0);
  }, [runSearch]);

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);
    runSearch(page);
  }, [runSearch]);

  const searchDisabled =
    loading ||
    (searchType !== 'date' && !searchTerm.trim()) ||
    (searchType === 'date' && (!startDate || !endDate));

  return (
    <div className="space-y-6">
      {/* Header card */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <TrendingUp className="w-8 h-8 text-red-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 4 – Insider Transactions</h1>
              <p className="text-gray-500 text-sm">Insider trading transactions and holdings reported to the SEC</p>
            </div>
          </div>
          <button
            onClick={handleSearch}
            disabled={searchDisabled}
            className="p-2 hover:bg-gray-100 rounded-full disabled:opacity-40"
            title="Refresh"
          >
            <RefreshCw className="w-5 h-5 text-gray-600" />
          </button>
        </div>

        {/* Search bar */}
        <div className="flex gap-3 flex-wrap items-center">
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as 'symbol' | 'cik' | 'date')}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="symbol">Ticker Symbol</option>
            <option value="cik">CIK</option>
            <option value="date">Date Range</option>
          </select>

          {searchType === 'date' ? (
            <>
              <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <span className="text-gray-400 text-sm">to</span>
              <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </>
          ) : (
            <>
              <input
                type="text"
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && !searchDisabled && handleSearch()}
                placeholder={searchType === 'symbol' ? 'e.g. PLTR, AAPL, TSLA' : 'e.g. 0001560327'}
                className="flex-1 min-w-[180px] px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />

              {searchType === 'symbol' && (
                <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer select-none">
                  <input type="checkbox" checked={showDateRange}
                    onChange={e => setShowDateRange(e.target.checked)}
                    className="rounded border-gray-300" />
                  Date range
                </label>
              )}

              {showDateRange && searchType === 'symbol' && (
                <>
                  <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
                    className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  <span className="text-gray-400 text-sm">to</span>
                  <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
                    className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </>
              )}
            </>
          )}

          <button
            onClick={handleSearch}
            disabled={searchDisabled}
            className="px-5 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2 disabled:opacity-50 text-sm"
          >
            <Search className="w-4 h-4" />
            Search
          </button>
        </div>
      </div>

      {/* Error */}
      {error && <ErrorMessage message={error} />}

      {/* Results card */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-lg">
            {(searchTerm || searchType === 'date')
              ? `Results — ${totalElements.toLocaleString()} transaction${totalElements !== 1 ? 's' : ''}`
              : 'Insider Transactions'}
          </h2>
          {filings.length > 0 && (
            <span className="text-sm text-gray-500">{filings.length} shown</span>
          )}
        </div>

        {loading && (
          <div className="py-12">
            <LoadingSpinner size="lg" text="Fetching insider transactions…" />
          </div>
        )}

        {!loading && filings.length === 0 && (
          <EmptyState
            type="search"
            message={
              searchTerm || searchType === 'date'
                ? 'No transactions match your search criteria.'
                : 'Enter a ticker symbol (e.g. PLTR, AAPL) and click Search to view insider transactions.'
            }
          />
        )}

        {!loading && filings.length > 0 && (
          <>
            <FilingsTable filings={filings} />

            {totalPages > 1 && (
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
                    runSearch(0);
                  }}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
