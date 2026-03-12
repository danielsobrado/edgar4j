/**
 * @file formatters.ts
 * @description Shared formatting utilities for all SEC filing tables.
 *
 * Import from here — never redefine these inline in a page component.
 * See frontend/guidelines/TableGuidelines.md for usage conventions.
 */

// ─── Date / Time ─────────────────────────────────────────────────────────────

/**
 * Format an ISO date string as **dd-mm-yyyy** (UTC, European style).
 *
 * @example toDisplayDate("2026-02-24") → "24-02-2026"
 * @example toDisplayDate(undefined)   → "-"
 */
export function toDisplayDate(raw: string | undefined | null): string {
  if (!raw) return '-';
  try {
    const d = new Date(raw);
    if (isNaN(d.getTime())) return raw;
    const dd   = String(d.getUTCDate()).padStart(2, '0');
    const mm   = String(d.getUTCMonth() + 1).padStart(2, '0');
    const yyyy = d.getUTCFullYear();
    return `${dd}-${mm}-${yyyy}`;
  } catch {
    return raw;
  }
}

/**
 * Format an ISO date string as **dd-mm-yyyy HH:mm** (UTC).
 *
 * @example toDisplayDateTime("2026-02-24T14:30:00Z") → "24-02-2026 14:30"
 */
export function toDisplayDateTime(raw: string | undefined | null): string {
  if (!raw) return '-';
  try {
    const d = new Date(raw);
    if (isNaN(d.getTime())) return raw;
    const date = toDisplayDate(raw);
    const hh  = String(d.getUTCHours()).padStart(2, '0');
    const min = String(d.getUTCMinutes()).padStart(2, '0');
    return `${date} ${hh}:${min}`;
  } catch {
    return raw;
  }
}

// ─── Numbers ─────────────────────────────────────────────────────────────────

/**
 * Format a number with locale-aware thousand separators.
 *
 * @example formatNumber(1234567) → "1,234,567"
 * @example formatNumber(undefined) → "-"
 */
export function formatNumber(v: number | undefined | null): string {
  if (v == null) return '-';
  return v.toLocaleString();
}

/**
 * Format a number as a USD currency string with 2 decimal places.
 *
 * @example formatCurrency(12345.6)  → "$12,345.60"
 * @example formatCurrency(undefined) → "-"
 */
export function formatCurrency(v: number | undefined | null): string {
  if (v == null) return '-';
  return `$${v.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/**
 * Format a number as a compact string (e.g. 1.2M, 5.3K).
 * Useful for large share counts or market cap figures in summary cards.
 *
 * @example formatCompact(1_250_000) → "1.25M"
 * @example formatCompact(3_500)     → "3.5K"
 */
export function formatCompact(v: number | undefined | null): string {
  if (v == null) return '-';
  if (Math.abs(v) >= 1_000_000_000) return `${(v / 1_000_000_000).toFixed(2)}B`;
  if (Math.abs(v) >= 1_000_000)     return `${(v / 1_000_000).toFixed(2)}M`;
  if (Math.abs(v) >= 1_000)         return `${(v / 1_000).toFixed(1)}K`;
  return v.toLocaleString();
}

/**
 * Format a signed share count with a leading +/- and colour hint token.
 * Returns `{ text, positive }` so the caller can apply the right CSS class.
 *
 * @example formatSignedShares(50000, true)  → { text: "+50,000", positive: true  }
 * @example formatSignedShares(50000, false) → { text: "-50,000", positive: false }
 */
export function formatSignedShares(
  shares: number | undefined | null,
  isBuy: boolean
): { text: string; positive: boolean } {
  if (shares == null) return { text: '-', positive: isBuy };
  const abs = Math.abs(shares);
  return {
    text: (isBuy ? '+' : '-') + formatNumber(abs),
    positive: isBuy,
  };
}

// ─── Misc helpers ─────────────────────────────────────────────────────────────

/**
 * Truncate a string at `maxLen` characters and append "…" if needed.
 *
 * @example truncate("Palantir Technologies Inc.", 20) → "Palantir Technologi…"
 */
export function truncate(s: string | undefined | null, maxLen = 40): string {
  if (!s) return '-';
  return s.length > maxLen ? s.slice(0, maxLen - 1) + '…' : s;
}

/**
 * Return the acquiredDisposedCode as a human-readable label.
 *
 * @example acquiredDisposedLabel("A") → "Buy"
 * @example acquiredDisposedLabel("D") → "Sell"
 */
export function acquiredDisposedLabel(code: string | undefined | null): string {
  if (code === 'A') return 'Buy';
  if (code === 'D') return 'Sell';
  return code ?? '-';
}
