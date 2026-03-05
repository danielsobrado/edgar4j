import { apiClient } from '../client';
import { Form4 } from '../types';

/**
 * Spring Data Page response shape (returned directly by the backend, no ApiResponse wrapper).
 */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (Spring uses "number" not "page")
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export const form4Api = {
  // Get by ID
  getById: (id: string): Promise<Form4> => {
    return apiClient.getRaw<Form4>(`/form4/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form4> => {
    return apiClient.getRaw<Form4>(`/form4/accession/${accessionNumber}`);
  },

  // Get by CIK  — Spring returns Page<Form4> directly
  getByCik: (cik: string, page = 0, size = 20): Promise<SpringPage<Form4>> => {
    return apiClient.getRaw<SpringPage<Form4>>(`/form4/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol — Spring returns Page<Form4> directly
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<SpringPage<Form4>> => {
    return apiClient.getRaw<SpringPage<Form4>>(`/form4/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range — Spring returns Page<Form4> directly
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<SpringPage<Form4>> => {
    return apiClient.getRaw<SpringPage<Form4>>(
      `/form4/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get by symbol and date range — Spring returns Page<Form4> directly
  getBySymbolAndDateRange: (
    symbol: string,
    startDate: string,
    endDate: string,
    page = 0,
    size = 20
  ): Promise<SpringPage<Form4>> => {
    return apiClient.getRaw<SpringPage<Form4>>(
      `/form4/symbol/${symbol}/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings — Spring returns List<Form4> directly
  getRecentFilings: (limit = 10): Promise<Form4[]> => {
    return apiClient.getRaw<Form4[]>(`/form4/recent?limit=${limit}`);
  },

  // Search by owner name
  searchByOwner: (name: string): Promise<Form4[]> => {
    return apiClient.getRaw<Form4[]>(`/form4/owner?name=${encodeURIComponent(name)}`);
  },

  // Download and parse a new Form 4
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string
  ): Promise<Form4> => {
    const params = new URLSearchParams({ cik, accessionNumber, primaryDocument });
    return apiClient.getRaw<Form4>(`/form4/download?${params.toString()}`);
  },
};
