import { apiClient } from '../client';
import { Company, CompanyListItem, CompanySearchRequest, Filing, PaginatedResponse } from '../types';

export const companiesApi = {
  getCompanies: (params: CompanySearchRequest = {}): Promise<PaginatedResponse<CompanyListItem>> => {
    const queryParams = new URLSearchParams();
    if (params.searchTerm) queryParams.append('search', params.searchTerm);
    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortDir) queryParams.append('sortDir', params.sortDir);

    const query = queryParams.toString();
    return apiClient.get<PaginatedResponse<CompanyListItem>>(`/companies${query ? `?${query}` : ''}`);
  },

  getCompanyById: (id: string): Promise<Company> => {
    return apiClient.get<Company>(`/companies/${id}`);
  },

  getCompanyByCik: (cik: string): Promise<Company> => {
    return apiClient.get<Company>(`/companies/cik/${cik}`);
  },

  getCompanyByTicker: (ticker: string): Promise<Company> => {
    return apiClient.get<Company>(`/companies/ticker/${ticker}`);
  },

  getCompanyFilings: (id: string, page: number = 0, size: number = 10): Promise<PaginatedResponse<Filing>> => {
    return apiClient.get<PaginatedResponse<Filing>>(`/companies/${id}/filings?page=${page}&size=${size}`);
  },

  getCompanyFilingsByCik: (cik: string, page: number = 0, size: number = 10): Promise<PaginatedResponse<Filing>> => {
    return apiClient.get<PaginatedResponse<Filing>>(`/companies/cik/${cik}/filings?page=${page}&size=${size}`);
  },
};
