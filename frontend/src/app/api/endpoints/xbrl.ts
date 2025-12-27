import { apiClient } from '../client';

// XBRL Types
export interface XbrlSummary {
  documentUri: string;
  format: 'XBRL' | 'INLINE_XBRL' | 'UNKNOWN';
  parseTime: string;
  entityIdentifier: string;
  totalFacts: number;
  totalContexts: number;
  totalUnits: number;
  factsByType: Record<string, number>;
  factsByNamespace: Record<string, number>;
  parseTimeMs: number;
  successRate: number;
  nestedFactsExtracted: number;
  warnings: number;
  errors: number;
}

export interface SecFilingMetadata {
  entityName: string;
  cik: string;
  tradingSymbol: string;
  securityExchange: string;
  formType: string;
  isAmendment: boolean;
  documentPeriodEndDate: string;
  fiscalYear: number;
  fiscalPeriod: string;
  fiscalYearEndDate: string;
  sharesOutstanding: number;
  filingCategory: string;
  deiData: Record<string, string>;
}

export interface FinancialStatement {
  type: 'BALANCE_SHEET' | 'INCOME_STATEMENT' | 'CASH_FLOW' | 'STOCKHOLDERS_EQUITY';
  title: string;
  lineItems: LineItem[];
  periods: ReportingPeriod[];
}

export interface LineItem {
  concept: string;
  label: string;
  indentLevel: number;
  isTotal: boolean;
  isSubtotal: boolean;
  unit: string;
  isMonetary: boolean;
  valuesByPeriod: Record<string, number>;
}

export interface ReportingPeriod {
  contextId: string;
  startDate: string;
  endDate: string;
  isInstant: boolean;
  durationDays: number;
  label: string;
}

export interface FinancialStatements {
  entityName: string;
  cik: string;
  fiscalYearEnd: string;
  periods: ReportingPeriod[];
  balanceSheet: FinancialStatement;
  incomeStatement: FinancialStatement;
  cashFlowStatement: FinancialStatement;
  equityStatement: FinancialStatement;
}

export interface KeyFinancials {
  [concept: string]: number;
}

export interface StandardizedData {
  standardizedValues: Record<string, number>;
  unmappedConcepts: number;
}

export interface CalculationValidation {
  totalChecks: number;
  validCalculations: number;
  errors: number;
  isValid: boolean;
}

export interface ComprehensiveAnalysis {
  summary: XbrlSummary;
  secMetadata: SecFilingMetadata;
  keyFinancials: KeyFinancials;
  standardizedValues: Record<string, number>;
  unmappedConcepts: number;
  calculationValidation: CalculationValidation;
}

export interface XbrlFact {
  concept: string;
  namespace: string;
  fullNamespace: string;
  value: number | string;
  rawValue: string;
  factType: string;
  isNil: boolean;
  contextRef: string;
  contextDescription: string;
  periodEnd: string;
  unitRef: string;
  unitDisplay: string;
  decimals: number;
  scale: number;
}

// XBRL API
export const xbrlApi = {
  // Parse XBRL from URL
  parseFromUrl: (url: string) =>
    apiClient.get<XbrlSummary>(`/xbrl/parse?url=${encodeURIComponent(url)}`),

  // Parse XBRL package from URL
  parsePackageFromUrl: (url: string) =>
    apiClient.get<{
      packageUri: string;
      totalFiles: number;
      instanceFiles: string[];
      totalFacts: number;
      instances: number;
      errors: Record<string, string>;
    }>(`/xbrl/parse-package?url=${encodeURIComponent(url)}`),

  // Get key financial metrics from URL
  getFinancialsFromUrl: (url: string) =>
    apiClient.get<KeyFinancials>(`/xbrl/financials?url=${encodeURIComponent(url)}`),

  // Validate calculations from URL
  validateFromUrl: (url: string) =>
    apiClient.get<CalculationValidation>(`/xbrl/validate?url=${encodeURIComponent(url)}`),

  // Get cache statistics
  getCacheStats: () =>
    apiClient.get<Record<string, number>>('/xbrl/cache/stats'),

  // Clear caches
  clearCache: () =>
    apiClient.post<void>('/xbrl/cache/clear'),

  // NEW: Parse and get comprehensive analysis
  getComprehensiveAnalysis: (url: string) =>
    apiClient.get<ComprehensiveAnalysis>(`/xbrl/analysis?url=${encodeURIComponent(url)}`),

  // NEW: Get reconstructed financial statements
  getStatements: (url: string) =>
    apiClient.get<FinancialStatements>(`/xbrl/statements?url=${encodeURIComponent(url)}`),

  // NEW: Get SEC metadata
  getSecMetadata: (url: string) =>
    apiClient.get<SecFilingMetadata>(`/xbrl/sec-metadata?url=${encodeURIComponent(url)}`),

  // NEW: Export all facts
  exportFacts: (url: string) =>
    apiClient.get<XbrlFact[]>(`/xbrl/facts?url=${encodeURIComponent(url)}`),

  // NEW: Search facts
  searchFacts: (url: string, query: string) =>
    apiClient.get<XbrlFact[]>(`/xbrl/facts/search?url=${encodeURIComponent(url)}&query=${encodeURIComponent(query)}`),
};
