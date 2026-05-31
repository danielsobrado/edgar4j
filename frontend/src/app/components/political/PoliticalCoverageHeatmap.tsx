import React from 'react';
import { CalendarRange, ChevronLeft, ChevronRight, Download, Filter, Loader, RefreshCw } from 'lucide-react';
import { politicalTradesApi, PoliticalTradeCoverage } from '../../api';
import { CoverageHeatmapGrid, CoverageSelection, todayIso, ymd } from '../coverage/CoverageHeatmapGrid';

interface Props {
  /** Apply the selected disclosure-date range as a table filter. */
  onSelectRange: (from: string, to: string) => void;
  /** Pull the latest disclosures into the database (not date-scoped). */
  onSync: (from?: string, to?: string) => void;
  syncing: boolean;
  /** Changes after a sync so coverage refetches to show newly cached disclosures. */
  refreshKey: string;
  syncChunkPages?: number;
  syncPauseSeconds?: number;
  onSyncChunkPagesChange?: (value: number) => void;
  onSyncPauseSecondsChange?: (value: number) => void;
}

function bucket(count: number) {
  if (count <= 0) return 0;
  if (count <= 2) return 1;
  if (count <= 5) return 2;
  if (count <= 10) return 3;
  return 4;
}

const LEVEL_CLASSES = [
  'bg-gray-200 hover:bg-gray-300',
  'bg-emerald-200 hover:bg-emerald-300',
  'bg-emerald-300 hover:bg-emerald-400',
  'bg-emerald-500 hover:bg-emerald-600',
  'bg-emerald-700 hover:bg-emerald-800',
];

export function PoliticalCoverageHeatmap({
  onSelectRange,
  onSync,
  syncing,
  refreshKey,
  syncChunkPages = 5,
  syncPauseSeconds = 2,
  onSyncChunkPagesChange,
  onSyncPauseSecondsChange,
}: Props) {
  const todayStr = todayIso();
  const currentYear = Number(todayStr.slice(0, 4));

  const [year, setYear] = React.useState(currentYear);
  const [coverage, setCoverage] = React.useState<PoliticalTradeCoverage | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [selection, setSelection] = React.useState<CoverageSelection | null>(null);

  const loadCoverage = React.useCallback(async (signal?: { cancelled: boolean }) => {
    setLoading(true);
    setError(null);
    try {
      const data = await politicalTradesApi.coverage(`${year}-01-01`, `${year}-12-31`);
      if (!signal?.cancelled) {
        setCoverage(data);
      }
    } catch (err) {
      if (!signal?.cancelled) {
        setError(err instanceof Error ? err.message : 'Failed to load coverage');
      }
    } finally {
      if (!signal?.cancelled) {
        setLoading(false);
      }
    }
  }, [year]);

  React.useEffect(() => {
    const signal = { cancelled: false };
    void loadCoverage(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadCoverage, refreshKey]);

  const countByDate = React.useMemo(() => {
    const counts = new Map<string, number>();
    coverage?.days.forEach((day) => counts.set(day.date, day.count));
    return counts;
  }, [coverage]);

  const selectedTrades = React.useMemo(() => {
    if (!selection) {
      return 0;
    }
    let total = 0;
    const cursor = new Date(`${selection.start}T00:00:00`);
    const end = new Date(`${selection.end}T00:00:00`);
    while (cursor <= end) {
      const date = ymd(cursor.getFullYear(), cursor.getMonth(), cursor.getDate());
      total += countByDate.get(date) ?? 0;
      cursor.setDate(cursor.getDate() + 1);
    }
    return total;
  }, [selection, countByDate]);

  const changeYear = (delta: number) => {
    setYear((current) => current + delta);
    setSelection(null);
  };

  return (
    <div className="rounded-lg bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900">
            <CalendarRange className="h-5 w-5 text-gray-500" />
            Data Coverage
          </h2>
          <p className="text-sm text-gray-500">
            Cached disclosures per day, shaded by volume. Drag across a span to filter the table to those dates.
            Use Sync to pull the latest disclosures into the database.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => changeYear(-1)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
            aria-label="Previous year"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-16 text-center font-mono text-sm text-gray-800">{year}</span>
          <button
            onClick={() => changeYear(1)}
            disabled={year >= currentYear}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Next year"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
          <button
            onClick={() => void loadCoverage()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {error && <p className="mt-4 text-sm text-red-700">{error}</p>}

      <div className="mt-4">
        <CoverageHeatmapGrid
          year={year}
          selection={selection}
          onSelectionChange={setSelection}
          levelClass={(level) => LEVEL_CLASSES[level] ?? LEVEL_CLASSES[0]}
          getDay={(date) => {
            const disabled = date > todayStr;
            const count = countByDate.get(date) ?? 0;
            return {
              level: bucket(count),
              disabled,
              title: disabled
                ? `${date} — future date`
                : `${date} — ${count} disclosure${count === 1 ? '' : 's'}`,
            };
          }}
        />
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-gray-600">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-gray-200" /> None
        </span>
        <span className="inline-flex items-center gap-1">
          Fewer
          <span className="h-3 w-3 rounded-sm bg-emerald-200" />
          <span className="h-3 w-3 rounded-sm bg-emerald-300" />
          <span className="h-3 w-3 rounded-sm bg-emerald-500" />
          <span className="h-3 w-3 rounded-sm bg-emerald-700" />
          More
        </span>
      </div>

      <div className="mt-4 flex flex-col gap-3 border-t border-gray-100 pt-4 lg:flex-row lg:items-end lg:justify-between">
        <p className="text-sm text-gray-600 lg:pb-2">
          {selection ? (
            <>
              Selected <span className="font-mono">{selection.start}</span> → <span className="font-mono">{selection.end}</span>
              {' '}({selectedTrades.toLocaleString()} disclosure{selectedTrades === 1 ? '' : 's'})
            </>
          ) : (
            'Drag across the grid to select a disclosure-date range.'
          )}
        </p>
        <div className="flex flex-wrap items-end gap-2 lg:ml-auto">
          <label htmlFor="political-coverage-chunk-pages" className="block text-xs text-gray-500">
            Chunk pages
            <input
              id="political-coverage-chunk-pages"
              type="number"
              min="1"
              max="50"
              value={syncChunkPages}
              onChange={(event) => onSyncChunkPagesChange?.(Number(event.target.value) || 5)}
              className="mt-1 h-9 w-24 rounded-md border border-gray-300 px-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label htmlFor="political-coverage-pause-seconds" className="block text-xs text-gray-500">
            Pause sec
            <input
              id="political-coverage-pause-seconds"
              type="number"
              min="0"
              max="60"
              value={syncPauseSeconds}
              onChange={(event) => onSyncPauseSecondsChange?.(Math.max(0, Number(event.target.value) || 0))}
              className="mt-1 h-9 w-24 rounded-md border border-gray-300 px-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <button
            onClick={() => void onSync(selection?.start, selection?.end)}
            disabled={syncing}
            title={selection ? 'Pull latest disclosures and save only the selected disclosure-date range' : 'Pull the latest disclosures into the database'}
            className="inline-flex items-center justify-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {syncing ? <Loader className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            Sync now
          </button>
          <button
            onClick={() => setSelection(null)}
            disabled={!selection}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Clear
          </button>
          <button
            onClick={() => selection && onSelectRange(selection.start, selection.end)}
            disabled={!selection}
            className="inline-flex items-center justify-center gap-2 rounded-md bg-blue-600 px-5 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Filter className="h-4 w-4" />
            Filter to selection
          </button>
        </div>
      </div>
    </div>
  );
}
