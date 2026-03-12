import { apiClient } from '../client';
import { Filing, FilingDetail, FilingSearchRequest, PaginatedResponse } from '../types';

export const filingsApi = {
  getFilings: (params: {
    cik?: string;
    formType?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  } = {}): Promise<PaginatedResponse<Filing>> => {
    const queryParams = new URLSearchParams();
    if (params.cik) queryParams.append('cik', params.cik);
    if (params.formType) queryParams.append('formType', params.formType);
    if (params.dateFrom) queryParams.append('dateFrom', params.dateFrom);
    if (params.dateTo) queryParams.append('dateTo', params.dateTo);
    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortDir) queryParams.append('sortDir', params.sortDir);

    const query = queryParams.toString();
    return apiClient.get<PaginatedResponse<Filing>>(`/filings${query ? `?${query}` : ''}`);
  },

  searchFilings: (request: FilingSearchRequest): Promise<PaginatedResponse<Filing>> => {
    return apiClient.post<PaginatedResponse<Filing>>('/filings/search', request);
  },

  getFilingById: (id: string): Promise<FilingDetail> => {
    return apiClient.get<FilingDetail>(`/filings/${id}`);
  },

  getFilingByAccessionNumber: (accessionNumber: string): Promise<FilingDetail> => {
    return apiClient.get<FilingDetail>(`/filings/accession/${accessionNumber}`);
  },

  getRecentFilings: (limit: number = 10): Promise<Filing[]> => {
    return apiClient.get<Filing[]>(`/filings/recent?limit=${limit}`);
  },
};
