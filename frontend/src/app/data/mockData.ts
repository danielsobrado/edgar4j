// Mock data for SEC EDGAR filings dashboard

export interface Company {
  id: string;
  ticker: string;
  name: string;
  cik: string;
  sicCode: string;
  sicDescription: string;
  stateOfIncorporation: string;
  fiscalYearEnd: string;
  businessAddress: string;
  mailingAddress: string;
}

export interface Filing {
  id: string;
  companyName: string;
  companyId: string;
  ticker: string;
  cik: string;
  formType: string;
  filingDate: string;
  reportDate?: string;
  accessionNumber: string;
  primaryDocument: string;
  documentDescription: string;
  wordHits: number;
  isXBRL: boolean;
  isInlineXBRL: boolean;
}

export const companies: Company[] = [
  {
    id: '1',
    ticker: 'AAPL',
    name: 'Apple Inc.',
    cik: '0000320193',
    sicCode: '3571',
    sicDescription: 'Electronic Computers',
    stateOfIncorporation: 'CA',
    fiscalYearEnd: '09-30',
    businessAddress: 'One Apple Park Way, Cupertino, CA 95014',
    mailingAddress: 'One Apple Park Way, Cupertino, CA 95014'
  },
  {
    id: '2',
    ticker: 'MSFT',
    name: 'Microsoft Corporation',
    cik: '0000789019',
    sicCode: '7372',
    sicDescription: 'Services-Prepackaged Software',
    stateOfIncorporation: 'WA',
    fiscalYearEnd: '06-30',
    businessAddress: 'One Microsoft Way, Redmond, WA 98052',
    mailingAddress: 'One Microsoft Way, Redmond, WA 98052'
  },
  {
    id: '3',
    ticker: 'TSLA',
    name: 'Tesla, Inc.',
    cik: '0001318605',
    sicCode: '3711',
    sicDescription: 'Motor Vehicles & Passenger Car Bodies',
    stateOfIncorporation: 'DE',
    fiscalYearEnd: '12-31',
    businessAddress: '1 Tesla Road, Austin, TX 78725',
    mailingAddress: '1 Tesla Road, Austin, TX 78725'
  },
  {
    id: '4',
    ticker: 'GOOGL',
    name: 'Alphabet Inc.',
    cik: '0001652044',
    sicCode: '7370',
    sicDescription: 'Services-Computer Programming, Data Processing, Etc.',
    stateOfIncorporation: 'DE',
    fiscalYearEnd: '12-31',
    businessAddress: '1600 Amphitheatre Parkway, Mountain View, CA 94043',
    mailingAddress: '1600 Amphitheatre Parkway, Mountain View, CA 94043'
  },
  {
    id: '5',
    ticker: 'AMZN',
    name: 'Amazon.com, Inc.',
    cik: '0001018724',
    sicCode: '5961',
    sicDescription: 'Retail-Catalog & Mail-Order Houses',
    stateOfIncorporation: 'DE',
    fiscalYearEnd: '12-31',
    businessAddress: '410 Terry Avenue North, Seattle, WA 98109',
    mailingAddress: 'P.O. Box 81226, Seattle, WA 98108'
  }
];

export const filings: Filing[] = [
  {
    id: '1',
    companyName: 'Apple Inc.',
    companyId: '1',
    ticker: 'AAPL',
    cik: '0000320193',
    formType: '10-K',
    filingDate: '2024-11-02',
    reportDate: '2024-09-28',
    accessionNumber: '0000320193-24-000123',
    primaryDocument: 'aapl-20240928.htm',
    documentDescription: 'Annual Report for Fiscal Year Ended September 28, 2024',
    wordHits: 245,
    isXBRL: true,
    isInlineXBRL: true
  },
  {
    id: '2',
    companyName: 'Microsoft Corporation',
    companyId: '2',
    ticker: 'MSFT',
    cik: '0000789019',
    formType: '10-Q',
    filingDate: '2024-10-25',
    reportDate: '2024-09-30',
    accessionNumber: '0000789019-24-000098',
    primaryDocument: 'msft-20240930.htm',
    documentDescription: 'Quarterly Report for Quarter Ended September 30, 2024',
    wordHits: 187,
    isXBRL: true,
    isInlineXBRL: true
  },
  {
    id: '3',
    companyName: 'Tesla, Inc.',
    companyId: '3',
    ticker: 'TSLA',
    cik: '0001318605',
    formType: '8-K',
    filingDate: '2024-12-15',
    accessionNumber: '0001318605-24-000087',
    primaryDocument: 'tsla-8k-20241215.htm',
    documentDescription: 'Current Report - Material Agreement',
    wordHits: 52,
    isXBRL: false,
    isInlineXBRL: false
  },
  {
    id: '4',
    companyName: 'Alphabet Inc.',
    companyId: '4',
    ticker: 'GOOGL',
    cik: '0001652044',
    formType: '10-K',
    filingDate: '2024-02-02',
    reportDate: '2023-12-31',
    accessionNumber: '0001652044-24-000015',
    primaryDocument: 'goog-20231231.htm',
    documentDescription: 'Annual Report for Fiscal Year Ended December 31, 2023',
    wordHits: 312,
    isXBRL: true,
    isInlineXBRL: true
  },
  {
    id: '5',
    companyName: 'Amazon.com, Inc.',
    companyId: '5',
    ticker: 'AMZN',
    cik: '0001018724',
    formType: 'DEF 14A',
    filingDate: '2024-04-12',
    accessionNumber: '0001018724-24-000042',
    primaryDocument: 'amzn-def14a-20240412.htm',
    documentDescription: 'Definitive Proxy Statement',
    wordHits: 128,
    isXBRL: false,
    isInlineXBRL: false
  },
  {
    id: '6',
    companyName: 'Apple Inc.',
    companyId: '1',
    ticker: 'AAPL',
    cik: '0000320193',
    formType: '8-K',
    filingDate: '2024-11-28',
    accessionNumber: '0000320193-24-000134',
    primaryDocument: 'aapl-8k-20241128.htm',
    documentDescription: 'Current Report - Results of Operations',
    wordHits: 98,
    isXBRL: false,
    isInlineXBRL: false
  },
  {
    id: '7',
    companyName: 'Microsoft Corporation',
    companyId: '2',
    ticker: 'MSFT',
    cik: '0000789019',
    formType: '4',
    filingDate: '2024-12-01',
    accessionNumber: '0000789019-24-000102',
    primaryDocument: 'msft-form4-20241201.xml',
    documentDescription: 'Statement of Changes in Beneficial Ownership',
    wordHits: 15,
    isXBRL: true,
    isInlineXBRL: false
  },
  {
    id: '8',
    companyName: 'Tesla, Inc.',
    companyId: '3',
    ticker: 'TSLA',
    cik: '0001318605',
    formType: '10-Q',
    filingDate: '2024-10-23',
    reportDate: '2024-09-30',
    accessionNumber: '0001318605-24-000076',
    primaryDocument: 'tsla-20240930.htm',
    documentDescription: 'Quarterly Report for Quarter Ended September 30, 2024',
    wordHits: 203,
    isXBRL: true,
    isInlineXBRL: true
  }
];

export const recentSearches = [
  { query: 'AAPL 10-K', timestamp: '2024-12-25 10:30:00' },
  { query: 'Tesla revenue', timestamp: '2024-12-24 15:45:00' },
  { query: 'Microsoft acquisition', timestamp: '2024-12-23 09:15:00' },
  { query: 'Amazon proxy', timestamp: '2024-12-22 14:20:00' },
  { query: 'Google restructuring', timestamp: '2024-12-21 11:00:00' }
];

export const stats = {
  totalFilings: 45782,
  companiesTracked: 8342,
  lastSync: '2024-12-25 08:00:00'
};

export const formTypes = [
  { value: '10-K', label: '10-K (Annual Report)' },
  { value: '10-Q', label: '10-Q (Quarterly Report)' },
  { value: '8-K', label: '8-K (Current Report)' },
  { value: 'DEF 14A', label: 'DEF 14A (Proxy Statement)' },
  { value: '4', label: 'Form 4 (Insider Trading)' },
  { value: 'S-1', label: 'S-1 (IPO Registration)' },
  { value: '13F', label: '13F (Holdings Report)' },
  { value: 'SC 13D', label: 'SC 13D (Beneficial Ownership)' },
  { value: 'SC 13G', label: 'SC 13G (Passive Ownership)' }
];

export const downloadJobs = [
  {
    id: '1',
    type: 'Company Tickers',
    status: 'completed',
    progress: 100,
    timestamp: '2024-12-25 07:00:00',
    size: '2.4 MB'
  },
  {
    id: '2',
    type: 'Bulk Submissions - AAPL',
    status: 'in_progress',
    progress: 67,
    timestamp: '2024-12-25 08:00:00',
    size: '125 MB'
  },
  {
    id: '3',
    type: 'Company Facts',
    status: 'pending',
    progress: 0,
    timestamp: '2024-12-25 08:30:00',
    size: 'TBD'
  }
];
