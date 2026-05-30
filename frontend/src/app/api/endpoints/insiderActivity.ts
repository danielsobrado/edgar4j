import { apiClient } from '../client';
import { timestampedFilename } from '../../utils/formatters';
import {
  InsiderActivity,
  InsiderActivityExportFormat,
  InsiderActivityFilter,
  PaginatedResponse,
} from '../types';

export function buildInsiderActivityQuery(filter: InsiderActivityFilter = {}, includePaging = true): string {
  const params = new URLSearchParams();

  setString(params, 'preset', filter.preset);
  setString(params, 'view', filter.view);
  setString(params, 'side', filter.side);
  if (filter.transactionCodes?.length) {
    params.set('transactionCodes', filter.transactionCodes.join(','));
  }
  setString(params, 'dateFrom', filter.dateFrom);
  setString(params, 'dateTo', filter.dateTo);
  setString(params, 'symbol', filter.symbol);
  setNumber(params, 'minPrice', filter.minPrice);
  setNumber(params, 'minShares', filter.minShares);
  setNumber(params, 'minTotalAmount', filter.minTotalAmount);
  setNumber(params, 'minInsiderCount', filter.minInsiderCount);
  setString(params, 'insiderTitle', filter.insiderTitle);
  setString(params, 'sortBy', filter.sortBy);
  setString(params, 'sortDir', filter.sortDir);

  if (includePaging) {
    params.set('page', String(filter.page ?? 0));
    params.set('size', String(filter.size ?? 50));
  }

  return params.toString();
}

export const insiderActivityApi = {
  screen: (filter: InsiderActivityFilter = {}): Promise<PaginatedResponse<InsiderActivity>> => {
    const query = buildInsiderActivityQuery(filter);
    return apiClient.get<PaginatedResponse<InsiderActivity>>(`/insider-activity/screen?${query}`);
  },

  export: async (filter: InsiderActivityFilter = {}, format: InsiderActivityExportFormat): Promise<void> => {
    const query = new URLSearchParams(buildInsiderActivityQuery(filter, false));
    query.set('format', format);
    const blob = await apiClient.downloadGet(`/insider-activity/export?${query.toString()}`);
    downloadBlob(blob, timestampedFilename('insider-activity', format.toLowerCase()));
  },
};

function setString(params: URLSearchParams, key: string, value: string | null | undefined) {
  if (value && value.trim()) {
    params.set(key, value.trim());
  }
}

function setNumber(params: URLSearchParams, key: string, value: number | null | undefined) {
  if (value !== undefined && value !== null && value > 0) {
    params.set(key, String(value));
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
