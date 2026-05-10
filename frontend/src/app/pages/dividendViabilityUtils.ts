import type {
  DividendAlert,
  DividendEvent,
  DividendFilingEvidence,
  DividendHistoryTrendDirection,
  DividendMetricDefinition,
  DividendOverview,
} from '../api';

export function formatDate(value?: string | null): string {
  if (!value) {
    return '-';
  }

  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    return value;
  }

  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(timestamp));
}

export function formatFiscalYearEnd(value?: string | number | null): string {
  if (value == null) {
    return '-';
  }

  const raw = value.toString().padStart(4, '0');
  const month = Number(raw.slice(0, 2));
  const day = Number(raw.slice(2, 4));
  if (!month || !day) {
    return raw;
  }

  const date = new Date(Date.UTC(2000, month - 1, day));
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(date);
}

export function formatCurrency(value: number | null, maximumFractionDigits: number = 2): string {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits,
  }).format(value);
}

export function formatCompactCurrency(value: number | null): string {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(value);
}

export function formatPercent(value: number | null, maximumFractionDigits: number = 1): string {
  if (value == null) {
    return '-';
  }

  return `${(value * 100).toFixed(maximumFractionDigits)}%`;
}

export function formatMultiple(value: number | null, maximumFractionDigits: number = 2): string {
  if (value == null) {
    return '-';
  }

  return `${value.toFixed(maximumFractionDigits)}x`;
}

export function formatInteger(value: number | null): string {
  if (value == null) {
    return '-';
  }

  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value);
}

export function severityClasses(severity: DividendAlert['severity']): string {
  if (severity === 'HIGH') {
    return 'border-rose-200 bg-rose-50 text-rose-700';
  }
  if (severity === 'MEDIUM') {
    return 'border-amber-200 bg-amber-50 text-amber-700';
  }
  return 'border-slate-200 bg-slate-50 text-slate-700';
}

export function ratingClasses(rating: DividendOverview['viability']['rating']): string {
  if (rating === 'SAFE') {
    return 'bg-emerald-500 text-white';
  }
  if (rating === 'STABLE') {
    return 'bg-sky-500 text-white';
  }
  if (rating === 'WATCH') {
    return 'bg-amber-500 text-slate-950';
  }
  return 'bg-rose-500 text-white';
}

export function historyTrendClasses(trend: DividendHistoryTrendDirection): string {
  if (trend === 'UP') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  }
  if (trend === 'DOWN') {
    return 'border-rose-200 bg-rose-50 text-rose-700';
  }
  if (trend === 'VOLATILE') {
    return 'border-amber-200 bg-amber-50 text-amber-700';
  }
  return 'border-slate-200 bg-slate-50 text-slate-700';
}

export function historyTrendLabel(trend: DividendHistoryTrendDirection): string {
  if (trend === 'INSUFFICIENT_DATA') {
    return 'Insufficient data';
  }
  if (trend === 'VOLATILE') {
    return 'Volatile';
  }
  if (trend === 'UP') {
    return 'Rising';
  }
  if (trend === 'DOWN') {
    return 'Falling';
  }
  return 'Flat';
}

export function eventTypeLabel(eventType: DividendEvent['eventType']): string {
  return eventType.replaceAll('_', ' ');
}

export function eventTypeClasses(eventType: DividendEvent['eventType']): string {
  if (eventType === 'DECLARATION' || eventType === 'REINSTATEMENT') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  }
  if (eventType === 'SPECIAL' || eventType === 'INCREASE') {
    return 'border-sky-200 bg-sky-50 text-sky-700';
  }
  if (eventType === 'POLICY_CHANGE') {
    return 'border-slate-200 bg-slate-100 text-slate-700';
  }
  return 'border-rose-200 bg-rose-50 text-rose-700';
}

export function formatEvidenceTitle(filing?: DividendFilingEvidence['filing'] | null): string {
  if (!filing) {
    return 'Selected filing';
  }

  const formType = filing.formType ?? 'Filing';
  const filedDate = filing.filingDate ? ` filed ${formatDate(filing.filingDate)}` : '';
  return `${formType}${filedDate}`;
}

export function formatMetricValue(
  value: number | null | undefined,
  definition: Pick<DividendMetricDefinition, 'formatHint'>,
): string {
  if (value == null) {
    return '-';
  }

  switch (definition.formatHint) {
    case 'currency':
      return formatCurrency(value);
    case 'compact_currency':
      return formatCompactCurrency(value);
    case 'percent':
      return formatPercent(value);
    case 'multiple':
      return formatMultiple(value);
    case 'count':
      return formatInteger(value);
    case 'score':
      return `${Math.round(value)}/100`;
    default:
      return value.toFixed(2);
  }
}

export function parseOptionalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
