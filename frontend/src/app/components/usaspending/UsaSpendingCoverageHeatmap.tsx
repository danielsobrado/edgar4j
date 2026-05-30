import React from 'react';
import { CalendarRange, ChevronLeft, ChevronRight, Download, Loader, RefreshCw } from 'lucide-react';
import { downloadsApi, UsaSpendingCoverage } from '../../api';
import { showError, showSuccess } from '../../store/notificationStore';
import {
  CoverageHeatmapGrid,
  CoverageSelection,
  daysInMonth,
  todayIso,
  ymd,
} from '../coverage/CoverageHeatmapGrid';

interface Props {
  /** Called with the queued job id so the page can track status / refresh the preview. */
  onJobQueued: (jobId: string) => void;
  /** Changes whenever a USAspending job completes, so coverage refetches to show new data. */
  refreshKey: string;
}

export function UsaSpendingCoverageHeatmap({ onJobQueued, refreshKey }: Props) {
  const todayStr = todayIso();
  const currentYear = Number(todayStr.slice(0, 4));

  const [year, setYear] = React.useState(currentYear);
  const [coverage, setCoverage] = React.useState<UsaSpendingCoverage | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [queueing, setQueueing] = React.useState(false);
  const [selection, setSelection] = React.useState<CoverageSelection | null>(null);

  const loadCoverage = React.useCallback(async (signal?: { cancelled: boolean }) => {
    setLoading(true);
    setError(null);
    try {
      const data = await downloadsApi.getUsaSpendingCoverage(`${year}-01-01`, `${year}-12-31`);
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

  const coveredDays = React.useMemo(() => {
    const covered = new Set<string>();
    if (!coverage) {
      return covered;
    }
    const ranges = coverage.ranges.map((range) => ({ from: range.dateFrom, to: range.dateTo }));
    for (let month = 0; month < 12; month++) {
      const total = daysInMonth(year, month);
      for (let day = 1; day <= total; day++) {
        const date = ymd(year, month, day);
        if (ranges.some((range) => date >= range.from && date <= range.to)) {
          covered.add(date);
        }
      }
    }
    return covered;
  }, [coverage, year]);

  const selectionStats = React.useMemo(() => {
    if (!selection) {
      return { totalDays: 0, gapDays: 0 };
    }
    let totalDays = 0;
    let gapDays = 0;
    const cursor = new Date(`${selection.start}T00:00:00`);
    const end = new Date(`${selection.end}T00:00:00`);
    while (cursor <= end) {
      const date = ymd(cursor.getFullYear(), cursor.getMonth(), cursor.getDate());
      totalDays += 1;
      if (!coveredDays.has(date) && date <= todayStr) {
        gapDays += 1;
      }
      cursor.setDate(cursor.getDate() + 1);
    }
    return { totalDays, gapDays };
  }, [selection, coveredDays, todayStr]);

  const changeYear = (delta: number) => {
    setYear((current) => current + delta);
    setSelection(null);
  };

  const queueDownload = async () => {
    if (!selection) {
      return;
    }
    setQueueing(true);
    try {
      const job = await downloadsApi.downloadUsaSpendingAwards({ dateFrom: selection.start, dateTo: selection.end });
      onJobQueued(job.id);
      showSuccess('Download Started', `USAspending download queued for ${selection.start} to ${selection.end}`);
    } catch (err) {
      showError('Download Failed', err instanceof Error ? err.message : 'Failed to start USAspending download');
    } finally {
      setQueueing(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-sm p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2">
            <CalendarRange className="w-5 h-5" />
            Data Coverage
          </h2>
          <p className="text-sm text-gray-600">
            Each cell is one day of award action dates. Drag across the gaps to select a range, then download it.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => changeYear(-1)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
            aria-label="Previous year"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="min-w-16 text-center font-mono text-sm text-gray-800">{year}</span>
          <button
            onClick={() => changeYear(1)}
            disabled={year >= currentYear}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Next year"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
          <button
            onClick={() => void loadCoverage()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
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
          levelClass={(level) =>
            level >= 1 ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-gray-200 hover:bg-gray-300'
          }
          getDay={(date) => {
            const disabled = date > todayStr;
            const covered = coveredDays.has(date);
            return {
              level: covered ? 1 : 0,
              disabled,
              title: `${date} — ${disabled ? 'future date' : covered ? 'have data' : 'no data (gap)'}`,
            };
          }}
        />
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-4 text-xs text-gray-600">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-emerald-500" /> Have data
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-gray-200" /> Gap
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-gray-50 ring-1 ring-inset ring-gray-200" /> Future
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-gray-200 ring-2 ring-inset ring-blue-600" /> Selected
        </span>
      </div>

      <div className="mt-4 flex flex-col gap-3 border-t border-gray-100 pt-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-gray-600">
          {selection ? (
            <>
              Selected <span className="font-mono">{selection.start}</span> → <span className="font-mono">{selection.end}</span>
              {' '}({selectionStats.totalDays} day{selectionStats.totalDays === 1 ? '' : 's'}, {selectionStats.gapDays} gap)
            </>
          ) : (
            'Drag across the grid to select a date range to download.'
          )}
        </p>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setSelection(null)}
            disabled={!selection}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Clear
          </button>
          <button
            onClick={() => void queueDownload()}
            disabled={!selection || queueing}
            className="inline-flex items-center justify-center gap-2 rounded-md bg-[#1a1f36] px-5 py-2 text-white hover:bg-[#252b47] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {queueing ? <Loader className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Download selection
          </button>
        </div>
      </div>
    </div>
  );
}
