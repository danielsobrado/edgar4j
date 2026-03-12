import { apiClient } from '../client';
import {
  Form13DG,
  BeneficialOwnerSummary,
  OwnershipHistoryEntry,
  OwnerPortfolioEntry,
  BeneficialOwnershipSnapshot,
  PaginatedResponse,
} from '../types';

export const form13dgApi = {
  // Get by ID
  getById: (id: string): Promise<Form13DG> => {
    return apiClient.get<Form13DG>(`/form13dg/${id}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form13DG> => {
    return apiClient.get<Form13DG>(`/form13dg/accession/${accessionNumber}`);
  },

  // Get by CUSIP (all filings for a security)
  getByCusip: (cusip: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/cusip/${cusip}?page=${page}&size=${size}`);
  },

  // Get by issuer CIK
  getByIssuerCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/issuer/${cik}?page=${page}&size=${size}`);
  },

  // Search by issuer name
  searchByIssuerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/issuer/search?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);
  },

  // Get by filer CIK
  getByFilerCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/filer/${cik}?page=${page}&size=${size}`);
  },

  // Search by filer name
  searchByFilerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/filer/search?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);
  },

  // Get by schedule type (13D or 13G)
  getByScheduleType: (scheduleType: '13D' | '13G', page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/schedule/${scheduleType}?page=${page}&size=${size}`);
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/date-range?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form13DG[]> => {
    return apiClient.get<Form13DG[]>(`/form13dg/recent?limit=${limit}`);
  },

  // Get filings above threshold (e.g., 5% or 10%)
  getAboveThreshold: (threshold: number, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/threshold/${threshold}?page=${page}&size=${size}`);
  },

  // Analytics: Get beneficial owners for a security
  getBeneficialOwners: (cusip: string): Promise<BeneficialOwnerSummary[]> => {
    return apiClient.get<BeneficialOwnerSummary[]>(`/form13dg/cusip/${cusip}/owners`);
  },

  // Analytics: Get ownership history for a filer on a security
  getOwnershipHistory: (cusip: string, filerCik: string): Promise<OwnershipHistoryEntry[]> => {
    return apiClient.get<OwnershipHistoryEntry[]>(`/form13dg/cusip/${cusip}/filer/${filerCik}/history`);
  },

  // Analytics: Get filer portfolio
  getFilerPortfolio: (filerCik: string): Promise<OwnerPortfolioEntry[]> => {
    return apiClient.get<OwnerPortfolioEntry[]>(`/form13dg/filer/${filerCik}/portfolio`);
  },

  // Analytics: Get comprehensive ownership snapshot
  getOwnershipSnapshot: (cusip: string): Promise<BeneficialOwnershipSnapshot> => {
    return apiClient.get<BeneficialOwnershipSnapshot>(`/form13dg/cusip/${cusip}/snapshot`);
  },

  // Get activist filings (13D only)
  getActivistFilings: (page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(`/form13dg/activist?page=${page}&size=${size}`);
  },

  // Get top activist investors
  getTopActivistInvestors: (limit = 10): Promise<BeneficialOwnerSummary[]> => {
    return apiClient.get<BeneficialOwnerSummary[]>(`/form13dg/top-activists?limit=${limit}`);
  },
};
