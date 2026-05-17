# Table & Formatting Guidelines

> **Last updated:** March 2026  
> **Applies to:** all SEC-filing data tables in `frontend/src/app/pages/`

---

## 1. Quick-start checklist

When building a new data table page, use **only** these shared utilities — never reinvent them inline.

```
src/app/utils/
├── formatters.ts        ← pure date/number/string helpers
├── TableComponents.tsx  ← shared React components (header, cells, badges)
└── index.ts             ← barrel re-export (import from here)
```

Import everything from the barrel:

```ts
import {
  toDisplayDate, toDisplayDateTime,
  formatNumber, formatCurrency, formatCompact, formatSignedShares,
  BuySellBadge, SignedAmount,
  TableWrapper, TableHead, Td, TdRight,
  type ColDef,
} from '../utils';
```

---

## 2. Date & time formatting

| Function | Input | Output | Notes |
|---|---|---|---|
| `toDisplayDate(raw)` | ISO string / `undefined` | `dd-mm-yyyy` | UTC, European style |
| `toDisplayDateTime(raw)` | ISO string / `undefined` | `dd-mm-yyyy HH:mm` | UTC 24-hour |

**Rules**
- Always use `toDisplayDate` for date-only columns (e.g. *Transaction Date*, *Period of Report*).
- Always use `toDisplayDateTime` for timestamp columns (e.g. *DateTime*, *Created At*).
- Both return `"-"` on null/undefined — never display raw ISO strings in a cell.

```tsx
<Td>{toDisplayDate(filing.transactionDate)}</Td>
<Td>{toDisplayDateTime(filing.updatedAt)}</Td>
```

---

## 3. Number & currency formatting

| Function | Input | Output | Use for |
|---|---|---|---|
| `formatNumber(v)` | number / undefined | `1,234,567` | share counts, quantities |
| `formatCurrency(v)` | number / undefined | `$1,234.56` | prices, dollar amounts |
| `formatCompact(v)` | number / undefined | `1.25M`, `3.5K` | summary cards, totals |
| `formatSignedShares(shares, isBuy)` | number, boolean | `{ text, positive }` | signed trade size column |

**Rules**
- All return `"-"` on null/undefined — never show `NaN` or `0` for missing data.
- Use `formatSignedShares` + `<SignedAmount>` together for signed trade columns.

```tsx
const signed = formatSignedShares(tx.shares, isBuy);
<TdRight>
  <SignedAmount text={signed.text} positive={signed.positive} />
</TdRight>
```

---

## 4. Table structure

Every table follows this exact pattern:

```tsx
<TableWrapper>            {/* overflow-x-auto scroll container */}
  <TableHead cols={COLS} />
  <tbody className="bg-white divide-y divide-gray-100">
    {rows.map(row => (
      <tr key={row.id} className="hover:bg-gray-50 transition-colors">
        <Td>...</Td>
        <TdRight>...</TdRight>
      </tr>
    ))}
  </tbody>
</TableWrapper>
```

### 4.1 Defining columns

```ts
const COLS: ColDef[] = [
  { label: 'Transaction\nDate',  align: 'left'  },  // \n = line break in header
  { label: 'Total\nAmount',      align: 'right' },
  { label: 'Symbol',             align: 'left'  },
];
```

- Left-align: text, names, dates, labels
- Right-align: all numbers, currencies, share counts

### 4.2 Cell components

| Component | When to use |
|---|---|
| `<Td>` | Standard left-aligned text cell |
| `<TdRight>` | Right-aligned number/currency cell |
| `<BuySellBadge buy={bool} />` | Buy/Sell indicator pill (green/red) |
| `<SignedAmount text={...} positive={bool} />` | Coloured ± number text |

---

## 5. Buy / Sell indicators

Use `<BuySellBadge>` in the *Reported* column (or wherever the Buy/Sell sentiment is shown).

```tsx
<Td>
  <div className="flex flex-col gap-1">
    <span>{toDisplayDate(filing.periodOfReport)}</span>
    <BuySellBadge buy={filing.acquiredDisposedCode === 'A'} />
  </div>
</Td>
```

**Determine buy/sell** from `acquiredDisposedCode`: `"A"` = Acquired = Buy, `"D"` = Disposed = Sell.

---

## 6. Page layout structure

Every SEC filing page follows this two-card layout:

```
┌─────────────────────────────────┐
│  Header card                    │
│  - Icon + title + description   │
│  - Search controls              │
└─────────────────────────────────┘
┌─────────────────────────────────┐
│  Results card                   │
│  - "Results — N transactions"   │
│  - <LoadingSpinner> or          │
│  - <EmptyState>    or           │
│  - <FilingsTable>               │
│  - <Pagination>                 │
└─────────────────────────────────┘
```

```tsx
<div className="space-y-6">
  <div className="bg-white rounded-lg shadow-sm p-6">
    {/* search controls */}
  </div>
  {error && <ErrorMessage message={error} />}
  <div className="bg-white rounded-lg shadow-sm p-6">
    {loading  && <LoadingSpinner size="lg" text="Loading…" />}
    {!loading && items.length === 0 && <EmptyState type="search" message="…" />}
    {!loading && items.length > 0 && (
      <>
        <FilingsTable items={items} />
        {totalPages > 1 && <Pagination … />}
      </>
    )}
  </div>
</div>
```

---

## 7. Colour conventions

| Meaning | Tailwind classes |
|---|---|
| Positive / Buy / Gain | `text-green-700` |
| Negative / Sell / Loss | `text-red-700` |
| Primary ticker | `font-semibold text-blue-700` |
| Muted secondary text | `text-gray-500` |
| Table hover row | `hover:bg-gray-50 transition-colors` |

---

## 8. Pagination

Use the shared `<Pagination>` component — never build custom prev/next buttons.

```tsx
<Pagination
  page={currentPage}          // 0-based (Spring uses 0-based pages)
  totalPages={totalPages}
  totalElements={totalElements}
  size={pageSize}
  onPageChange={(p) => { setCurrentPage(p); runSearch(p); }}
  onPageSizeChange={(s) => { setPageSize(s); setCurrentPage(0); runSearch(0); }}
/>
```

---

## 9. Complete example — minimal new form page

```tsx
// frontend/src/app/pages/FormXxx.tsx
import React, { useCallback, useState } from 'react';
import { Search } from 'lucide-react';
import { useFormXxxSearch } from '../hooks';
import { LoadingSpinner, EmptyState, ErrorMessage, Pagination } from '../components/common';
import { toDisplayDate, formatCurrency, BuySellBadge,
         TableWrapper, TableHead, Td, TdRight, type ColDef } from '../utils';

const COLS: ColDef[] = [
  { label: 'Date',   align: 'left'  },
  { label: 'Symbol', align: 'left'  },
  { label: 'Value',  align: 'right' },
];

function DataRow({ item }: { item: FormXxx }) {
  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <Td>{toDisplayDate(item.date)}</Td>
      <td className="px-3 py-3 text-sm font-semibold text-blue-700">{item.symbol}</td>
      <TdRight>{formatCurrency(item.value)}</TdRight>
    </tr>
  );
}

export function FormXxxPage() {
  const [query, setQuery] = useState('');
  const { items, loading, error, totalPages, totalElements, search } = useFormXxxSearch();

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h1 className="text-xl font-semibold mb-4">Form XXX</h1>
        <div className="flex gap-3">
          <input value={query} onChange={e => setQuery(e.target.value)}
            className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm" />
          <button onClick={() => search(query, 0, 20)}
            className="px-5 py-2 bg-[#1a1f36] text-white rounded-md text-sm flex items-center gap-2">
            <Search className="w-4 h-4" /> Search
          </button>
        </div>
      </div>

      {error && <ErrorMessage message={error} />}

      <div className="bg-white rounded-lg shadow-sm p-6">
        {loading && <LoadingSpinner size="lg" text="Loading…" />}
        {!loading && items.length === 0 && <EmptyState type="search" />}
        {!loading && items.length > 0 && (
          <TableWrapper>
            <TableHead cols={COLS} />
            <tbody className="bg-white divide-y divide-gray-100">
              {items.map(item => <DataRow key={item.id} item={item} />)}
            </tbody>
          </TableWrapper>
        )}
      </div>
    </div>
  );
}
```

---

## 10. Anti-patterns — never do these

| ❌ Don't | ✅ Do instead |
|---|---|
| `new Date(s).toLocaleDateString()` in a cell | `toDisplayDate(s)` |
| `$${n.toFixed(2)}` | `formatCurrency(n)` |
| `<span style={{color:'green'}}>...` | `<SignedAmount text={...} positive={true} />` |
| Inline `<table className="...">` | `<TableWrapper><table>...` |
| Inline `<thead>` with manual `<th>` | `<TableHead cols={COLS} />` |
| Defining `BuySellBadge` in the page file | Import from `../utils` |
