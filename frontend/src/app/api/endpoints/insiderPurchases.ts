import { apiClient } from '../client';
import {
  InsiderPurchase,
  InsiderPurchaseFilter,
  InsiderPurchaseSummary,
  PaginatedResponse,
} from '../types';

export function buildInsiderPurchasesQuery(filter: InsiderPurchaseFilter = {}): string {
  const params = new URLSearchParams();

  if (filter.lookbackDays !== undefined && filter.lookbackDays > 0) {
    params.set('lookbackDays', String(filter.lookbackDays));
  }
  if (filter.minMarketCap !== undefined && filter.minMarketCap > 0) {
    params.set('minMarketCap', String(filter.minMarketCap));
  }
  if (filter.sp500Only) {
    params.set('sp500Only', 'true');
  }
  if (filter.minTransactionValue !== undefined && filter.minTransactionValue > 0) {
    params.set('minTransactionValue', String(filter.minTransactionValue));
  }
  if (filter.sortBy) {
    params.set('sortBy', filter.sortBy);
  }
  if (filter.sortDir) {
    params.set('sortDir', filter.sortDir);
  }

  params.set('page', String(filter.page ?? 0));
  params.set('size', String(filter.size ?? 50));

  return params.toString();
}

export const insiderPurchasesApi = {
  getInsiderPurchases: (filter: InsiderPurchaseFilter = {}): Promise<PaginatedResponse<InsiderPurchase>> => {
    const query = buildInsiderPurchasesQuery(filter);
    return apiClient.get<PaginatedResponse<InsiderPurchase>>(`/insider-purchases${query ? `?${query}` : ''}`);
  },

  getTopInsiderPurchases: (limit: number = 10): Promise<InsiderPurchase[]> => {
    return apiClient.get<InsiderPurchase[]>(`/insider-purchases/top?limit=${limit}`);
  },

  getSummary: (lookbackDays: number = 30): Promise<InsiderPurchaseSummary> => {
    return apiClient.get<InsiderPurchaseSummary>(`/insider-purchases/summary?lookbackDays=${lookbackDays}`);
  },
};
