import { vi } from 'vitest';
import type { Form13F, Form13DG, Form8K, Form3, Form5, Form6K, Form4Transaction, PaginatedResponse } from '../../app/api/types';

// Mock Form13F data
export const mockForm13F: Form13F = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  cik: '0001234567',
  filerName: 'Test Investment Fund',
  formType: '13F-HR',
  filedDate: '2024-12-15',
  reportPeriod: '2024-Q4',
  holdingsCount: 50,
  totalValue: 1000000,
  holdings: [
    {
      nameOfIssuer: 'APPLE INC',
      titleOfClass: 'COM',
      cusip: '037833100',
      value: 500000,
      sharesOrPrincipalAmount: 2500,
      sharesOrPrincipalAmountType: 'SH',
      investmentDiscretion: 'SOLE',
      votingAuthoritySole: 2500,
      votingAuthorityShared: 0,
      votingAuthorityNone: 0,
    },
    {
      nameOfIssuer: 'MICROSOFT CORP',
      titleOfClass: 'COM',
      cusip: '594918104',
      value: 500000,
      sharesOrPrincipalAmount: 1200,
      sharesOrPrincipalAmountType: 'SH',
      investmentDiscretion: 'SOLE',
      votingAuthoritySole: 1200,
      votingAuthorityShared: 0,
      votingAuthorityNone: 0,
    },
  ],
};

export const mockForm13FList: Form13F[] = [mockForm13F];

export const mockForm13FPaginated: PaginatedResponse<Form13F> = {
  content: mockForm13FList,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// Mock Form13DG data
export const mockForm13DG: Form13DG = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  formType: 'SC 13D',
  scheduleType: '13D',
  filedDate: '2024-12-15',
  eventDate: '2024-12-10',
  cusip: '037833100',
  issuerName: 'ACME CORPORATION',
  issuerCik: '0009876543',
  securityTitle: 'Common Stock',
  filingPersonName: 'Activist Capital Partners',
  filingPersonCik: '0001234567',
  percentOfClass: 7.5,
  sharesBeneficiallyOwned: 6000000,
  votingPowerSole: 5000000,
  votingPowerShared: 1000000,
  dispositivePowerSole: 5000000,
  dispositivePowerShared: 1000000,
  purposeOfTransaction: 'Investment purposes',
};

export const mockForm13DGList: Form13DG[] = [mockForm13DG];

export const mockForm13DGPaginated: PaginatedResponse<Form13DG> = {
  content: mockForm13DGList,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// Mock Form8K data
export const mockForm8K: Form8K = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  cik: '0001234567',
  companyName: 'ACME CORPORATION',
  tradingSymbol: 'ACME',
  formType: '8-K',
  filedDate: '2024-12-15',
  reportDate: '2024-12-14',
  primaryDocument: 'd123456d8k.htm',
  items: '2.02, 9.01',
  itemSections: [
    {
      itemNumber: '2.02',
      title: 'Results of Operations and Financial Condition',
      content: 'The company announced quarterly earnings...',
    },
  ],
  exhibits: [
    {
      exhibitNumber: '99.1',
      description: 'Press Release',
      document: 'ex99-1.htm',
    },
  ],
};

export const mockForm8KList: Form8K[] = [mockForm8K];

export const mockForm8KPaginated: PaginatedResponse<Form8K> = {
  content: mockForm8KList,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// Mock Form4Transaction data (used by Form3 and Form5)
export const mockForm4Transaction: Form4Transaction = {
  transactionType: 'NON_DERIVATIVE',
  securityTitle: 'Common Stock',
  transactionDate: '2024-12-15',
  transactionCode: 'P',
  transactionShares: 1000,
  transactionPricePerShare: 150.00,
  acquiredDisposedCode: 'A',
  sharesOwnedFollowingTransaction: 10000,
  directOrIndirectOwnership: 'D',
};

// Mock Form3 data
export const mockForm3: Form3 = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  documentType: '3',
  periodOfReport: '2024-12-15',
  filedDate: '2024-12-15',
  cik: '0001234567',
  issuerName: 'ACME CORPORATION',
  tradingSymbol: 'ACME',
  rptOwnerCik: '0009876543',
  rptOwnerName: 'John Doe',
  officerTitle: 'Chief Executive Officer',
  isDirector: true,
  isOfficer: true,
  isTenPercentOwner: false,
  isOther: false,
  ownerType: 'Officer',
  holdings: [mockForm4Transaction],
};

export const mockForm3List: Form3[] = [mockForm3];

export const mockForm3Paginated: PaginatedResponse<Form3> = {
  content: mockForm3List,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// Mock Form5 data
export const mockForm5: Form5 = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  documentType: '5',
  periodOfReport: '2024-12-31',
  filedDate: '2025-02-15',
  cik: '0001234567',
  issuerName: 'ACME CORPORATION',
  tradingSymbol: 'ACME',
  rptOwnerCik: '0009876543',
  rptOwnerName: 'Jane Smith',
  officerTitle: 'Chief Financial Officer',
  isDirector: false,
  isOfficer: true,
  isTenPercentOwner: false,
  isOther: false,
  ownerType: 'Officer',
  transactions: [mockForm4Transaction],
  holdings: [mockForm4Transaction],
};

export const mockForm5List: Form5[] = [mockForm5];

export const mockForm5Paginated: PaginatedResponse<Form5> = {
  content: mockForm5List,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// Mock Form6K data
export const mockForm6K: Form6K = {
  id: '1',
  accessionNumber: '0001234567-24-000001',
  cik: '0001234567',
  companyName: 'Foreign Corp Ltd',
  tradingSymbol: 'FCRP',
  formType: '6-K',
  filedDate: '2024-12-15',
  reportDate: '2024-12-14',
  primaryDocument: 'd123456d6k.htm',
  reportText: 'This is a report from a foreign private issuer...',
  exhibits: [
    {
      exhibitNumber: '99.1',
      description: 'Press Release',
      document: 'ex99-1.htm',
    },
  ],
};

export const mockForm6KList: Form6K[] = [mockForm6K];

export const mockForm6KPaginated: PaginatedResponse<Form6K> = {
  content: mockForm6KList,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

// API mock functions
export const createApiMock = () => ({
  form13fApi: {
    getById: vi.fn().mockResolvedValue(mockForm13F),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm13F),
    getByCik: vi.fn().mockResolvedValue(mockForm13FPaginated),
    searchByFilerName: vi.fn().mockResolvedValue(mockForm13FPaginated),
    getByQuarter: vi.fn().mockResolvedValue(mockForm13FPaginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm13FList),
    getHoldings: vi.fn().mockResolvedValue(mockForm13F.holdings),
    getByCusip: vi.fn().mockResolvedValue(mockForm13FPaginated),
    getByIssuerName: vi.fn().mockResolvedValue(mockForm13FPaginated),
    getTopFilers: vi.fn().mockResolvedValue([]),
    getTopHoldings: vi.fn().mockResolvedValue([]),
    getPortfolioHistory: vi.fn().mockResolvedValue([]),
    getInstitutionalOwnership: vi.fn().mockResolvedValue(null),
    compareHoldings: vi.fn().mockResolvedValue(null),
  },
  form13dgApi: {
    getById: vi.fn().mockResolvedValue(mockForm13DG),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm13DG),
    getByCusip: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getByIssuerCik: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    searchByIssuerName: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getByFilerCik: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    searchByFilerName: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getByScheduleType: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getByDateRange: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm13DGList),
    getAboveThreshold: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getBeneficialOwners: vi.fn().mockResolvedValue([]),
    getOwnershipHistory: vi.fn().mockResolvedValue([]),
    getFilerPortfolio: vi.fn().mockResolvedValue([]),
    getOwnershipSnapshot: vi.fn().mockResolvedValue(null),
    getActivistFilings: vi.fn().mockResolvedValue(mockForm13DGPaginated),
    getTopActivistInvestors: vi.fn().mockResolvedValue([]),
  },
  form8kApi: {
    getById: vi.fn().mockResolvedValue(mockForm8K),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm8K),
    getByCik: vi.fn().mockResolvedValue(mockForm8KPaginated),
    getBySymbol: vi.fn().mockResolvedValue(mockForm8KPaginated),
    getByDateRange: vi.fn().mockResolvedValue(mockForm8KPaginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm8KList),
    downloadAndParse: vi.fn().mockResolvedValue(mockForm8K),
  },
  form3Api: {
    getById: vi.fn().mockResolvedValue(mockForm3),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm3),
    getByCik: vi.fn().mockResolvedValue(mockForm3Paginated),
    getBySymbol: vi.fn().mockResolvedValue(mockForm3Paginated),
    getByDateRange: vi.fn().mockResolvedValue(mockForm3Paginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm3List),
    downloadAndParse: vi.fn().mockResolvedValue(mockForm3),
  },
  form5Api: {
    getById: vi.fn().mockResolvedValue(mockForm5),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm5),
    getByCik: vi.fn().mockResolvedValue(mockForm5Paginated),
    getBySymbol: vi.fn().mockResolvedValue(mockForm5Paginated),
    getByDateRange: vi.fn().mockResolvedValue(mockForm5Paginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm5List),
    downloadAndParse: vi.fn().mockResolvedValue(mockForm5),
  },
  form6kApi: {
    getById: vi.fn().mockResolvedValue(mockForm6K),
    getByAccessionNumber: vi.fn().mockResolvedValue(mockForm6K),
    getByCik: vi.fn().mockResolvedValue(mockForm6KPaginated),
    getBySymbol: vi.fn().mockResolvedValue(mockForm6KPaginated),
    getByDateRange: vi.fn().mockResolvedValue(mockForm6KPaginated),
    getRecentFilings: vi.fn().mockResolvedValue(mockForm6KList),
    downloadAndParse: vi.fn().mockResolvedValue(mockForm6K),
  },
});
