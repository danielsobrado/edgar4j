import React from 'react';
import { Link } from 'react-router-dom';
import { addMonths, format, parseISO, subMonths } from 'date-fns';
import {
  type BusinessDay,
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  type IChartApi,
  type ISeriesApi,
  type CandlestickData,
  type CandlestickSeriesOptions,
  type HistogramData,
  type HistogramSeriesOptions,
  type MouseEventParams,
  type Time,
  type UTCTimestamp,
  createChart,
  createSeriesMarkers,
  HistogramSeries,
} from 'lightweight-charts';
import { Form4Transaction, marketDataApi, MarketPriceHistory } from '../api';
import { Tooltip, TooltipContent, TooltipTrigger } from './ui/tooltip';
import { buildMarketCloseLookup, resolveEstimatedClose, type MarketCloseLookup } from '../utils';

type Props = {
  ticker: string;
  anchorDate?: string;
  rangeStartDate?: string;
  rangeEndDate?: string;
  transactions: Form4Transaction[];
};

type MarkerDirection = 'buy' | 'sell' | 'other';

type MarkerAggregate = {
  key: string;
  date: string;
  direction: MarkerDirection;
  count: number;
  totalShares: number;
  totalValue: number;
  reportedShares: number;
  reportedValue: number;
  reportedTransactionCount: number;
  estimatedShares: number;
  estimatedValue: number;
  estimatedTransactionCount: number;
  priorCloseEstimateCount: number;
  unresolvedTransactionCount: number;
};

type PriceBand = {
  direction: MarkerDirection;
  price: number;
  label: string;
  color: string;
};

type MarkerTooltipState = {
  kind: 'marker';
  aggregate: MarkerAggregate;
  x: number;
  y: number;
};

type VolumeTooltipState = {
  kind: 'volume';
  date: string;
  aggregates: MarkerAggregate[];
  x: number;
  y: number;
};

type ChartTooltipState = MarkerTooltipState | VolumeTooltipState;

function toTimestamp(date: string): UTCTimestamp {
  return Math.floor(parseISO(date).getTime() / 1000) as UTCTimestamp;
}

function formatRangeDate(date: Date): string {
  return format(date, 'yyyy-MM-dd');
}

function clampRangeEnd(date: Date): Date {
  const today = new Date();
  return date > today ? today : date;
}

function buildRange(anchorDate?: string, rangeStartDate?: string, rangeEndDate?: string) {
  if (rangeStartDate && rangeEndDate) {
    const start = subMonths(parseISO(rangeStartDate), 1);
    const end = clampRangeEnd(addMonths(parseISO(rangeEndDate), 1));
    return {
      startDate: formatRangeDate(start),
      endDate: formatRangeDate(end),
    };
  }

  const anchor = anchorDate ? parseISO(anchorDate) : new Date();
  const start = subMonths(anchor, 6);
  const end = clampRangeEnd(addMonths(anchor, 1));
  return {
    startDate: formatRangeDate(start),
    endDate: formatRangeDate(end),
  };
}

function transactionDirection(transaction: Form4Transaction): MarkerDirection {
  if (transaction.acquiredDisposedCode === 'A') return 'buy';
  if (transaction.acquiredDisposedCode === 'D') return 'sell';
  return 'other';
}

function transactionReportedNotionalValue(transaction: Form4Transaction): number | null {
  if (transaction.transactionValue != null && Math.abs(transaction.transactionValue) > 0) {
    return Math.abs(transaction.transactionValue);
  }
  if (
    transaction.transactionShares != null
    && transaction.transactionPricePerShare != null
    && Math.abs(transaction.transactionPricePerShare) > 0
  ) {
    return Math.abs(transaction.transactionShares * transaction.transactionPricePerShare);
  }
  return null;
}

function compactNumber(value: number) {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: value >= 1000 ? 1 : 0,
  }).format(value);
}

function compactCurrency(value: number) {
  return `$${compactNumber(value)}`;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value);
}

function timeToDateString(time?: Time): string | null {
  if (time == null) {
    return null;
  }

  if (typeof time === 'string') {
    return time;
  }

  if (typeof time === 'number') {
    return format(new Date((time as UTCTimestamp as number) * 1000), 'yyyy-MM-dd');
  }

  const businessDay = time as BusinessDay;
  return [
    businessDay.year,
    String(businessDay.month).padStart(2, '0'),
    String(businessDay.day).padStart(2, '0'),
  ].join('-');
}

function aggregateMarkers(
  transactions: Form4Transaction[],
  marketCloseLookup: MarketCloseLookup | null,
): MarkerAggregate[] {
  const byKey = new Map<string, MarkerAggregate>();

  transactions
    .filter((transaction) => !!transaction.transactionDate)
    .forEach((transaction) => {
      const date = transaction.transactionDate!;
      const direction = transactionDirection(transaction);
      const key = `${date}:${direction}`;
      const existing = byKey.get(key) ?? {
        key,
        date,
        direction,
        count: 0,
        totalShares: 0,
        totalValue: 0,
        reportedShares: 0,
        reportedValue: 0,
        reportedTransactionCount: 0,
        estimatedShares: 0,
        estimatedValue: 0,
        estimatedTransactionCount: 0,
        priorCloseEstimateCount: 0,
        unresolvedTransactionCount: 0,
      };

      const shares = Math.abs(transaction.transactionShares ?? 0);
      const reportedValue = transactionReportedNotionalValue(transaction);
      const estimatedClose = reportedValue == null ? resolveEstimatedClose(date, marketCloseLookup) : null;
      const estimatedValue = estimatedClose && shares > 0 ? Math.abs(shares * estimatedClose.close) : null;

      existing.count += 1;
      existing.totalShares += shares;
      if (reportedValue != null) {
        existing.reportedShares += shares;
        existing.reportedValue += reportedValue;
        existing.totalValue += reportedValue;
        existing.reportedTransactionCount += 1;
      } else if (estimatedValue != null) {
        existing.estimatedShares += shares;
        existing.estimatedValue += estimatedValue;
        existing.totalValue += estimatedValue;
        existing.estimatedTransactionCount += 1;
        if (estimatedClose.sourceDate !== date) {
          existing.priorCloseEstimateCount += 1;
        }
      } else {
        existing.unresolvedTransactionCount += 1;
      }

      byKey.set(key, existing);
    });

  return Array.from(byKey.values()).sort((left, right) => left.date.localeCompare(right.date));
}

function aggregateRankValue(aggregate: MarkerAggregate) {
  return aggregate.totalValue > 0 ? aggregate.totalValue : aggregate.totalShares;
}

function aggregateDisplayAmount(aggregate: MarkerAggregate) {
  if (aggregate.totalValue > 0) {
    return `${compactCurrency(aggregate.totalValue)}${aggregate.estimatedTransactionCount > 0 ? '*' : ''}`;
  }
  if (aggregate.totalShares > 0) {
    return `${compactNumber(aggregate.totalShares)} sh`;
  }
  return '-';
}

function aggregateAveragePrice(aggregate: MarkerAggregate) {
  const valuedShares = aggregate.reportedShares + aggregate.estimatedShares;
  if (valuedShares <= 0 || aggregate.totalValue <= 0) {
    return null;
  }

  return aggregate.totalValue / valuedShares;
}

function aggregateValueSummary(aggregate: MarkerAggregate) {
  if (aggregate.totalValue > 0) {
    return `${formatCurrency(aggregate.totalValue)} total notional${aggregate.estimatedTransactionCount > 0 ? '*' : ''}`;
  }

  if (aggregate.reportedTransactionCount > 0) {
    return `${formatCurrency(0)} reported notional`;
  }

  return 'No filing or market price available';
}

function markerText(aggregate: MarkerAggregate) {
  if (aggregate.count === 1) {
    const prefix = aggregate.direction === 'sell' ? 'S' : aggregate.direction === 'buy' ? 'B' : 'T';
    const valueText = aggregate.totalValue > 0
      ? `${compactCurrency(aggregate.totalValue)}${aggregate.estimatedTransactionCount > 0 ? '*' : ''}`
      : compactNumber(aggregate.totalShares);
    return `${prefix} ${valueText}`;
  }

  const prefix = aggregate.direction === 'sell' ? 'S' : aggregate.direction === 'buy' ? 'B' : 'T';
  return `${prefix}x${aggregate.count}${aggregate.estimatedTransactionCount > 0 ? '*' : ''}`;
}

function labelBudget(markerCount: number) {
  if (markerCount > 80) return 10;
  if (markerCount > 40) return 16;
  if (markerCount > 20) return 24;
  return markerCount;
}

function buildPriceBands(transactions: Form4Transaction[]): PriceBand[] {
  const directions: MarkerDirection[] = ['buy', 'sell'];

  return directions.flatMap((direction) => {
    const matching = transactions.filter((transaction) =>
      transactionDirection(transaction) === direction
      && transaction.transactionPricePerShare != null
      && transaction.transactionPricePerShare > 0
      && transaction.transactionShares != null
      && transaction.transactionShares > 0
    );

    if (matching.length === 0) {
      return [];
    }

    const totalShares = matching.reduce((sum, transaction) => sum + (transaction.transactionShares ?? 0), 0);
    if (totalShares <= 0) {
      return [];
    }

    const weightedPrice = matching.reduce(
      (sum, transaction) => sum + ((transaction.transactionPricePerShare ?? 0) * (transaction.transactionShares ?? 0)),
      0
    ) / totalShares;

    return [{
      direction,
      price: weightedPrice,
      label: direction === 'buy' ? 'Avg buy px' : 'Avg sell px',
      color: direction === 'buy' ? '#4ade80' : '#f87171',
    }];
  });
}

function AggregateTooltipBody({
  aggregate,
  priceSourceLabel,
}: {
  aggregate: MarkerAggregate;
  priceSourceLabel: string;
}) {
  const averagePrice = aggregateAveragePrice(aggregate);
  const averagePriceLabel = aggregate.estimatedTransactionCount > 0
    ? 'Avg price used'
    : 'Avg executed price';

  return (
    <div className="space-y-1">
      <p className="font-medium">
        {format(parseISO(aggregate.date), 'MMM d, yyyy')} {aggregate.direction === 'sell' ? 'sell' : 'buy'} cluster
      </p>
      <p>{aggregate.count} Form 4 transaction{aggregate.count === 1 ? '' : 's'}</p>
      <p>{aggregate.totalShares.toLocaleString()} shares</p>
      <p>{aggregateValueSummary(aggregate)}</p>
      {averagePrice != null && (
        <p>{averagePriceLabel}: {formatCurrency(averagePrice)}</p>
      )}
      {aggregate.estimatedTransactionCount > 0 && (
        <p>
          * {aggregate.estimatedTransactionCount} transaction{aggregate.estimatedTransactionCount === 1 ? '' : 's'} valued with {priceSourceLabel} close
          {aggregate.priorCloseEstimateCount > 0 ? `; ${aggregate.priorCloseEstimateCount} used prior trading day close` : ''}
        </p>
      )}
      {aggregate.unresolvedTransactionCount > 0 && (
        <p>
          {aggregate.unresolvedTransactionCount} transaction{aggregate.unresolvedTransactionCount === 1 ? '' : 's'} excluded from notional value
        </p>
      )}
    </div>
  );
}

function VolumeTooltipBody({
  date,
  aggregates,
  priceSourceLabel,
}: {
  date: string;
  aggregates: MarkerAggregate[];
  priceSourceLabel: string;
}) {
  return (
    <div className="space-y-1">
      <p className="font-medium">{format(parseISO(date), 'MMM d, yyyy')} insider value</p>
      {aggregates.map((aggregate) => (
        <div key={aggregate.key} className="space-y-1">
          <p>
            {aggregate.direction === 'sell' ? 'Sell' : aggregate.direction === 'buy' ? 'Buy' : 'Other'}: {formatCurrency(aggregate.totalValue)}
            {aggregate.estimatedTransactionCount > 0 ? '*' : ''}
          </p>
          <p>
            {aggregate.count} Form 4 transaction{aggregate.count === 1 ? '' : 's'} across {aggregate.totalShares.toLocaleString()} shares
          </p>
          {aggregate.estimatedTransactionCount > 0 && (
            <p>
              * {aggregate.estimatedTransactionCount} transaction{aggregate.estimatedTransactionCount === 1 ? '' : 's'} valued with {priceSourceLabel} close
              {aggregate.priorCloseEstimateCount > 0 ? `; ${aggregate.priorCloseEstimateCount} used prior trading day close` : ''}
            </p>
          )}
          {aggregate.unresolvedTransactionCount > 0 && (
            <p>
              {aggregate.unresolvedTransactionCount} transaction{aggregate.unresolvedTransactionCount === 1 ? '' : 's'} excluded from bar value
            </p>
          )}
        </div>
      ))}
    </div>
  );
}

export function Form4PriceChart({ ticker, anchorDate, rangeStartDate, rangeEndDate, transactions }: Props) {
  const chartRef = React.useRef<HTMLDivElement | null>(null);
  const [history, setHistory] = React.useState<MarketPriceHistory | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [chartTooltip, setChartTooltip] = React.useState<ChartTooltipState | null>(null);
  const marketCloseLookup = React.useMemo(() => buildMarketCloseLookup(history), [history]);
  const markerAggregates = React.useMemo(
    () => aggregateMarkers(transactions, marketCloseLookup),
    [marketCloseLookup, transactions],
  );
  const markerLookup = React.useMemo(
    () => new Map(markerAggregates.map((aggregate) => [aggregate.key, aggregate])),
    [markerAggregates]
  );
  const volumeLookup = React.useMemo(() => {
    const byDate = new Map<string, MarkerAggregate[]>();

    markerAggregates
      .filter((aggregate) => aggregate.totalValue > 0)
      .forEach((aggregate) => {
        const existing = byDate.get(aggregate.date) ?? [];
        existing.push(aggregate);
        existing.sort((left, right) => {
          const directionOrder = { buy: 0, sell: 1, other: 2 } satisfies Record<MarkerDirection, number>;
          return directionOrder[left.direction] - directionOrder[right.direction];
        });
        byDate.set(aggregate.date, existing);
      });

    return byDate;
  }, [markerAggregates]);
  const transactionCount = React.useMemo(
    () => transactions.filter((transaction) => !!transaction.transactionDate).length,
    [transactions]
  );
  const estimatedTransactionCount = React.useMemo(
    () => markerAggregates.reduce((sum, aggregate) => sum + aggregate.estimatedTransactionCount, 0),
    [markerAggregates],
  );
  const priorCloseEstimateCount = React.useMemo(
    () => markerAggregates.reduce((sum, aggregate) => sum + aggregate.priorCloseEstimateCount, 0),
    [markerAggregates],
  );
  const collapsedCount = React.useMemo(
    () => markerAggregates.filter((aggregate) => aggregate.count > 1).length,
    [markerAggregates]
  );
  const labeledMarkerKeys = React.useMemo(() => {
    const aggregatedKeys = markerAggregates
      .filter((aggregate) => aggregate.count > 1)
      .map((aggregate) => aggregate.key);

    const remainingBudget = Math.max(0, labelBudget(markerAggregates.length) - aggregatedKeys.length);
    const rankedSingles = markerAggregates
      .filter((aggregate) => aggregate.count === 1)
      .sort((left, right) => aggregateRankValue(right) - aggregateRankValue(left))
      .slice(0, remainingBudget)
      .map((aggregate) => aggregate.key);

    return new Set([...aggregatedKeys, ...rankedSingles]);
  }, [markerAggregates]);
  const topCollapsedDays = React.useMemo(() => (
    [...markerAggregates]
      .filter((aggregate) => aggregate.count > 1)
      .sort((left, right) => {
        if (right.count !== left.count) {
          return right.count - left.count;
        }
        return aggregateRankValue(right) - aggregateRankValue(left);
      })
      .slice(0, 8)
  ), [markerAggregates]);
  const priceBands = React.useMemo(() => buildPriceBands(transactions), [transactions]);
  const marketPriceSource = history?.provider || 'market';

  React.useEffect(() => {
    let cancelled = false;
    const { startDate, endDate } = buildRange(anchorDate, rangeStartDate, rangeEndDate);

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await marketDataApi.getPriceHistory(ticker, startDate, endDate);
        if (!cancelled) {
          setHistory(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load market data');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [anchorDate, rangeEndDate, rangeStartDate, ticker]);

  React.useEffect(() => {
    if (!chartRef.current || !history || history.prices.length === 0) {
      return;
    }

    const container = chartRef.current;
    const chart: IChartApi = createChart(container, {
      autoSize: true,
      height: 360,
      layout: {
        background: { type: ColorType.Solid, color: '#ffffff' },
        textColor: '#334155',
      },
      grid: {
        vertLines: { color: '#e2e8f0' },
        horzLines: { color: '#e2e8f0' },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
      },
      rightPriceScale: {
        borderColor: '#cbd5e1',
      },
      timeScale: {
        borderColor: '#cbd5e1',
        timeVisible: false,
      },
    });

    const candleSeries: ISeriesApi<'Candlestick'> = chart.addSeries(
      CandlestickSeries,
      {
        upColor: '#16a34a',
        downColor: '#dc2626',
        borderUpColor: '#16a34a',
        borderDownColor: '#dc2626',
        wickUpColor: '#16a34a',
        wickDownColor: '#dc2626',
      } satisfies CandlestickSeriesOptions
    );

    const buyValueSeries: ISeriesApi<'Histogram'> = chart.addSeries(
      HistogramSeries,
      {
        priceFormat: { type: 'volume' },
        priceScaleId: 'volume',
        color: 'rgba(22, 163, 74, 0.58)',
      } satisfies HistogramSeriesOptions
    );
    const sellValueSeries: ISeriesApi<'Histogram'> = chart.addSeries(
      HistogramSeries,
      {
        priceFormat: { type: 'volume' },
        priceScaleId: 'volume',
        color: 'rgba(220, 38, 38, 0.58)',
      } satisfies HistogramSeriesOptions
    );
    chart.priceScale('volume').applyOptions({
      scaleMargins: {
        top: 0.78,
        bottom: 0,
      },
    });

    const candleData: CandlestickData[] = history.prices.map((price) => ({
      time: toTimestamp(price.date),
      open: price.open,
      high: price.high,
      low: price.low,
      close: price.close,
    }));
    candleSeries.setData(candleData);

    const buyValueData: HistogramData[] = markerAggregates
      .filter((aggregate) => aggregate.direction === 'buy' && aggregate.totalValue > 0)
      .map((aggregate) => ({
        time: toTimestamp(aggregate.date),
        value: aggregate.totalValue,
        color: 'rgba(22, 163, 74, 0.58)',
      }));
    const sellValueData: HistogramData[] = markerAggregates
      .filter((aggregate) => aggregate.direction === 'sell' && aggregate.totalValue > 0)
      .map((aggregate) => ({
        time: toTimestamp(aggregate.date),
        value: aggregate.totalValue,
        color: 'rgba(220, 38, 38, 0.58)',
      }));
    buyValueSeries.setData(buyValueData);
    sellValueSeries.setData(sellValueData);

    const markers = markerAggregates.map((aggregate) => ({
      id: aggregate.key,
      time: toTimestamp(aggregate.date),
      position: aggregate.direction === 'sell' ? 'aboveBar' : 'belowBar',
      color: aggregate.direction === 'sell' ? '#dc2626' : aggregate.direction === 'buy' ? '#16a34a' : '#475569',
      shape: aggregate.direction === 'sell' ? 'arrowDown' : aggregate.direction === 'buy' ? 'arrowUp' : 'circle',
      text: labeledMarkerKeys.has(aggregate.key) ? markerText(aggregate) : '',
    }));
    createSeriesMarkers(candleSeries, markers);

    const updateMarkerTooltip = (hoveredObjectId: unknown, x: number, y: number) => {
      const aggregate = markerLookup.get(String(hoveredObjectId));
      if (!aggregate) {
        return false;
      }

      setChartTooltip({
        kind: 'marker',
        aggregate,
        x,
        y,
      });
      return true;
    };

    const updateVolumeTooltip = (param: MouseEventParams, x: number, y: number) => {
      const buyValuePoint = param.seriesData.get(buyValueSeries);
      const sellValuePoint = param.seriesData.get(sellValueSeries);
      const hasVolumePoint = (buyValuePoint && 'value' in buyValuePoint && typeof buyValuePoint.value === 'number')
        || (sellValuePoint && 'value' in sellValuePoint && typeof sellValuePoint.value === 'number');
      if (!hasVolumePoint) {
        return false;
      }

      const date = timeToDateString(param.time);
      if (!date) {
        return false;
      }

      const aggregates = volumeLookup.get(date);
      if (!aggregates || aggregates.length === 0) {
        return false;
      }

      setChartTooltip({
        kind: 'volume',
        date,
        aggregates,
        x,
        y,
      });
      return true;
    };

    const handleCrosshairMove = (param: MouseEventParams) => {
      if (!param.point) {
        setChartTooltip(null);
        return;
      }

      if (param.hoveredObjectId != null && updateMarkerTooltip(param.hoveredObjectId, param.point.x, param.point.y)) {
        return;
      }

      if (updateVolumeTooltip(param, param.point.x, param.point.y)) {
        return;
      }

      setChartTooltip(null);
    };

    const handleClick = (param: MouseEventParams) => {
      if (!param.point) {
        setChartTooltip(null);
        return;
      }

      if (param.hoveredObjectId != null && updateMarkerTooltip(param.hoveredObjectId, param.point.x, param.point.y)) {
        return;
      }

      if (updateVolumeTooltip(param, param.point.x, param.point.y)) {
        return;
      }

      setChartTooltip(null);
    };

    chart.subscribeCrosshairMove(handleCrosshairMove);
    chart.subscribeClick(handleClick);

    priceBands.forEach((band) => {
      candleSeries.createPriceLine({
        price: band.price,
        color: band.color,
        lineStyle: 2,
        lineWidth: 1,
        axisLabelVisible: true,
        title: band.label,
      });
    });

    chart.timeScale().fitContent();

    const resizeObserver = new ResizeObserver(() => {
      chart.applyOptions({ width: container.clientWidth });
    });
    resizeObserver.observe(container);

    return () => {
      chart.unsubscribeCrosshairMove(handleCrosshairMove);
      chart.unsubscribeClick(handleClick);
      setChartTooltip(null);
      resizeObserver.disconnect();
      chart.remove();
    };
  }, [history, labeledMarkerKeys, markerAggregates, markerLookup, priceBands, volumeLookup]);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h4 className="text-lg font-semibold text-slate-900">Price Context</h4>
          <p className="mt-1 text-sm text-slate-600">
            Daily candles for {ticker} with Form 4 transactions drawn directly on the chart.
          </p>
        </div>
        {history && (
          <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
            {history.provider}
          </div>
        )}
      </div>

      {loading && <p className="text-sm text-slate-600">Loading price history and chart data...</p>}

      {!loading && error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <p>{error}</p>
          <p className="mt-2">
            If Tiingo is not configured yet, set it up in{' '}
            <Link to="/settings" className="font-medium underline">
              Settings
            </Link>
            .
          </p>
        </div>
      )}

      {!loading && !error && history && history.prices.length === 0 && (
        <p className="text-sm text-slate-600">No price bars were returned for this period.</p>
      )}

      {!loading && !error && history && history.prices.length > 0 && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <div className="rounded-lg bg-slate-50 p-3">
              <p className="text-xs uppercase tracking-wide text-slate-500">Range</p>
              <p className="mt-1 text-sm text-slate-900">{history.startDate} to {history.endDate}</p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3">
              <p className="text-xs uppercase tracking-wide text-slate-500">Bars</p>
              <p className="mt-1 text-sm text-slate-900">{history.prices.length}</p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3">
              <p className="text-xs uppercase tracking-wide text-slate-500">Plot Points</p>
              <p className="mt-1 text-sm text-slate-900">
                {markerAggregates.length} markers from {transactionCount} transactions
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {collapsedCount} multi-trade day groups collapsed by date and direction
              </p>
              {estimatedTransactionCount > 0 && (
                <p className="mt-1 text-xs text-slate-500">
                  {estimatedTransactionCount} transaction{estimatedTransactionCount === 1 ? '' : 's'} valued from {marketPriceSource} close
                </p>
              )}
            </div>
          </div>

          <div className="relative">
            <div ref={chartRef} className="w-full overflow-hidden rounded-lg border border-slate-200" />
            {chartTooltip && (
              <div
                className="pointer-events-none absolute z-20 max-w-xs rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white shadow-xl"
                style={{
                  left: Math.max(12, Math.min(chartTooltip.x, (chartRef.current?.clientWidth ?? chartTooltip.x) - 220)),
                  top: chartTooltip.y < 120 ? chartTooltip.y + 18 : chartTooltip.y - 18,
                  transform: chartTooltip.y < 120 ? 'translateX(-12%)' : 'translate(-12%, -100%)',
                }}
              >
                {chartTooltip.kind === 'marker' ? (
                  <AggregateTooltipBody aggregate={chartTooltip.aggregate} priceSourceLabel={marketPriceSource} />
                ) : (
                  <VolumeTooltipBody date={chartTooltip.date} aggregates={chartTooltip.aggregates} priceSourceLabel={marketPriceSource} />
                )}
              </div>
            )}
          </div>

          {topCollapsedDays.length > 0 && (
            <div className="rounded-lg bg-slate-50 p-3">
              <p className="text-xs uppercase tracking-wide text-slate-500">Collapsed Activity</p>
              <div className="mt-2 flex flex-wrap gap-2 text-xs">
                {topCollapsedDays.map((aggregate) => (
                  <Tooltip key={aggregate.key}>
                    <TooltipTrigger asChild>
                      <button
                        type="button"
                        className={`rounded-full px-3 py-1 text-left ${
                          aggregate.direction === 'sell'
                            ? 'bg-rose-100 text-rose-700'
                            : 'bg-emerald-100 text-emerald-700'
                        }`}
                      >
                        {format(parseISO(aggregate.date), 'MMM d')} {aggregate.direction === 'sell' ? 'S' : 'B'}x{aggregate.count} {aggregateDisplayAmount(aggregate)}
                      </button>
                    </TooltipTrigger>
                    <TooltipContent side="top" sideOffset={6} className="max-w-xs bg-slate-900 text-white">
                      <AggregateTooltipBody aggregate={aggregate} priceSourceLabel={marketPriceSource} />
                    </TooltipContent>
                  </Tooltip>
                ))}
              </div>
            </div>
          )}

          <div className="flex flex-wrap gap-2 text-xs text-slate-600">
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">Green arrow: buy / acquire</span>
            <span className="rounded-full bg-rose-50 px-3 py-1 text-rose-700">Red arrow: sell / dispose</span>
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">Green bars: insider buy value</span>
            <span className="rounded-full bg-rose-50 px-3 py-1 text-rose-700">Red bars: insider sell value</span>
            <span className="rounded-full bg-slate-100 px-3 py-1">Markers are aggregated by day and direction</span>
            <span className="rounded-full bg-slate-100 px-3 py-1">Collapsed markers use `BxN` / `SxN` labels</span>
            <span className="rounded-full bg-slate-100 px-3 py-1">Price lines show weighted average reported buy/sell levels</span>
            {estimatedTransactionCount > 0 && (
              <span className="rounded-full bg-amber-50 px-3 py-1 text-amber-700">
                * Uses {marketPriceSource} close when filing price is missing or zero
              </span>
            )}
            {priorCloseEstimateCount > 0 && (
              <span className="rounded-full bg-amber-50 px-3 py-1 text-amber-700">
                Weekend and holiday estimates use the prior trading close
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
