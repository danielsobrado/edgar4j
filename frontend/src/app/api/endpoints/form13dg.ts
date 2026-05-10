import { apiClient } from '../client';
import {
  Form13DG,
  BeneficialOwnerSummary,
  OwnershipHistoryEntry,
  OwnerPortfolioEntry,
  BeneficialOwnershipSnapshot,
  PaginatedResponse,
} from '../types';

function pathSegment(value: string): string {
  return encodeURIComponent(value);
}

function buildQueryString(params: Record<string, string | number | undefined>): string {
  const queryParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      queryParams.set(key, String(value));
    }
  });

  const query = queryParams.toString();
  return query ? `?${query}` : '';
}

function toPaginatedResponse<T>(content: T[], page: number, size: number, fetchedCount: number): PaginatedResponse<T> {
  const safeSize = Math.max(1, size);
  const totalPages = Math.ceil(fetchedCount / safeSize);
  const last = page >= totalPages - 1;

  return {
    content,
    totalElements: fetchedCount,
    totalPages,
    page,
    size: safeSize,
    first: page === 0,
    last,
    hasNext: !last,
    hasPrevious: page > 0,
  };
}

export const form13dgApi = {
  // Get by ID
  getById: (id: string): Promise<Form13DG> => {
    return apiClient.get<Form13DG>(`/form13dg/${pathSegment(id)}`);
  },

  // Get by accession number
  getByAccessionNumber: (accessionNumber: string): Promise<Form13DG> => {
    return apiClient.get<Form13DG>(`/form13dg/accession/${pathSegment(accessionNumber)}`);
  },

  // Get by CUSIP (all filings for a security)
  getByCusip: (cusip: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/cusip/${pathSegment(cusip)}${buildQueryString({ page, size })}`
    );
  },

  // Get by issuer CIK
  getByIssuerCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/issuer/cik/${pathSegment(cik)}${buildQueryString({ page, size })}`
    );
  },

  // Search by issuer name
  searchByIssuerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/issuer${buildQueryString({ name, page, size })}`
    );
  },

  // Get by filer CIK
  getByFilerCik: (cik: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/filer/cik/${pathSegment(cik)}${buildQueryString({ page, size })}`
    );
  },

  // Search by filer name
  searchByFilerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/filer${buildQueryString({ name, page, size })}`
    );
  },

  // Get by schedule type (13D or 13G)
  getByScheduleType: (scheduleType: '13D' | '13G', page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/schedule/${pathSegment(scheduleType)}${buildQueryString({ page, size })}`
    );
  },

  // Get by event date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/event-date-range${buildQueryString({ startDate, endDate, page, size })}`
    );
  },

  // Get recent filings
  getRecentFilings: (limit = 10): Promise<Form13DG[]> => {
    return apiClient.get<Form13DG[]>(`/form13dg/recent${buildQueryString({ limit })}`);
  },

  // Get filings above threshold (e.g., 5% or 10%)
  getAboveThreshold: (threshold: number, page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    return apiClient.get<PaginatedResponse<Form13DG>>(
      `/form13dg/ownership/min${buildQueryString({ minPercent: threshold, page, size })}`
    );
  },

  // Analytics: Get beneficial owners for a security
  getBeneficialOwners: (cusip: string, limit = 10): Promise<BeneficialOwnerSummary[]> => {
    return apiClient.get<BeneficialOwnerSummary[]>(
      `/form13dg/cusip/${pathSegment(cusip)}/top-owners${buildQueryString({ limit })}`
    );
  },

  // Analytics: Get ownership history for a filer on an issuer
  getOwnershipHistory: (filerCik: string, issuerCik: string): Promise<OwnershipHistoryEntry[]> => {
    return apiClient.get<OwnershipHistoryEntry[]>(
      `/form13dg/filer/${pathSegment(filerCik)}/issuer/${pathSegment(issuerCik)}/history`
    );
  },

  // Analytics: Get filer portfolio
  getFilerPortfolio: (filerCik: string): Promise<OwnerPortfolioEntry[]> => {
    return apiClient.get<OwnerPortfolioEntry[]>(`/form13dg/filer/cik/${pathSegment(filerCik)}/portfolio`);
  },

  // Analytics: Get comprehensive ownership snapshot
  getOwnershipSnapshot: (cusip: string): Promise<BeneficialOwnershipSnapshot> => {
    return apiClient.get<BeneficialOwnershipSnapshot>(`/form13dg/cusip/${pathSegment(cusip)}/ownership`);
  },

  // Get activist filings (13D only)
  getActivistFilings: async (page = 0, size = 20): Promise<PaginatedResponse<Form13DG>> => {
    const fetchLimit = Math.min((page + 1) * Math.max(1, size), 100);
    const filings = await apiClient.get<Form13DG[]>(`/form13dg/activist${buildQueryString({ limit: fetchLimit })}`);
    const start = page * size;
    return toPaginatedResponse(filings.slice(start, start + size), page, size, filings.length);
  },

  // Derived from the backend's recent activist filings until a dedicated aggregate endpoint exists.
  getTopActivistInvestors: async (limit = 10): Promise<BeneficialOwnerSummary[]> => {
    const filings = await apiClient.get<Form13DG[]>(`/form13dg/activist${buildQueryString({ limit })}`);
    return filings.map((filing) => ({
      id: filing.filingPersonCik || filing.accessionNumber,
      filingPersonName: filing.filingPersonName || 'Unknown filer',
      percentOfClass: filing.percentOfClass || 0,
      sharesBeneficiallyOwned: filing.sharesBeneficiallyOwned || 0,
      latestEventDate: filing.eventDate || filing.filedDate || '',
      scheduleType: filing.scheduleType || filing.formType || '13D',
    }));
  },
};
