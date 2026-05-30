import { apiClient } from '../client';
import { timestampedFilename } from '../../utils/formatters';
import {
  PaginatedResponse,
  PoliticalTrade,
  PoliticalTradeCoverage,
  PoliticalTradeExportFormat,
  PoliticalTradeFilter,
  PoliticalTradeSyncRequest,
  PoliticalTradeSyncResponse,
} from '../types';

export function buildPoliticalTradesQuery(filter: PoliticalTradeFilter = {}, includePaging = true): string {
  const params = new URLSearchParams();

  setString(params, 'politician', filter.politician);
  setString(params, 'ticker', filter.ticker);
  setString(params, 'issuer', filter.issuer);
  setString(params, 'party', filter.party);
  setString(params, 'chamber', filter.chamber);
  setString(params, 'state', filter.state);
  setString(params, 'assetType', filter.assetType);
  setString(params, 'transactionType', filter.transactionType);
  setString(params, 'owner', filter.owner);
  setString(params, 'tradedDateFrom', filter.tradedDateFrom);
  setString(params, 'tradedDateTo', filter.tradedDateTo);
  setString(params, 'disclosureDateFrom', filter.disclosureDateFrom);
  setString(params, 'disclosureDateTo', filter.disclosureDateTo);
  setNumber(params, 'minAmount', filter.minAmount);
  setNumber(params, 'maxAmount', filter.maxAmount);
  setString(params, 'sortBy', filter.sortBy);
  setString(params, 'sortDir', filter.sortDir);

  if (includePaging) {
    params.set('page', String(filter.page ?? 0));
    params.set('size', String(filter.size ?? 50));
  }

  return params.toString();
}

export function buildPoliticalTradesSyncQuery(request: PoliticalTradeSyncRequest = {}): string {
  const params = new URLSearchParams();

  setString(params, 'assetType', request.assetType);
  setNumber(params, 'maxPages', request.maxPages);

  if (request.force) {
    params.set('force', 'true');
  }

  return params.toString();
}

export const politicalTradesApi = {
  screen: (filter: PoliticalTradeFilter = {}): Promise<PaginatedResponse<PoliticalTrade>> => {
    const query = buildPoliticalTradesQuery(filter);
    return apiClient.get<PaginatedResponse<PoliticalTrade>>(`/political-trades/screen?${query}`);
  },

  export: async (filter: PoliticalTradeFilter = {}, format: PoliticalTradeExportFormat): Promise<void> => {
    const query = new URLSearchParams(buildPoliticalTradesQuery(filter, false));
    query.set('format', format);
    const blob = await apiClient.downloadGet(`/political-trades/export?${query.toString()}`);
    downloadBlob(blob, timestampedFilename('political-trades', format.toLowerCase()));
  },

  coverage: (from: string, to: string): Promise<PoliticalTradeCoverage> => {
    return apiClient.get<PoliticalTradeCoverage>(`/political-trades/coverage?from=${from}&to=${to}`);
  },

  sync: (request: PoliticalTradeSyncRequest = {}): Promise<PoliticalTradeSyncResponse> => {
    const query = buildPoliticalTradesSyncQuery(request);
    return apiClient.post<PoliticalTradeSyncResponse>(`/political-trades/sync${query ? `?${query}` : ''}`);
  },

  politicians: (query = '', limit = 100): Promise<string[]> => {
    const params = new URLSearchParams();
    setString(params, 'query', query);
    setNumber(params, 'limit', limit);
    return apiClient.get<string[]>(`/political-trades/politicians?${params.toString()}`);
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
