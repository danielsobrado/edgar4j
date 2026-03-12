import { apiClient } from '../client';
import { Form3, PaginatedResponse } from '../types';

export const form3Api = {
  // Get by ID
  getById: (id: string): Promise<Form3> => {
    return apiClient.get<Form3>(`/form3/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form3> => {
    return apiClient.get<Form3>(`/form3/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form3>> => {
    return apiClient.get<PaginatedResponse<Form3>>(`/form3/cik/${cik}?page=${page}&size=${size}`);
  },

  // Get by trading symbol
  getBySymbol: (symbol: string, page = 0, size = 20): Promise<PaginatedResponse<Form3>> => {
    return apiClient.get<PaginatedResponse<Form3>>(`/form3/symbol/${symbol}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form3>> => {
    return apiClient.get<PaginatedResponse<Form3>>(
      `/form3/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form3[]> => {
    return apiClient.get<Form3[]>(`/form3/recent?limit=${limit}`);
  },

  // Download and parse a new Form 3
  downloadAndParse: (
    cik: string,
    accessionNumber: string,
    primaryDocument: string,
    companyName?: string,
    filedDate?: string
  ): Promise<Form3> => {
    const params = new URLSearchParams({
      cik,
      accessionNumber,
      primaryDocument,
    });
    if (companyName) params.append('companyName', companyName);
    if (filedDate) params.append('filedDate', filedDate);

    return apiClient.post<Form3>(`/form3/download?${params.toString()}`);
  },
};
