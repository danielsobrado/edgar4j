import React from 'react';
import { format } from 'date-fns';
import { ExternalLink, LineChart, RefreshCw, Search } from 'lucide-react';

import type { DividendComparison, DividendEvents, DividendHistory } from '../api';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/ui/button';
import {
  eventTypeClasses,
  eventTypeLabel,
  formatCurrency,
  formatDate,
  formatMetricValue,
  ratingClasses,
} from './dividendViabilityUtils';

type ComparisonState =
  | { status: 'idle' }
  | { status: 'loading'; peers: string[] }
  | { status: 'error'; peers: string[]; message: string }
  | { status: 'success'; peers: string[]; result: DividendComparison };

export function DividendEventsSection({
  dividendEvents,
  openEvidence,
}: {
  dividendEvents: DividendEvents['events'];
  openEvidence: (accessionNumber?: string | null) => Promise<void>;
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Dividend events</p>
          <h2 className="mt-2 text-xl font-semibold text-slate-950">Filing-text event timeline</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
            These entries now come from `/api/dividend/.../events` and represent regex-extracted dividend
            declarations, special payouts, reinstatements, cuts, and policy language from recent filing text.
          </p>
        </div>
        <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
          {dividendEvents.length} extracted event{dividendEvents.length === 1 ? '' : 's'}
        </div>
      </div>

      <div className="mt-5 space-y-4">
        {dividendEvents.length === 0 ? (
          <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
            No dividend events were extracted from the currently available filing text for this company.
          </div>
        ) : dividendEvents.slice(0, 6).map((event) => (
          <div key={event.id} className="rounded-[26px] border border-slate-200 bg-slate-50 px-4 py-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] ${eventTypeClasses(event.eventType)}`}>
                    {eventTypeLabel(event.eventType)}
                  </span>
                  {event.amountPerShare != null && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700">
                      {formatCurrency(event.amountPerShare, 4)} / share
                    </span>
                  )}
                  {event.dividendType && event.dividendType !== 'UNKNOWN' && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700">
                      {event.dividendType.toLowerCase()}
                    </span>
                  )}
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-700">
                  {event.textSnippet || event.policyLanguage || 'No snippet was extracted for this event.'}
                </p>
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                  <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                    Filed {formatDate(event.filedDate)}
                  </span>
                  {event.declarationDate && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                      Declared {formatDate(event.declarationDate)}
                    </span>
                  )}
                  {event.recordDate && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                      Record {formatDate(event.recordDate)}
                    </span>
                  )}
                  {event.payableDate && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                      Payable {formatDate(event.payableDate)}
                    </span>
                  )}
                  {event.sourceSection && (
                    <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1">
                      {event.sourceSection}
                    </span>
                  )}
                </div>
              </div>
              <div className="flex shrink-0 flex-col items-start gap-2 lg:items-end">
                <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-700">
                  {event.confidence}
                </span>
                {event.accessionNumber && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => void openEvidence(event.accessionNumber)}
                    className="rounded-full"
                  >
                    View evidence
                  </Button>
                )}
                {event.url && (
                  <a
                    href={event.url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-100"
                  >
                    Source filing
                    <ExternalLink className="h-4 w-4" />
                  </a>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export function DividendPeerComparisonSection({
  compareInput,
  setCompareInput,
  runComparison,
  comparisonState,
  comparisonResult,
  companyIdentifier,
  metrics,
}: {
  compareInput: string;
  setCompareInput: React.Dispatch<React.SetStateAction<string>>;
  runComparison: (event: React.FormEvent<HTMLFormElement>) => Promise<void>;
  comparisonState: ComparisonState;
  comparisonResult: DividendComparison | null;
  companyIdentifier: string;
  metrics: readonly string[];
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Peer comparison</p>
          <h2 className="mt-2 text-xl font-semibold text-slate-950">Compare this company against selected peers</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
            This view uses the new `/api/dividend/compare` endpoint to line up payout pressure, dividend growth,
            leverage, rating, and score across multiple companies.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {metrics.map((metric) => (
            <span key={metric} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium uppercase tracking-[0.12em] text-slate-600">
              {metric.replaceAll('_', ' ')}
            </span>
          ))}
        </div>
      </div>

      <form onSubmit={(event) => void runComparison(event)} className="mt-5 flex flex-col gap-3 lg:flex-row lg:items-center">
        <label className="sr-only" htmlFor="dividend-peer-input">Peer tickers</label>
        <div className="flex min-w-[280px] flex-1 items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2">
          <Search className="h-4 w-4 text-slate-400" />
          <input
            id="dividend-peer-input"
            value={compareInput}
            onChange={(event) => setCompareInput(event.target.value)}
            placeholder="MSFT, JNJ, PG"
            className="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
          />
        </div>
        <Button type="submit" variant="outline" className="rounded-2xl">
          {comparisonState.status === 'loading' ? <RefreshCw className="h-4 w-4 animate-spin" /> : <LineChart className="h-4 w-4" />}
          Compare peers
        </Button>
      </form>

      <div className="mt-5">
        {comparisonState.status === 'idle' && (
          <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
            Add one or more peer tickers to compare against {companyIdentifier}.
          </div>
        )}

        {comparisonState.status === 'loading' && (
          <div className="rounded-3xl border border-slate-200 bg-slate-50 px-4 py-8">
            <LoadingSpinner size="sm" text={`Comparing ${comparisonState.peers.join(', ')}...`} />
          </div>
        )}

        {comparisonState.status === 'error' && (
          <div className="rounded-3xl border border-rose-200 bg-rose-50 px-4 py-4 text-sm text-rose-700">
            {comparisonState.message}
          </div>
        )}

        {comparisonState.status === 'success' && comparisonResult && (
          <div className="space-y-4">
            {comparisonResult.warnings.length > 0 && (
              <div className="rounded-3xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
                {comparisonResult.warnings.map((warning) => (
                  <p key={warning}>{warning}</p>
                ))}
              </div>
            )}

            <div className="overflow-x-auto rounded-3xl border border-slate-200">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Company</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Rating</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Score</th>
                    {comparisonResult.metrics.map((metric) => (
                      <th key={metric.id} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                        {metric.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 bg-white">
                  {comparisonResult.companies.map((row) => (
                    <tr key={row.company.cik}>
                      <td className="px-4 py-4 align-top">
                        <div>
                          <p className="text-sm font-semibold text-slate-950">{row.company.ticker || row.company.cik}</p>
                          <p className="mt-1 text-xs text-slate-500">{row.company.name || row.company.cik}</p>
                        </div>
                      </td>
                      <td className="px-4 py-4 align-top">
                        <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${ratingClasses(row.viability.rating)}`}>
                          {row.viability.rating}
                        </span>
                      </td>
                      <td className="px-4 py-4 align-top text-sm font-semibold text-slate-950">{row.viability.score}/100</td>
                      {comparisonResult.metrics.map((metric) => (
                        <td key={`${row.company.cik}-${metric.id}`} className="px-4 py-4 align-top text-sm text-slate-700">
                          {formatMetricValue(row.values[metric.id], metric)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

export function DividendHistoryTableSection({
  historyRows,
  columns,
}: {
  historyRows: DividendHistory['rows'];
  columns: ReadonlyArray<{ id: string; label: string; formatHint: string }>;
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">Annual history table</p>
          <h2 className="mt-2 text-xl font-semibold text-slate-950">Multi-metric annual dividend history</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
            The table uses the richer history response to show dividend progression, payout strain, free cash
            flow support, and leverage/liquidity metrics in one annual view.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
            {historyRows.length} row{historyRows.length === 1 ? '' : 's'}
          </span>
          <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-600">
            {columns.length} tracked columns
          </span>
        </div>
      </div>

      <div className="mt-5 overflow-x-auto rounded-3xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Year</th>
              {columns.map((column) => (
                <th key={column.id} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 bg-white">
            {historyRows.length === 0 ? (
              <tr>
                <td colSpan={columns.length + 1} className="px-4 py-6 text-sm text-slate-600">
                  No annual history rows are available yet.
                </td>
              </tr>
            ) : historyRows.map((row) => {
              const yearLabel = row.periodEnd
                ? format(new Date(row.periodEnd), 'yyyy')
                : (row.filingDate ? format(new Date(row.filingDate), 'yyyy') : '-');

              return (
                <tr key={row.accessionNumber ?? `${row.periodEnd}-${row.filingDate}`}>
                  <td className="px-4 py-4 align-top">
                    <div>
                      <p className="text-sm font-semibold text-slate-950">{yearLabel}</p>
                      <p className="mt-1 text-xs text-slate-500">{formatDate(row.periodEnd ?? row.filingDate)}</p>
                    </div>
                  </td>
                  {columns.map((column) => (
                    <td key={`${row.accessionNumber ?? yearLabel}-${column.id}`} className="px-4 py-4 align-top text-sm text-slate-700">
                      {formatMetricValue(
                        row.metrics[column.id],
                        { formatHint: column.formatHint },
                      )}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
