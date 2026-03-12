import { apiClient } from '../client';
import { Form8K, PaginatedResponse } from '../types';

export const form8kApi = {
  // Get by ID
  getById: (id: string): Promise<Form8K> => {
    return apiClient.get<Form8K>(`/form8k/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form8K> => {
    return apiClient.get<Form8K>(`/form8k/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form8K>> => {
    return apiClient.get<PaginatedResponse<Form8K>>(`/form8k/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form8K>> => {
    return apiClient.get<PaginatedResponse<Form8K>>(`/form8k/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form8K>> => {
    return apiClient.get<PaginatedResponse<Form8K>>(
      `/form8k/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form8K[]> => {
    return apiClient.get<Form8K[]>(`/form8k/recent?limit=${limit}`);
  },

  // Download and parse a new 8-K
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string,
    companyName?: string,
    filedDate?: string,
    reportDate?: string,
    items?: string
  ): Promise<Form8K> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    if (companyName) params.append('companyName', companyName);
    if (filedDate) params.append('filedDate', filedDate);
    if (reportDate) params.append('reportDate', reportDate);
    if (items) params.append('items', items);

    return apiClient.post<Form8K>(`/form8k/download?${params.toString()}`);
  },
};
