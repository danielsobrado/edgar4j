import { apiClient } from '../client';
import {
  Form13F,
  Form13FHolding,
  FilerSummary,
  HoldingSummary,
  PortfolioSnapshot,
  InstitutionalOwnershipStats,
  HoldingsComparison,
  PaginatedResponse,
} from '../types';

export const form13fApi = {
  // Get by ID
  getById: (id: string): Promise<Form13F> => {
    return apiClient.get<Form13F>(`/form13f/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form13F> => {
    return apiClient.get<Form13F>(`/form13f/accession/${accessionNumber}`);
  },

  // Get by CIK
  getByCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/cik/${cik}?page=${page}&size=${size}`);
  },

  // Search by filer name
  searchByFilerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/filer?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);
  },

  // Get by quarter/report period
  getByQuarter: (period: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/quarter?period=${period}&page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(
      `/form13f/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form13F[]> => {
    return apiClient.get<Form13F[]>(`/form13f/recent?limit=${limit}`);
  },

  // Get holdings for a filing
  getHoldings: (accessionNumber: string): Promise<Form13FHolding[]> => {
    return apiClient.get<Form13FHolding[]>(`/form13f/accession/${accessionNumber}/holdings`);
  },

  // Search by CUSIP
  getByCusip: (cusip: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/cusip/${cusip}?page=${page}&size=${size}`);
  },

  // Search by issuer name
  getByIssuerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/issuer?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);
  },

  // Get top filers for a quarter
  getTopFilers: (period: string, limit = 10): Promise<FilerSummary[]> => {
    return apiClient.get<FilerSummary[]>(`/form13f/top-filers?period=${period}&limit=${limit}`);
  },

  // Get top holdings for a quarter
  getTopHoldings: (period: string, limit = 10): Promise<HoldingSummary[]> => {
    return apiClient.get<HoldingSummary[]>(`/form13f/top-holdings?period=${period}&limit=${limit}`);
  },

  // Get portfolio history for a CIK
  getPortfolioHistory: (cik: string): Promise<PortfolioSnapshot[]> => {
    return apiClient.get<PortfolioSnapshot[]>(`/form13f/cik/${cik}/history`);
  },

  // Get institutional ownership for a CUSIP
  getInstitutionalOwnership: (cusip: string, period: string): Promise<InstitutionalOwnershipStats> => {
    return apiClient.get<InstitutionalOwnershipStats>(`/form13f/cusip/${cusip}/ownership?period=${period}`);
  },

  // Compare holdings between quarters
  compareHoldings: (cik: string, period1: string, period2: string): Promise<HoldingsComparison> => {
    return apiClient.get<HoldingsComparison>(`/form13f/cik/${cik}/compare?period1=${period1}&period2=${period2}`);
  },
};
