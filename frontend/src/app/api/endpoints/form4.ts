import { apiClient } from '../client';
import { Form4, PaginatedResponse } from '../types';

export const form4Api = {
  // Get by ID
  getById: (id: string): Promise<Form4> => {
    return apiClient.get<Form4>(`/form4/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form4> => {
    return apiClient.get<Form4>(`/form4/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form4>> => {
    return apiClient.get<PaginatedResponse<Form4>>(`/form4/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form4>> => {
    return apiClient.get<PaginatedResponse<Form4>>(`/form4/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form4>> => {
    return apiClient.get<PaginatedResponse<Form4>>(
      `/form4/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get by symbol and date range
  getBySymbolAndDateRange: (
    symbol: string,
    startDate: string,
    endDate: string,
    page = 0,
    size = 20
  ): Promise<PaginatedResponse<Form4>> => {
    return apiClient.get<PaginatedResponse<Form4>>(
      `/form4/symbol/${symbol}/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form4[]> => {
    return apiClient.get<Form4[]>(`/form4/recent?limit=${limit}`);
  },

  // Search by owner name
  searchByOwner: (name: string): Promise<Form4[]> => {
    return apiClient.get<Form4[]>(`/form4/owner?name=${encodeURIComponent(name)}`);
  },

  // Download and parse a new Form 4
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string
  ): Promise<Form4> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    return apiClient.post<Form4>(`/form4/download?${params.toString()}`);
  },
};
