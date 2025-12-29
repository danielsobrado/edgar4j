import { apiClient } from '../client';
import { Form6K, PaginatedResponse } from '../types';

export const form6kApi = {
  // Get by ID
  getById: (id: string): Promise<Form6K> => {
    return apiClient.get<Form6K>(`/form6k/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form6K> => {
    return apiClient.get<Form6K>(`/form6k/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form6K>> => {
    return apiClient.get<PaginatedResponse<Form6K>>(`/form6k/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form6K>> => {
    return apiClient.get<PaginatedResponse<Form6K>>(`/form6k/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form6K>> => {
    return apiClient.get<PaginatedResponse<Form6K>>(
      `/form6k/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form6K[]> => {
    return apiClient.get<Form6K[]>(`/form6k/recent?limit=${limit}`);
  },

  // Download and parse a new Form 6-K
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string,
    companyName?: string,
    filedDate?: string,
    reportDate?: string
  ): Promise<Form6K> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    if (companyName) params.append('companyName', companyName);
    if (filedDate) params.append('filedDate', filedDate);
    if (reportDate) params.append('reportDate', reportDate);

    return apiClient.post<Form6K>(`/form6k/download?${params.toString()}`);
  },
};
