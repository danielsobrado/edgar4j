import { apiClient } from '../client';
import { Form5, PaginatedResponse } from '../types';

export const form5Api = {
  // Get by ID
  getById: (id: string): Promise<Form5> => {
    return apiClient.get<Form5>(`/form5/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form5> => {
    return apiClient.get<Form5>(`/form5/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form5>> => {
    return apiClient.get<PaginatedResponse<Form5>>(`/form5/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form5>> => {
    return apiClient.get<PaginatedResponse<Form5>>(`/form5/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form5>> => {
    return apiClient.get<PaginatedResponse<Form5>>(
      `/form5/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form5[]> => {
    return apiClient.get<Form5[]>(`/form5/recent?limit=${limit}`);
  },

  // Download and parse a new Form 5
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string,
    companyName?: string,
    filedDate?: string
  ): Promise<Form5> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    if (companyName) params.append('companyName', companyName);
    if (filedDate) params.append('filedDate', filedDate);

    return apiClient.post<Form5>(`/form5/download?${params.toString()}`);
  },
};
