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

const QUARTER_INPUT_PATTERN = /^(\d{4})-Q([1-4])$/i;
const DATE_INPUT_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function buildQueryString(params: Record<string, string | number | undefined>): string {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });
  const query = searchParams.toString();
  return query ? `?${query}` : '';
}

export function normalizeQuarterPeriodInput(period: string): string {
  const trimmed = period.trim();

  if (DATE_INPUT_PATTERN.test(trimmed)) {
    return trimmed;
  }

  const match = QUARTER_INPUT_PATTERN.exec(trimmed);
  if (!match) {
    throw new Error('Quarter must use YYYY-QN or YYYY-MM-DD format');
  }

  const [, year, quarterText] = match;
  const quarter = Number(quarterText);
  const quarterEndMonth = quarter * 3;
  const quarterEndDate = new Date(Date.UTC(Number(year), quarterEndMonth, 0));

  return quarterEndDate.toISOString().slice(0, 10);
}

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
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/cik/${cik}${buildQueryString({ page, size })}`);
  },

  // Search by filer name
  searchByFilerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/filer${buildQueryString({ name, page, size })}`);
  },

  // Get by quarter/report period
  getByQuarter: (period: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    const normalizedPeriod = normalizeQuarterPeriodInput(period);
    return apiClient.get<PaginatedResponse<Form13F>>(
      `/form13f/quarter${buildQueryString({ period: normalizedPeriod, page, size })}`
    );
  },

  // Get by date range
  getByDateRange: (startDate: string, endDate: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(
      `/form13f/date-range${buildQueryString({ startDate, endDate, page, size })}`
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
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/cusip/${cusip}${buildQueryString({ page, size })}`);
  },

  // Search by issuer name
  getByIssuerName: (name: string, page = 0, size = 20): Promise<PaginatedResponse<Form13F>> => {
    return apiClient.get<PaginatedResponse<Form13F>>(`/form13f/issuer${buildQueryString({ name, page, size })}`);
  },

  // Get top filers for a quarter
  getTopFilers: (period: string, limit = 10): Promise<FilerSummary[]> => {
    return apiClient.get<FilerSummary[]>(`/form13f/top-filers${buildQueryString({ period, limit })}`);
  },

  // Get top holdings for a quarter
  getTopHoldings: (period: string, limit = 10): Promise<HoldingSummary[]> => {
    return apiClient.get<HoldingSummary[]>(`/form13f/top-holdings${buildQueryString({ period, limit })}`);
  },

  // Get portfolio history for a CIK
  getPortfolioHistory: (cik: string): Promise<PortfolioSnapshot[]> => {
    return apiClient.get<PortfolioSnapshot[]>(`/form13f/cik/${cik}/history`);
  },

  // Get institutional ownership for a CUSIP
  getInstitutionalOwnership: (cusip: string, period: string): Promise<InstitutionalOwnershipStats> => {
    return apiClient.get<InstitutionalOwnershipStats>(`/form13f/cusip/${cusip}/ownership${buildQueryString({ period })}`);
  },

  // Compare holdings between quarters
  compareHoldings: (cik: string, period1: string, period2: string): Promise<HoldingsComparison> => {
    return apiClient.get<HoldingsComparison>(`/form13f/cik/${cik}/compare${buildQueryString({ period1, period2 })}`);
  },
};
