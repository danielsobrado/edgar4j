/**
 * @file TableComponents.tsx
 * @description Shared React components for all SEC filing data tables.
 *
 * Import from here — never duplicate these in individual page components.
 * See frontend/guidelines/TableGuidelines.md for full usage guide.
 */

import React from 'react';
import { TrendingUp, TrendingDown, ArrowUp, ArrowDown } from 'lucide-react';

// ─── BuySellBadge ─────────────────────────────────────────────────────────────

/**
 * Green BUY / Red SELL pill badge.
 *
 * @example <BuySellBadge buy={true} />   // renders green  BUY
 * @example <BuySellBadge buy={false} />  // renders red    SELL
 */
export function BuySellBadge({ buy }: { buy: boolean }) {
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

// ─── SignedAmount ─────────────────────────────────────────────────────────────

/**
 * Coloured text for a signed numeric value (green = positive, red = negative).
 *
 * @example <SignedAmount text="+50,000" positive={true} />
 */
export function SignedAmount({ text, positive }: { text: string; positive: boolean }) {
  return (
    <span className={positive ? 'text-green-700' : 'text-red-700'}>
      {text}
    </span>
  );
}

// ─── TableWrapper ─────────────────────────────────────────────────────────────

/**
 * Horizontally-scrollable wrapper for all data tables.
 * Always wrap your `<table>` in this component.
 *
 * @example
 * <TableWrapper>
 *   <table>…</table>
 * </TableWrapper>
 */
export function TableWrapper({ children }: { children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        {children}
      </table>
    </div>
  );
}

// ─── TableHead ────────────────────────────────────────────────────────────────

export type ColDef = {
  /** Header label — use `\n` for a line-break inside the header cell */
  label: string;
  /** Text alignment for both header and data cells */
  align?: 'left' | 'right' | 'center';
};

/**
 * Standardised `<thead>` block.
 *
 * @example
 * const COLS: ColDef[] = [
 *   { label: 'Date',          align: 'left'  },
 *   { label: 'Total\nAmount', align: 'right' },
 * ];
 * <TableHead cols={COLS} />
 */
export function TableHead({ cols }: { cols: ColDef[] }) {
  return (
    <thead className="bg-gray-50">
      <tr>
        {cols.map(({ label, align = 'left' }) => (
          <th
            key={label}
            className={`px-3 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-pre-line ${
              align === 'right'  ? 'text-right'  :
              align === 'center' ? 'text-center' :
              'text-left'
            }`}
          >
            {label}
          </th>
        ))}
      </tr>
    </thead>
  );
}

// ─── Td helpers ───────────────────────────────────────────────────────────────

/** Standard left-aligned table cell. */
export function Td({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <td className={`px-3 py-3 text-sm text-gray-900 ${className}`}>
      {children}
    </td>
  );
}

/** Right-aligned table cell (for numbers / currencies). */
export function TdRight({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <td className={`px-3 py-3 text-sm text-right text-gray-900 ${className}`}>
      {children}
    </td>
  );
}

// ─── SortableHeader ──────────────────────────────────────────────────────────

/**
 * A single clickable header cell with an asc/desc arrow indicator.
 * Use when you want a client-side sortable column.
 *
 * @example
 * <SortableHeader
 *   label="Date"
 *   field="transactionDate"
 *   currentSort={sort}
 *   onSort={setSort}
 * />
 */
export type SortState<T extends string = string> = {
  field: T;
  direction: 'asc' | 'desc';
};

export function SortableHeader<T extends string>({
  label,
  field,
  currentSort,
  onSort,
  align = 'left',
}: {
  label: string;
  field: T;
  currentSort: SortState<T>;
  onSort: (s: SortState<T>) => void;
  align?: 'left' | 'right';
}) {
  const active = currentSort.field === field;
  const next: SortState<T> = {
    field,
    direction: active && currentSort.direction === 'asc' ? 'desc' : 'asc',
  };
  return (
    <th
      onClick={() => onSort(next)}
      className={`px-3 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer select-none hover:text-gray-700 ${
        align === 'right' ? 'text-right' : 'text-left'
      }`}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        {active ? (
          currentSort.direction === 'asc'
            ? <ArrowUp className="w-3 h-3" />
            : <ArrowDown className="w-3 h-3" />
        ) : (
          <span className="w-3 h-3 opacity-0">↕</span>
        )}
      </span>
    </th>
  );
}

// ─── EmptyRow ─────────────────────────────────────────────────────────────────

/**
 * A full-width "no data" row spanning all columns.
 * Use inside `<tbody>` when there are no results to display.
 */
export function EmptyRow({ colSpan, message = 'No data available' }: {
  colSpan: number;
  message?: string;
}) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-3 py-12 text-center text-sm text-gray-400">
        {message}
      </td>
    </tr>
  );
}
