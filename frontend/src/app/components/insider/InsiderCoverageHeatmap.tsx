import React from 'react';
import { CalendarRange, ChevronLeft, ChevronRight, Download, Filter, Loader, RefreshCw } from 'lucide-react';
import { CoverageHeatmapGrid, CoverageSelection, todayIso, ymd } from '../coverage/CoverageHeatmapGrid';
import { insiderActivityApi } from '../../api';

interface Props {
  /** Apply the selected transaction-date range as screener filter. */
  onSelectRange: (from: string, to: string) => void;
  /** Queue filings download for selected date window and form. */
  onDownload: (form: string, from: string, to: string, remoteFilingSyncMode: 'COMPANY' | 'FILING_DATE') => void;
  downloading: boolean;
  refreshKey: string;
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

export function InsiderCoverageHeatmap({ onSelectRange, onDownload, downloading, refreshKey }: Props) {
  const todayStr = todayIso();
  const currentYear = Number(todayStr.slice(0, 4));
  const [year, setYear] = React.useState(currentYear);
  const [form, setForm] = React.useState<'4' | '3' | '5'>('4');
  const [coverage, setCoverage] = React.useState<{ days: { date: string; count: number }[] } | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [selection, setSelection] = React.useState<CoverageSelection | null>(null);
  const [syncMode, setSyncMode] = React.useState<'COMPANY' | 'FILING_DATE'>('FILING_DATE');

  const loadCoverage = React.useCallback(async (signal?: { cancelled: boolean }) => {
      setLoading(true);
      setError(null);
      try {
        const data = await insiderActivityApi.coverage(form, `${year}-01-01`, `${year}-12-31`);
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
    }, [form, year]);

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

  const selectedTransactions = React.useMemo(() => {
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

  const changeForm = (nextForm: '4' | '3' | '5') => {
    setForm(nextForm);
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
            {`Cached Form ${form} filings per transaction date, shaded by volume. Drag a date range for screener filtering.
            Download is synced by filing date, so there can be up to ~2-day lag for fresh transaction dates.`}
          </p>
          <div className="mt-3 inline-flex rounded-md border border-gray-200">
            {(['4', '3', '5'] as const).map((option) => (
              <button
                type="button"
                key={option}
                onClick={() => changeForm(option)}
                disabled={option !== '4'}
                className={`px-3 py-2 text-sm ${
                  form === option ? 'bg-blue-600 text-white' : 'text-gray-700'
                } ${option === '4' ? 'hover:bg-blue-50' : ''} ${
                  option === '3' ? 'border-l border-gray-200' : ''
                } ${option === '5' ? 'border-l border-gray-200' : ''}`}
                title={option === '4' ? 'Enable Form 4' : 'Coming soon'}
              >
                Form {option}
              </button>
            ))}
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <label htmlFor="insider-coverage-sync-mode" className="text-xs text-gray-600">
              Sync mode
            </label>
            <select
              id="insider-coverage-sync-mode"
              value={syncMode}
              onChange={(event) => setSyncMode(event.target.value as 'COMPANY' | 'FILING_DATE')}
              className="rounded-md border border-gray-200 px-2 py-1 text-xs text-gray-700"
              aria-label="Select sync mode"
            >
              <option value="COMPANY">Company-scoped sync</option>
              <option value="FILING_DATE">Filing-date sync</option>
            </select>
          </div>
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
            onClick={() => void loadCoverage(undefined)}
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
                : `${date} — ${count} Form ${form} filing${count === 1 ? '' : 's'}`,
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

        <div className="mt-4 flex flex-col gap-3 border-t border-gray-100 pt-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-gray-600">
          {selection ? (
            <>
              Selected <span className="font-mono">{selection.start}</span> → <span className="font-mono">{selection.end}</span>
              {' '}({selectedTransactions.toLocaleString()} filing{selectedTransactions === 1 ? '' : 's'} in range)
            </>
          ) : (
            'Drag across the grid to select a transaction-date range.'
          )}
        </p>
        <div className="flex items-center gap-2">
          <button
            onClick={() => selection && onDownload(form, selection.start, selection.end, syncMode)}
            disabled={!selection || downloading}
            className="inline-flex items-center justify-center gap-2 rounded-md bg-blue-600 px-5 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {downloading ? <Loader className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            Download to DB
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
            className="inline-flex items-center justify-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Filter className="h-4 w-4" />
            Filter screener to selection
          </button>
        </div>
      </div>
    </div>
  );
}
