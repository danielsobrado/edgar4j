// API Response Types

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  path?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

// Dashboard Types
export interface DashboardStats {
  totalFilings: number;
  companiesTracked: number;
  lastSync: string;
  filingsTodayCount: number;
  form4Count: number;
  form10KCount: number;
  form10QCount: number;
}

export interface RecentSearch {
  id: string;
  query: string;
  type: string;
  timestamp: string;
  resultCount: number;
}

// Company Types
export interface Company {
  id: string;
  name: string;
  ticker: string;
  cik: string;
  sic: string;
  sicDescription: string;
  entityType: string;
  stateOfIncorporation: string;
  stateOfIncorporationDescription: string;
  fiscalYearEnd: number;
  ein: string;
  description: string;
  website: string;
  investorWebsite: string;
  category: string;
  tickers: string[];
  exchanges: string[];
  businessAddress?: Address;
  mailingAddress?: Address;
  filingCount: number;
  hasInsiderTransactions: boolean;
}

export interface CompanyListItem {
  id: string;
  name: string;
  ticker: string;
  cik: string;
  sic: string;
  sicDescription: string;
  stateOfIncorporation: string;
  filingCount: number;
}

export interface Address {
  street1: string;
  street2: string;
  city: string;
  stateOrCountry: string;
  zipCode: string;
}

// Filing Types
export interface Filing {
  id: string;
  companyName: string;
  ticker?: string;
  cik: string;
  formType: string;
  formTypeDescription?: string;
  filingDate: string;
  reportDate?: string;
  accessionNumber: string;
  primaryDocument: string;
  primaryDocDescription?: string;
  url?: string;
  isXBRL: boolean;
  isInlineXBRL: boolean;
  wordHits?: number;
}

export interface FilingDetail extends Filing {
  fileNumber?: string;
  filmNumber?: string;
  items?: string;
  contentPreview?: string;
  documentTags?: string[];
}

// Search Request Types
export interface FilingSearchRequest {
  companyName?: string;
  ticker?: string;
  cik?: string;
  formTypes?: string[];
  dateFrom?: string;
  dateTo?: string;
  keywords?: string[];
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

export interface CompanySearchRequest {
  searchTerm?: string;
  ticker?: string;
  cik?: string;
  sic?: string;
  stateOfIncorporation?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

// Download Types
export type DownloadType =
  | 'TICKERS_ALL'
  | 'TICKERS_NYSE'
  | 'TICKERS_NASDAQ'
  | 'TICKERS_MF'
  | 'SUBMISSIONS'
  | 'BULK_SUBMISSIONS'
  | 'BULK_COMPANY_FACTS';

export type JobStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface DownloadRequest {
  type: DownloadType;
  cik?: string;
  userAgent?: string;
}

export interface DownloadJob {
  id: string;
  type: string;
  description: string;
  status: JobStatus;
  progress: number;
  error?: string;
  startedAt: string;
  completedAt?: string;
  filesDownloaded: number;
  totalFiles: number;
  estimatedSize?: string;
}

// Settings Types
export interface Settings {
  userAgent: string;
  autoRefresh: boolean;
  refreshInterval: number;
  darkMode: boolean;
  emailNotifications: boolean;
  apiEndpoints?: ApiEndpointsInfo;
  mongoDbStatus?: ConnectionStatus;
  elasticsearchStatus?: ConnectionStatus;
}

export interface ApiEndpointsInfo {
  baseSecUrl: string;
  submissionsUrl: string;
  edgarArchivesUrl: string;
  companyTickersUrl: string;
}

export interface ConnectionStatus {
  connected: boolean;
  message: string;
  latencyMs: number;
}

export interface SettingsRequest {
  userAgent: string;
  autoRefresh: boolean;
  refreshInterval: number;
  darkMode: boolean;
  emailNotifications: boolean;
}

// Export Types
export type ExportFormat = 'CSV' | 'JSON';

export interface ExportRequest {
  filingIds?: string[];
  searchCriteria?: FilingSearchRequest;
  format: ExportFormat;
}

// Form Types (static data)
export const FORM_TYPES = [
  { value: '10-K', label: '10-K (Annual Report)' },
  { value: '10-Q', label: '10-Q (Quarterly Report)' },
  { value: '8-K', label: '8-K (Current Report)' },
  { value: 'DEF 14A', label: 'DEF 14A (Proxy Statement)' },
  { value: '4', label: 'Form 4 (Insider Trading)' },
  { value: 'S-1', label: 'S-1 (IPO Registration)' },
  { value: '13F', label: '13F (Holdings Report)' },
  { value: 'SC 13D', label: 'SC 13D (Beneficial Ownership)' },
  { value: 'SC 13G', label: 'SC 13G (Passive Ownership)' }
] as const;
