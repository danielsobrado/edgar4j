import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { format, parseISO, subDays } from 'date-fns';
import { Search, RefreshCw, TrendingUp } from 'lucide-react';
import { useForm4Search } from '../hooks';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';
import { Slider } from '../components/ui/slider';
import { Tooltip, TooltipContent, TooltipTrigger } from '../components/ui/tooltip';
import { form4Api, marketDataApi } from '../api';
import { Form4, Form4Transaction } from '../api/types';
import { Form4PriceChart } from '../components/Form4PriceChart';
import {
  buildMarketCloseLookup,
  toDisplayDate,
  toDisplayDateTime,
  formatNumber,
  formatCurrency,
  formatSignedShares,
  mergeMarketCloseLookups,
  resolveEstimatedClose,
  BuySellBadge,
  SignedAmount,
  TableWrapper,
  TableHead,
  Td,
  TdRight,
  type MarketCloseLookup,
  type ColDef,
} from '../utils';

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

function primaryTransaction(filing: Form4): Form4Transaction | undefined {
  return filing.transactions?.[0];
}

function isDerivativeFiling(filing: Form4): boolean {
  const tx = primaryTransaction(filing);

  return (
    tx?.transactionType === 'DERIVATIVE'
    || Boolean(
      tx?.underlyingSecurityTitle
      || tx?.underlyingSecurityShares
      || tx?.exercisePrice
      || tx?.expirationDate,
    )
  );
}

function derivativeSecurityTitle(filing: Form4): string | null {
  const tx = primaryTransaction(filing);
  return tx?.securityTitle || tx?.underlyingSecurityTitle || null;
}

function filingTicker(filing: Form4): string | null {
  const ticker = filing.tradingSymbol?.trim().toUpperCase();
  return ticker || null;
}

function filingTransactionDate(filing: Form4): string | null {
  return primaryTransaction(filing)?.transactionDate ?? filing.transactionDate ?? null;
}

function insiderNameForFiling(filing: Form4): string {
  return filing.rptOwnerName?.trim() || 'Unknown Insider';
}

function sharesTradedForFiling(filing: Form4): number {
  const shares = primaryTransaction(filing)?.transactionShares ?? filing.transactionShares ?? 0;
  return Math.abs(shares);
}

function sliderStep(maxShares: number): number {
  if (maxShares <= 1_000) return 1;
  if (maxShares <= 10_000) return 10;
  if (maxShares <= 100_000) return 100;
  if (maxShares <= 1_000_000) return 1_000;
  return 10_000;
}

function hasMeaningfulValue(value: number | null | undefined): value is number {
  return value != null && Math.abs(value) > 0;
}

function paddedMarketRequestStart(date: string): string {
  try {
    return format(subDays(parseISO(date), 7), 'yyyy-MM-dd');
  } catch {
    return date;
  }
}

type MarketPriceCacheEntry = {
  requestStart: string;
  requestEnd: string;
  lookup: MarketCloseLookup;
};

type TiingoValuation = {
  displayPrice: number | null;
  displayTotalAmount: number | null;
  usesTiingoPrice: boolean;
  sourceDate: string | null;
};

function cacheCoversRequest(
  entry: MarketPriceCacheEntry | undefined,
  requestStart: string,
  requestEnd: string,
): boolean {
  if (!entry) {
    return false;
  }

  return entry.requestStart <= requestStart && entry.requestEnd >= requestEnd;
}

function resolveTiingoValuation(
  filing: Form4,
  marketPriceCache: Record<string, MarketPriceCacheEntry>,
): TiingoValuation {
  const tx = primaryTransaction(filing);
  const sharesTraded = tx?.transactionShares ?? filing.transactionShares ?? null;
  const reportedPrice = tx?.transactionPricePerShare ?? filing.transactionPricePerShare ?? null;
  const reportedTotalAmount = tx?.transactionValue ?? filing.transactionValue
    ?? (sharesTraded != null && reportedPrice != null ? Math.abs(sharesTraded) * reportedPrice : null);

  if (hasMeaningfulValue(reportedPrice)) {
    return {
      displayPrice: reportedPrice,
      displayTotalAmount: reportedTotalAmount,
      usesTiingoPrice: false,
      sourceDate: null,
    };
  }

  const ticker = filingTicker(filing);
  const transactionDate = filingTransactionDate(filing);
  const estimatedClose = ticker && transactionDate
    ? resolveEstimatedClose(transactionDate, marketPriceCache[ticker]?.lookup ?? null)
    : null;

  if (estimatedClose) {
    return {
      displayPrice: estimatedClose.close,
      displayTotalAmount: hasMeaningfulValue(reportedTotalAmount)
        ? reportedTotalAmount
        : (sharesTraded != null ? Math.abs(sharesTraded) * estimatedClose.close : null),
      usesTiingoPrice: true,
      sourceDate: estimatedClose.sourceDate,
    };
  }

  return {
    displayPrice: reportedPrice,
    displayTotalAmount: reportedTotalAmount,
    usesTiingoPrice: false,
    sourceDate: null,
  };
}

const COLS: ColDef[] = [
  { label: 'Transaction\nDate', align: 'left' },
  { label: 'Reported\n(dd-mm-yyyy)', align: 'left' },
  { label: 'DateTime', align: 'left' },
  { label: 'Ticker', align: 'left' },
  { label: 'Insider\nRelationship', align: 'left' },
  { label: 'Shares\nTraded', align: 'right' },
  { label: 'Average\nPrice', align: 'right' },
  { label: 'Total\nAmount', align: 'right' },
  { label: 'Shares\nOwned', align: 'right' },
];

function TransactionRow({
  f,
  marketPriceCache,
}: {
  f: Form4;
  marketPriceCache: Record<string, MarketPriceCacheEntry>;
}) {
  const tx = primaryTransaction(f);
  const buy = isBuy(f);
  const derivative = isDerivativeFiling(f);
  const securityTitle = derivativeSecurityTitle(f);
  const transactionDate = tx?.transactionDate ?? f.transactionDate ?? null;

  const sharesTraded = tx?.transactionShares ?? f.transactionShares;
  const valuation = resolveTiingoValuation(f, marketPriceCache);
  const sharesOwned = tx?.sharesOwnedFollowingTransaction;
  const signed = formatSignedShares(sharesTraded, buy);
  const tiingoBadgeText = valuation.sourceDate && transactionDate && valuation.sourceDate !== transactionDate
    ? 'Tiingo prior close'
    : 'Tiingo price';

  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <Td className="whitespace-nowrap font-medium">
        {toDisplayDate(tx?.transactionDate ?? f.transactionDate)}
      </Td>

      <Td className="whitespace-nowrap">
        <div className="flex flex-col gap-1">
          <span className="text-gray-600">{toDisplayDate(f.periodOfReport ?? f.transactionDate)}</span>
          <BuySellBadge buy={buy} />
        </div>
      </Td>

      <Td className="whitespace-nowrap text-gray-500">
        {toDisplayDateTime(tx?.transactionDate ?? f.transactionDate)}
      </Td>

      <Td className="w-[180px]">
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              className="inline-flex cursor-help flex-col items-start text-left"
            >
              <span className="text-sm font-semibold text-blue-700">
                {f.tradingSymbol ?? '-'}
              </span>
              {derivative ? (
                <span className="mt-1 inline-flex rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700 ring-1 ring-amber-200">
                  Derivative
                </span>
              ) : null}
            </button>
          </TooltipTrigger>
          <TooltipContent side="top" sideOffset={6} className="max-w-xs">
            <div className="text-sm font-medium">{f.issuerName ?? 'Unknown issuer'}</div>
            {derivative && securityTitle ? (
              <div className="mt-1 text-xs opacity-90">{securityTitle}</div>
            ) : null}
          </TooltipContent>
        </Tooltip>
      </Td>

      <Td className="max-w-[180px]">
        <div className="font-medium truncate">{f.rptOwnerName ?? '-'}</div>
        <div className="text-xs text-gray-500">{getRelationship(f)}</div>
      </Td>

      <TdRight>
        <SignedAmount text={signed.text} positive={signed.positive} />
      </TdRight>

      <TdRight className="text-gray-700">
        <div className="inline-flex flex-col items-end gap-1">
          <span>{formatCurrency(valuation.displayPrice)}</span>
          {valuation.usesTiingoPrice ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex cursor-help rounded-full bg-sky-50 px-2 py-0.5 text-[11px] font-medium text-sky-700 ring-1 ring-sky-200">
                  {tiingoBadgeText}
                </span>
              </TooltipTrigger>
              <TooltipContent side="top" sideOffset={6} className="max-w-xs">
                <div className="text-sm font-medium">Estimated from Tiingo market close</div>
                <div className="mt-1 text-xs opacity-90">
                  {valuation.sourceDate && transactionDate && valuation.sourceDate !== transactionDate
                    ? `Used the prior trading close from ${toDisplayDate(valuation.sourceDate)} because ${toDisplayDate(transactionDate)} had no market close.`
                    : 'Used the same-day market close because the filing reported no priced transaction.'}
                </div>
              </TooltipContent>
            </Tooltip>
          ) : null}
        </div>
      </TdRight>

      <TdRight className="font-medium">
        <div className="inline-flex flex-col items-end gap-1">
          <SignedAmount text={formatCurrency(valuation.displayTotalAmount)} positive={buy} />
          {valuation.usesTiingoPrice ? (
            <span className="text-[11px] font-medium text-sky-700">
              shares x Tiingo
            </span>
          ) : null}
        </div>
      </TdRight>

      <TdRight className="text-gray-700">{formatNumber(sharesOwned)}</TdRight>
    </tr>
  );
}

function FilingsTable({
  filings,
  marketPriceCache,
}: {
  filings: Form4[];
  marketPriceCache: Record<string, MarketPriceCacheEntry>;
}) {
  if (!filings.length) return null;

  return (
    <TableWrapper>
      <TableHead cols={COLS} />
      <tbody className="bg-white divide-y divide-gray-100">
        {filings.map((f) => (
          <TransactionRow key={f.id ?? f.accessionNumber} f={f} marketPriceCache={marketPriceCache} />
        ))}
      </tbody>
    </TableWrapper>
  );
}

function flattenTransactions(filings: Form4[]): Form4Transaction[] {
  return filings.flatMap((filing) => {
    if (filing.transactions && filing.transactions.length > 0) {
      return filing.transactions;
    }

    if (!filing.transactionDate) {
      return [];
    }

    return [{
      accessionNumber: filing.accessionNumber,
      transactionType: 'NON_DERIVATIVE',
      securityTitle: filing.securityTitle || 'Security',
      transactionDate: filing.transactionDate,
      transactionShares: filing.transactionShares,
      transactionPricePerShare: filing.transactionPricePerShare,
      transactionValue: filing.transactionValue,
      acquiredDisposedCode: filing.acquiredDisposedCode,
    }];
  });
}

function uniqueTickers(filings: Form4[]): string[] {
  return Array.from(new Set(
    filings
      .map((filing) => filing.tradingSymbol?.trim().toUpperCase())
      .filter((value): value is string => Boolean(value)),
  ));
}

function transactionDateRange(transactions: Form4Transaction[]) {
  const dates = transactions
    .map((transaction) => transaction.transactionDate)
    .filter((value): value is string => Boolean(value))
    .sort();

  if (dates.length === 0) {
    return { startDate: undefined, endDate: undefined };
  }

  return {
    startDate: dates[0],
    endDate: dates[dates.length - 1],
  };
}

type Form4SearchType = 'symbol' | 'cik' | 'date';

type Form4SearchCriteria = {
  searchType: Form4SearchType;
  searchTerm: string;
  startDate: string;
  endDate: string;
  showDateRange: boolean;
};

function createEmptyPage(size: number) {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size,
    first: true,
    last: true,
    empty: true,
  };
}

export function Form4Page() {
  const [searchType, setSearchType] = useState<Form4SearchType>('symbol');
  const [searchTerm, setSearchTerm] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [showDateRange, setShowDateRange] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [activeSearch, setActiveSearch] = useState<Form4SearchCriteria | null>(null);
  const [chartFilings, setChartFilings] = useState<Form4[]>([]);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [selectedInsiderName, setSelectedInsiderName] = useState('ALL');
  const [minSharesTraded, setMinSharesTraded] = useState(0);
  const [marketPriceCache, setMarketPriceCache] = useState<Record<string, MarketPriceCacheEntry>>({});
  const [marketPriceUnavailableTickers, setMarketPriceUnavailableTickers] = useState<Record<string, true>>({});
  const pendingMarketPriceRequestsRef = React.useRef(new Set<string>());

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

  const buildSearchCriteria = useCallback((): Form4SearchCriteria => ({
    searchType,
    searchTerm: searchTerm.trim(),
    startDate,
    endDate,
    showDateRange,
  }), [endDate, searchTerm, searchType, showDateRange, startDate]);

  const fetchChartPage = useCallback((criteria: Form4SearchCriteria, page: number, size: number) => {
    switch (criteria.searchType) {
      case 'symbol':
        if (!criteria.searchTerm) {
          return Promise.resolve(createEmptyPage(size));
        }
        if (criteria.showDateRange && criteria.startDate && criteria.endDate) {
          return form4Api.getBySymbolAndDateRange(criteria.searchTerm.toUpperCase(), criteria.startDate, criteria.endDate, page, size);
        }
        return form4Api.getBySymbol(criteria.searchTerm.toUpperCase(), page, size);
      case 'cik':
        if (!criteria.searchTerm) {
          return Promise.resolve(createEmptyPage(size));
        }
        return form4Api.getByCik(criteria.searchTerm, page, size);
      case 'date':
        if (!criteria.startDate || !criteria.endDate) {
          return Promise.resolve(createEmptyPage(size));
        }
        return form4Api.getByDateRange(criteria.startDate, criteria.endDate, page, size);
    }
  }, []);

  useEffect(() => {
    if (loading || !activeSearch || totalElements === 0) {
      setChartFilings([]);
      setChartError(null);
      setChartLoading(false);
      return;
    }

    let cancelled = false;

    const loadChartFilings = async () => {
      setChartLoading(true);
      setChartError(null);
      try {
        const chartPageSize = 200;
        const pageCount = Math.max(1, Math.ceil(totalElements / chartPageSize));
        const pages = await Promise.all(
          Array.from({ length: pageCount }, (_, page) => fetchChartPage(activeSearch, page, chartPageSize)),
        );
        const allFilings = pages.flatMap((page) => page.content ?? []);
        if (!cancelled) {
          setChartFilings(allFilings);
        }
      } catch (err) {
        if (!cancelled) {
          setChartError(err instanceof Error ? err.message : 'Failed to load chart data');
          setChartFilings([]);
        }
      } finally {
        if (!cancelled) {
          setChartLoading(false);
        }
      }
    };

    void loadChartFilings();
    return () => {
      cancelled = true;
    };
  }, [activeSearch, fetchChartPage, loading, totalElements]);

  const runSearch = useCallback((page: number, criteria = activeSearch, sizeOverride = pageSize) => {
    if (!criteria) {
      return;
    }

    switch (criteria.searchType) {
      case 'symbol':
        if (!criteria.searchTerm) return;
        if (criteria.showDateRange && criteria.startDate && criteria.endDate) {
          searchBySymbolAndDateRange(criteria.searchTerm.toUpperCase(), criteria.startDate, criteria.endDate, page, sizeOverride);
        } else {
          searchBySymbol(criteria.searchTerm.toUpperCase(), page, sizeOverride);
        }
        break;
      case 'cik':
        if (!criteria.searchTerm) return;
        searchByCik(criteria.searchTerm, page, sizeOverride);
        break;
      case 'date':
        if (!criteria.startDate || !criteria.endDate) return;
        searchByDateRange(criteria.startDate, criteria.endDate, page, sizeOverride);
        break;
    }
  }, [activeSearch, pageSize, searchByCik, searchByDateRange, searchBySymbol, searchBySymbolAndDateRange]);

  const handleSearch = useCallback(() => {
    const criteria = buildSearchCriteria();
    if ((criteria.searchType === 'date' && (!criteria.startDate || !criteria.endDate))
      || (criteria.searchType !== 'date' && !criteria.searchTerm)) {
      return;
    }

    setActiveSearch(criteria);
    setSelectedInsiderName('ALL');
    setMinSharesTraded(0);
    setCurrentPage(0);
    runSearch(0, criteria);
  }, [buildSearchCriteria, runSearch]);

  const filtersReady = !chartLoading && chartFilings.length > 0;

  const insiderOptions = useMemo(() => {
    if (!filtersReady) {
      return [];
    }

    return Array.from(new Set(chartFilings.map(insiderNameForFiling)))
      .sort((left, right) => left.localeCompare(right));
  }, [chartFilings, filtersReady]);

  const maxSharesTraded = useMemo(() => {
    if (!filtersReady) {
      return 0;
    }

    return chartFilings.reduce(
      (maxValue, filing) => Math.max(maxValue, sharesTradedForFiling(filing)),
      0,
    );
  }, [chartFilings, filtersReady]);

  useEffect(() => {
    if (selectedInsiderName === 'ALL') {
      return;
    }
    if (!insiderOptions.includes(selectedInsiderName)) {
      setSelectedInsiderName('ALL');
    }
  }, [insiderOptions, selectedInsiderName]);

  useEffect(() => {
    if (minSharesTraded > maxSharesTraded) {
      setMinSharesTraded(maxSharesTraded);
    }
  }, [maxSharesTraded, minSharesTraded]);

  useEffect(() => {
    if (!filtersReady) {
      return;
    }
    setCurrentPage(0);
  }, [filtersReady, minSharesTraded, selectedInsiderName]);

  const filteredChartFilings = useMemo(() => {
    if (!filtersReady) {
      return chartFilings;
    }

    return chartFilings.filter((filing) => {
      if (selectedInsiderName !== 'ALL' && insiderNameForFiling(filing) !== selectedInsiderName) {
        return false;
      }
      return sharesTradedForFiling(filing) >= minSharesTraded;
    });
  }, [chartFilings, filtersReady, minSharesTraded, selectedInsiderName]);

  const chartTransactions = useMemo(
    () => flattenTransactions(filteredChartFilings),
    [filteredChartFilings],
  );
  const filteredTickerList = useMemo(
    () => uniqueTickers(filteredChartFilings),
    [filteredChartFilings],
  );
  const chartTicker = filteredTickerList.length === 1
    ? filteredTickerList[0]
    : (activeSearch?.searchType === 'symbol' && activeSearch.searchTerm
      ? activeSearch.searchTerm.toUpperCase()
      : null);
  const chartRange = useMemo(
    () => transactionDateRange(chartTransactions),
    [chartTransactions],
  );

  const explicitRangeStart = activeSearch && (activeSearch.searchType === 'date' || activeSearch.showDateRange)
    ? activeSearch.startDate
    : undefined;
  const explicitRangeEnd = activeSearch && (activeSearch.searchType === 'date' || activeSearch.showDateRange)
    ? activeSearch.endDate
    : undefined;

  const resultsTotalElements = filtersReady ? filteredChartFilings.length : totalElements;
  const resultsTotalPages = filtersReady
    ? (filteredChartFilings.length === 0 ? 0 : Math.ceil(filteredChartFilings.length / pageSize))
    : totalPages;
  const resultsFilings = useMemo(() => {
    if (!filtersReady) {
      return filings;
    }

    const startIndex = currentPage * pageSize;
    return filteredChartFilings.slice(startIndex, startIndex + pageSize);
  }, [currentPage, filings, filteredChartFilings, filtersReady, pageSize]);

  const tiingoPriceRequests = useMemo(() => {
    const requests = new Map<string, { ticker: string; requestStart: string; requestEnd: string }>();

    resultsFilings.forEach((filing) => {
      const tx = primaryTransaction(filing);
      const reportedPrice = tx?.transactionPricePerShare ?? filing.transactionPricePerShare;
      if (hasMeaningfulValue(reportedPrice)) {
        return;
      }

      const ticker = filingTicker(filing);
      const transactionDate = filingTransactionDate(filing);
      if (!ticker || !transactionDate) {
        return;
      }

      const requestStart = paddedMarketRequestStart(transactionDate);
      const existing = requests.get(ticker);
      if (!existing) {
        requests.set(ticker, {
          ticker,
          requestStart,
          requestEnd: transactionDate,
        });
        return;
      }

      requests.set(ticker, {
        ticker,
        requestStart: existing.requestStart < requestStart ? existing.requestStart : requestStart,
        requestEnd: existing.requestEnd > transactionDate ? existing.requestEnd : transactionDate,
      });
    });

    return Array.from(requests.values());
  }, [resultsFilings]);

  useEffect(() => {
    if (tiingoPriceRequests.length === 0) {
      return;
    }

    let cancelled = false;

    tiingoPriceRequests.forEach(({ ticker, requestStart, requestEnd }) => {
      if (marketPriceUnavailableTickers[ticker]) {
        return;
      }
      if (cacheCoversRequest(marketPriceCache[ticker], requestStart, requestEnd)) {
        return;
      }

      const pendingKey = `${ticker}:${requestStart}:${requestEnd}`;
      if (pendingMarketPriceRequestsRef.current.has(pendingKey)) {
        return;
      }

      pendingMarketPriceRequestsRef.current.add(pendingKey);
      void marketDataApi.getPriceHistory(ticker, requestStart, requestEnd)
        .then((history) => {
          if (cancelled) {
            return;
          }

          const incomingLookup = buildMarketCloseLookup(history);
          if (!incomingLookup) {
            setMarketPriceUnavailableTickers((current) => (
              current[ticker] ? current : { ...current, [ticker]: true }
            ));
            return;
          }

          setMarketPriceCache((current) => {
            const existing = current[ticker];
            const mergedLookup = mergeMarketCloseLookups(existing?.lookup ?? null, incomingLookup);
            if (!mergedLookup) {
              return current;
            }

            return {
              ...current,
              [ticker]: {
                requestStart: existing
                  ? (existing.requestStart < requestStart ? existing.requestStart : requestStart)
                  : requestStart,
                requestEnd: existing
                  ? (existing.requestEnd > requestEnd ? existing.requestEnd : requestEnd)
                  : requestEnd,
                lookup: mergedLookup,
              },
            };
          });
        })
        .catch(() => {
          if (cancelled) {
            return;
          }

          setMarketPriceUnavailableTickers((current) => (
            current[ticker] ? current : { ...current, [ticker]: true }
          ));
        })
        .finally(() => {
          pendingMarketPriceRequestsRef.current.delete(pendingKey);
        });
    });

    return () => {
      cancelled = true;
    };
  }, [marketPriceCache, marketPriceUnavailableTickers, tiingoPriceRequests]);

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);
    if (!filtersReady) {
      runSearch(page);
    }
  }, [filtersReady, runSearch]);

  const handlePageSizeChange = useCallback((size: number) => {
    setPageSize(size);
    setCurrentPage(0);
    if (!filtersReady) {
      runSearch(0, activeSearch, size);
    }
  }, [activeSearch, filtersReady, runSearch]);

  const resetResultFilters = useCallback(() => {
    setSelectedInsiderName('ALL');
    setMinSharesTraded(0);
  }, []);

  const sliderMax = Math.max(maxSharesTraded, 1);
  const sliderIncrement = sliderStep(sliderMax);
  const hasActiveResultFilters = selectedInsiderName !== 'ALL' || minSharesTraded > 0;
  const hasSubmittedSearch = activeSearch !== null;
  const resultCardTitle = hasSubmittedSearch
    ? `Results - ${resultsTotalElements.toLocaleString()} transaction${resultsTotalElements !== 1 ? 's' : ''}`
    : 'Insider Transactions';
  const resultCardSubtitle = filtersReady && hasActiveResultFilters && totalElements !== resultsTotalElements
    ? `${resultsFilings.length} shown - filtered from ${totalElements.toLocaleString()}`
    : (resultsFilings.length > 0 ? `${resultsFilings.length} shown` : null);
  const filterStatusText = chartLoading
    ? 'Loading the full result set to build filters...'
    : chartError
      ? 'Filters are unavailable because the full result set could not be loaded.'
      : hasSubmittedSearch
        ? 'Run a search that returns results to enable these filters.'
        : 'Run a search to enable these filters.';

  const searchDisabled =
    loading ||
    (searchType !== 'date' && !searchTerm.trim()) ||
    (searchType === 'date' && (!startDate || !endDate));

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <TrendingUp className="w-8 h-8 text-red-600" />
            <div>
              <h1 className="text-xl font-semibold">Form 4 - Insider Transactions</h1>
              <p className="text-gray-500 text-sm">
                Insider trading transactions and holdings reported to the SEC
              </p>
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

        <div className="flex gap-3 flex-wrap items-center">
          <select
            value={searchType}
            onChange={(event) => setSearchType(event.target.value as Form4SearchType)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="symbol">Ticker Symbol</option>
            <option value="cik">CIK</option>
            <option value="date">Date Range</option>
          </select>

          {searchType === 'date' ? (
            <>
              <input
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <span className="text-gray-400 text-sm">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </>
          ) : (
            <>
              <input
                type="text"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                onKeyDown={(event) => event.key === 'Enter' && !searchDisabled && handleSearch()}
                placeholder={searchType === 'symbol' ? 'e.g. PLTR, AAPL, TSLA' : 'e.g. 0001560327'}
                className="flex-1 min-w-[180px] px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {searchType === 'symbol' && (
                <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={showDateRange}
                    onChange={(event) => setShowDateRange(event.target.checked)}
                    className="rounded border-gray-300"
                  />
                  Date range
                </label>
              )}
              {showDateRange && searchType === 'symbol' && (
                <>
                  <input
                    type="date"
                    value={startDate}
                    onChange={(event) => setStartDate(event.target.value)}
                    className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <span className="text-gray-400 text-sm">to</span>
                  <input
                    type="date"
                    value={endDate}
                    onChange={(event) => setEndDate(event.target.value)}
                    className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </>
              )}
            </>
          )}

          <button
            onClick={handleSearch}
            disabled={searchDisabled}
            className="px-5 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors flex items-center gap-2 disabled:opacity-50 text-sm"
          >
            <Search className="w-4 h-4" /> Search
          </button>
        </div>
      </div>

      {!loading && chartLoading && (
        <div className="rounded-xl border border-gray-200 bg-white p-6">
          <LoadingSpinner size="lg" text="Building chart from all matching Form 4 transactions..." />
        </div>
      )}

      {!loading && !chartLoading && chartTicker && chartTransactions.length > 0 && (
        <Form4PriceChart
          ticker={chartTicker}
          anchorDate={chartRange.endDate}
          rangeStartDate={explicitRangeStart || chartRange.startDate}
          rangeEndDate={explicitRangeEnd || chartRange.endDate}
          transactions={chartTransactions}
        />
      )}

      {!loading && chartError && (
        <ErrorMessage message={chartError} />
      )}

      {!loading && !chartLoading && filteredChartFilings.length > 0 && filteredTickerList.length > 1 && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          The chart is shown for single-symbol Form 4 searches. Narrow the search to one ticker or CIK to overlay all related transactions on one price chart.
        </div>
      )}

      {error && <ErrorMessage message={error} />}

      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <h2 className="font-semibold text-lg">Result Filters</h2>
            <p className="text-sm text-gray-500">
              Refine the current Form 4 result set by insider name and the size of the reported trade.
            </p>
          </div>
          {filtersReady ? (
            <button
              type="button"
              onClick={resetResultFilters}
              disabled={!hasActiveResultFilters}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Reset Filters
            </button>
          ) : (
            <span className="text-sm text-gray-500">{filterStatusText}</span>
          )}
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-2">
          <div className="space-y-2">
            <label htmlFor="insider-filter" className="block text-sm font-medium text-gray-700">
              Insider Name
            </label>
            <select
              id="insider-filter"
              value={selectedInsiderName}
              onChange={(event) => setSelectedInsiderName(event.target.value)}
              disabled={!filtersReady}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
            >
              <option value="ALL">All insiders</option>
              {insiderOptions.map((insiderName) => (
                <option key={insiderName} value={insiderName}>
                  {insiderName}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-500">
              Based on insider names available in the loaded Form 4 result set.
            </p>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <label htmlFor="min-shares-filter" className="text-sm font-medium text-gray-700">
                Min Shares Traded
              </label>
              <span className="text-sm font-medium text-gray-900">
                {formatNumber(minSharesTraded)}
              </span>
            </div>
            <Slider
              id="min-shares-filter"
              value={[minSharesTraded]}
              min={0}
              max={sliderMax}
              step={sliderIncrement}
              disabled={!filtersReady}
              onValueChange={(values) => setMinSharesTraded(values[0] ?? 0)}
              className="py-1"
            />
            <div className="flex items-center justify-between text-xs text-gray-500">
              <span>0</span>
              <span>{formatNumber(maxSharesTraded)}</span>
            </div>
            <p className="text-xs text-gray-500">
              Uses the primary transaction share count shown in the results table.
            </p>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-lg">{resultCardTitle}</h2>
          {resultCardSubtitle && <span className="text-sm text-gray-500">{resultCardSubtitle}</span>}
        </div>

        {loading && (
          <div className="py-12">
            <LoadingSpinner size="lg" text="Fetching insider transactions..." />
          </div>
        )}

        {!loading && resultsFilings.length === 0 && (
          <EmptyState
            type="search"
            message={
              hasSubmittedSearch
                ? (filtersReady && hasActiveResultFilters
                  ? 'No transactions match the selected insider filters.'
                  : 'No transactions match your search criteria.')
                : 'Enter a ticker symbol (e.g. PLTR, AAPL) and click Search to view insider transactions.'
            }
          />
        )}

        {!loading && resultsFilings.length > 0 && (
          <>
            <FilingsTable filings={resultsFilings} marketPriceCache={marketPriceCache} />
            {resultsTotalPages > 1 && (
              <div className="mt-6">
                <Pagination
                  page={currentPage}
                  totalPages={resultsTotalPages}
                  totalElements={resultsTotalElements}
                  size={pageSize}
                  onPageChange={handlePageChange}
                  onPageSizeChange={handlePageSizeChange}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
