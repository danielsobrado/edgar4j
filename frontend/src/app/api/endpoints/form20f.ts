import { apiClient } from '../client';
import { Form20F, PaginatedResponse } from '../types';

export const form20fApi = {
  // Get by ID
  getById: (id: string): Promise<Form20F> => {
    return apiClient.get<Form20F>(`/form20f/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form20F> => {
    return apiClient.get<Form20F>(`/form20f/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form20F>> => {
    return apiClient.get<PaginatedResponse<Form20F>>(`/form20f/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form20F>> => {
    return apiClient.get<PaginatedResponse<Form20F>>(`/form20f/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form20F>> => {
    return apiClient.get<PaginatedResponse<Form20F>>(
      `/form20f/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form20F[]> => {
    return apiClient.get<Form20F[]>(`/form20f/recent?limit=${limit}`);
  },

  // Download and parse a new Form 20-F
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string,
    companyName?: string,
    filedDate?: string,
    reportDate?: string
  ): Promise<Form20F> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    if (companyName) params.append('companyName', companyName);
    if (filedDate) params.append('filedDate', filedDate);
    if (reportDate) params.append('reportDate', reportDate);

    return apiClient.post<Form20F>(`/form20f/download?${params.toString()}`);
  },
};
