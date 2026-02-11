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

export interface RemoteTicker {
  cik: string;
  ticker: string;
  name: string;
  exchange?: string;
}

export interface RemoteSubmissionFiling {
  accessionNumber?: string;
  formType?: string;
  filingDate?: string;
  reportDate?: string;
  primaryDocument?: string;
  primaryDocDescription?: string;
}

export interface RemoteSubmission {
  cik: string;
  companyName: string;
  sic?: string;
  sicDescription?: string;
  tickers?: string[];
  exchanges?: string[];
  recentFilingsCount: number;
  recentFilings: RemoteSubmissionFiling[];
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

// ========== Form 13F Types ==========

export interface Form13FHolding {
  nameOfIssuer: string;
  titleOfClass: string;
  cusip: string;
  figi?: string;
  value: number;
  sharesOrPrincipalAmount: number;
  sharesOrPrincipalAmountType: string;
  putCall?: string;
  investmentDiscretion: string;
  otherManager?: string;
  votingAuthoritySole: number;
  votingAuthorityShared: number;
  votingAuthorityNone: number;
}

export interface Form13F {
  id: string;
  accessionNumber: string;
  cik: string;
  filerName: string;
  businessAddress?: string;
  formType: string;
  filedDate: string;
  reportPeriod: string;
  amendmentType?: string;
  amendmentNumber?: number;
  confidentialTreatment?: boolean;
  reportType?: string;
  holdingsCount: number;
  totalValue: number;
  holdings: Form13FHolding[];
  signatureName?: string;
  signatureTitle?: string;
  signatureDate?: string;
}

export interface FilerSummary {
  id: {
    cik: string;
    filerName: string;
  };
  totalValue: number;
  holdingsCount: number;
}

export interface HoldingSummary {
  id: string; // CUSIP
  issuerName: string;
  totalShares: number;
  totalValue: number;
  filerCount: number;
}

export interface PortfolioSnapshot {
  reportPeriod: string;
  totalValue: number;
  holdingsCount: number;
}

export interface InstitutionalOwnershipStats {
  cusip: string;
  issuerName: string;
  reportPeriod: string;
  institutionCount: number;
  totalShares: number;
  totalValue: number;
  topHolders: TopHolder[];
}

export interface TopHolder {
  cik: string;
  filerName: string;
  shares: number;
  value: number;
}

export interface HoldingsComparison {
  cik: string;
  filerName: string;
  period1: string;
  period2: string;
  newPositions: HoldingChange[];
  closedPositions: HoldingChange[];
  increasedPositions: HoldingChange[];
  decreasedPositions: HoldingChange[];
  totalValueChange: number;
}

export interface HoldingChange {
  cusip: string;
  issuerName: string;
  sharesPeriod1?: number;
  sharesPeriod2?: number;
  valuePeriod1?: number;
  valuePeriod2?: number;
  percentChange?: number;
}

// ========== Form 13D/G Types ==========

export interface Form13DGAddress {
  street1?: string;
  street2?: string;
  city?: string;
  stateOrCountry?: string;
  zipCode?: string;
}

export interface Form13DGReportingPerson {
  name: string;
  cik?: string;
  address?: Form13DGAddress;
  citizenshipOrOrganization?: string;
  reportingPersonTypes?: string[];
  sharesBeneficiallyOwned?: number;
  percentOfClass?: number;
  votingPowerSole?: number;
  votingPowerShared?: number;
  dispositivePowerSole?: number;
  dispositivePowerShared?: number;
}

export interface Form13DG {
  id: string;
  accessionNumber: string;
  formType: string;
  scheduleType: string;
  filedDate: string;
  eventDate: string;
  amendmentNumber?: number;
  amendmentType?: string;
  cusip: string;
  issuerName: string;
  issuerCik?: string;
  securityTitle?: string;
  filingPersonName: string;
  filingPersonCik?: string;
  filingPersonAddress?: Form13DGAddress;
  citizenshipOrOrganization?: string;
  reportingPersonTypes?: string[];
  percentOfClass?: number;
  sharesBeneficiallyOwned?: number;
  votingPowerSole?: number;
  votingPowerShared?: number;
  votingPowerNone?: number;
  dispositivePowerSole?: number;
  dispositivePowerShared?: number;
  purposeOfTransaction?: string;
  sourceOfFunds?: string[];
  filerCategory?: string;
  additionalReportingPersons?: Form13DGReportingPerson[];
  signatureName?: string;
  signatureTitle?: string;
  signatureDate?: string;
}

export interface BeneficialOwnerSummary {
  id: string;
  filingPersonName: string;
  percentOfClass: number;
  sharesBeneficiallyOwned: number;
  latestEventDate: string;
  scheduleType: string;
}

export interface OwnershipHistoryEntry {
  eventDate: string;
  percentOfClass: number;
  sharesBeneficiallyOwned: number;
  formType: string;
  amendmentType?: string;
}

export interface OwnerPortfolioEntry {
  id: string;
  issuerName: string;
  cusip: string;
  percentOfClass: number;
  sharesBeneficiallyOwned: number;
  latestEventDate: string;
  scheduleType: string;
}

export interface BeneficialOwnershipSnapshot {
  cusip: string;
  securityTitle?: string;
  issuerName: string;
  issuerCik?: string;
  beneficialOwners: BeneficialOwnerDetail[];
  totalPercentOwned: number;
  totalSharesOwned: number;
  activistCount: number;
  passiveCount: number;
  asOfDate: string;
}

export interface BeneficialOwnerDetail {
  filingPersonCik: string;
  filingPersonName: string;
  percentOfClass: number;
  sharesBeneficiallyOwned: number;
  eventDate: string;
  scheduleType: string;
  isActivist: boolean;
}

// ========== Form 8-K Types ==========

export interface Form8KItemSection {
  itemNumber: string;
  title: string;
  content?: string;
}

export interface Form8KExhibit {
  exhibitNumber: string;
  description?: string;
  document?: string;
}

export interface Form8K {
  id: string;
  accessionNumber: string;
  cik: string;
  companyName?: string;
  tradingSymbol?: string;
  formType: string;
  filedDate: string;
  reportDate?: string;
  primaryDocument?: string;
  items?: string;
  itemSections?: Form8KItemSection[];
  exhibits?: Form8KExhibit[];
}

// ========== Form 3 & 5 Types (Initial & Annual Ownership) ==========

export interface Form4Transaction {
  accessionNumber?: string;
  transactionType: string; // NON_DERIVATIVE or DERIVATIVE
  securityTitle: string;
  transactionDate?: string;
  transactionCode?: string;
  transactionFormType?: string;
  equitySwapInvolved?: boolean;
  transactionShares?: number;
  transactionPricePerShare?: number;
  transactionValue?: number;
  acquiredDisposedCode?: string; // A = Acquired, D = Disposed
  sharesOwnedFollowingTransaction?: number;
  directOrIndirectOwnership?: string; // D = Direct, I = Indirect
  natureOfOwnership?: string;
  // Derivative-specific fields
  exercisePrice?: number;
  expirationDate?: string;
  underlyingSecurityTitle?: string;
  underlyingSecurityShares?: number;
}

export interface Form3 {
  id: string;
  accessionNumber: string;
  documentType?: string;
  periodOfReport?: string;
  filedDate: string;
  // Issuer information
  cik: string;
  issuerName?: string;
  tradingSymbol?: string;
  // Reporting owner information
  rptOwnerCik?: string;
  rptOwnerName?: string;
  officerTitle?: string;
  isDirector?: boolean;
  isOfficer?: boolean;
  isTenPercentOwner?: boolean;
  isOther?: boolean;
  ownerType?: string;
  // Holdings
  holdings?: Form4Transaction[];
}

export interface Form5 {
  id: string;
  accessionNumber: string;
  documentType?: string;
  periodOfReport?: string;
  filedDate: string;
  // Issuer information
  cik: string;
  issuerName?: string;
  tradingSymbol?: string;
  // Reporting owner information
  rptOwnerCik?: string;
  rptOwnerName?: string;
  officerTitle?: string;
  isDirector?: boolean;
  isOfficer?: boolean;
  isTenPercentOwner?: boolean;
  isOther?: boolean;
  ownerType?: string;
  // Transactions and Holdings
  transactions?: Form4Transaction[];
  holdings?: Form4Transaction[];
}

// ========== Form 6-K Types (Foreign Private Issuer) ==========

export interface Form6KExhibit {
  exhibitNumber: string;
  description?: string;
  document?: string;
}

export interface Form6K {
  id: string;
  accessionNumber: string;
  cik: string;
  companyName?: string;
  tradingSymbol?: string;
  formType: string;
  filedDate: string;
  reportDate?: string;
  primaryDocument?: string;
  reportText?: string;
  exhibits?: Form6KExhibit[];
}

// ========== Form 20-F Types (Foreign Private Issuer Annual Report) ==========

export interface Form20F {
  id: string;
  accessionNumber: string;
  cik: string;
  companyName?: string;
  tradingSymbol?: string;
  securityExchange?: string;
  formType: string;
  filedDate: string;
  reportDate?: string;
  documentPeriodEndDate?: string;
  fiscalYear?: number;
  fiscalPeriod?: string;
  fiscalYearEndDate?: string;
  sharesOutstanding?: number;
  isAmendment?: boolean;
  primaryDocument?: string;
  keyFinancials?: Record<string, number>;
  deiData?: Record<string, string>;
}

// Common 8-K item descriptions
export const FORM_8K_ITEMS: Record<string, string> = {
  '1.01': 'Entry into Material Definitive Agreement',
  '1.02': 'Termination of Material Definitive Agreement',
  '1.03': 'Bankruptcy or Receivership',
  '1.04': 'Mine Safety',
  '1.05': 'Material Cybersecurity Incidents',
  '2.01': 'Completion of Acquisition or Disposition of Assets',
  '2.02': 'Results of Operations and Financial Condition',
  '2.03': 'Creation of Direct Financial Obligation',
  '2.04': 'Triggering Events That Accelerate Obligations',
  '2.05': 'Costs Associated with Exit or Disposal Activities',
  '2.06': 'Material Impairments',
  '3.01': 'Notice of Delisting or Transfer',
  '3.02': 'Unregistered Sales of Equity Securities',
  '3.03': 'Material Modification to Rights of Holders',
  '4.01': 'Changes in Certifying Accountant',
  '4.02': 'Non-Reliance on Previously Issued Financial Statements',
  '5.01': 'Changes in Control of Registrant',
  '5.02': 'Departure/Appointment of Directors or Officers',
  '5.03': 'Amendments to Articles of Incorporation or Bylaws',
  '5.04': 'Temporary Suspension of Trading Under Employee Benefit Plans',
  '5.05': 'Amendment to Code of Ethics',
  '5.06': 'Change in Shell Company Status',
  '5.07': 'Submission of Matters to Vote of Security Holders',
  '5.08': 'Shareholder Nominations',
  '6.01': 'ABS Informational and Computational Material',
  '6.02': 'Change of Servicer or Trustee',
  '6.03': 'Change in Credit Enhancement or External Support',
  '6.04': 'Failure to Make a Distribution',
  '6.05': 'Securities Act Updating Disclosure',
  '7.01': 'Regulation FD Disclosure',
  '8.01': 'Other Events',
  '9.01': 'Financial Statements and Exhibits',
};
