import { apiClient } from '../client';
import { companiesApi } from './companies';
import { dashboardApi } from './dashboard';
import { dividendApi } from './dividend';
import { downloadsApi } from './downloads';
import { exportApi } from './export';
import { filingsApi } from './filings';
import { form20fApi } from './form20f';
import { form3Api } from './form3';
import { form4Api } from './form4';
import { form5Api } from './form5';
import { form6kApi } from './form6k';
import { form8kApi } from './form8k';
import { marketDataApi } from './marketData';
import { remoteEdgarApi } from './remoteEdgar';
import { settingsApi } from './settings';
import { timestampedFilename } from '../../utils/formatters';
import type { ExportRequest } from '../types';

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    downloadFile: vi.fn(),
    downloadGet: vi.fn(),
  },
}));

vi.mock('../../utils/formatters', () => ({
  timestampedFilename: vi.fn((prefix: string, extension: string) => `${prefix}.${extension}`),
}));

const mockedGet = vi.mocked(apiClient.get);
const mockedPost = vi.mocked(apiClient.post);
const mockedPut = vi.mocked(apiClient.put);
const mockedDelete = vi.mocked(apiClient.delete);
const mockedDownloadFile = vi.mocked(apiClient.downloadFile);

describe('coverage API endpoint coverage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('companiesApi', () => {
    it('builds filtered company query strings', async () => {
      mockedGet.mockResolvedValue({} as never);

      await companiesApi.getCompanies({
        searchTerm: 'apple',
        page: 2,
        size: 50,
        sortBy: 'name',
        sortDir: 'asc',
      });

      expect(mockedGet).toHaveBeenCalledWith('/companies?search=apple&page=2&size=50&sortBy=name&sortDir=asc');
      expect(mockedGet).toHaveBeenCalledTimes(1);
    });

    it('omits optional company query values when absent', async () => {
      mockedGet.mockResolvedValue({} as never);

      await companiesApi.getCompanies({ searchTerm: 'berkshire' });

      expect(mockedGet).toHaveBeenCalledWith('/companies?search=berkshire');
    });
  });

  describe('dashboardApi', () => {
    it('loads dashboard primitives', async () => {
      mockedGet.mockResolvedValue({} as never);

      await dashboardApi.getStats();
      await dashboardApi.getRecentSearches();
      await dashboardApi.getRecentFilings(25);

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/dashboard/stats');
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/dashboard/recent-searches?limit=10');
      expect(mockedGet).toHaveBeenNthCalledWith(3, '/dashboard/recent-filings?limit=25');
    });
  });

  describe('dividendApi', () => {
    it('builds encoded dividend URLs and query params', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await dividendApi.getOverview('AAPL');
      await dividendApi.getHistory('MSFT', { metrics: ['cashCoverage', 'interestCoverage'], periods: 'FY', years: 5 });
      await dividendApi.resolveAlert('AAPL', 'alert-1', { action: 'acknowledge', note: 'ok' });
      await dividendApi.compare(['AAPL', 'MSFT'], { metrics: ['cashCoverage'] });

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/dividend/AAPL');
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/dividend/MSFT/history', {
        params: {
          metrics: 'cashCoverage,interestCoverage',
          periods: 'FY',
          years: 5,
        },
      });
      expect(mockedGet).toHaveBeenNthCalledWith(3, '/dividend/compare', {
        params: {
          tickers: 'AAPL,MSFT',
          metrics: 'cashCoverage',
        },
      });
      expect(mockedPost).toHaveBeenCalledWith('/dividend/AAPL/alerts/alert-1/resolve', { action: 'acknowledge', note: 'ok' });
    });

    it('encodes special chars in overview paths', async () => {
      mockedGet.mockResolvedValue({} as never);

      await dividendApi.getOverview('BRK/B');

      expect(mockedGet).toHaveBeenCalledWith('/dividend/BRK%2FB');
    });
  });

  describe('downloadsApi', () => {
    it('posts download request payloads and builds polling routes', async () => {
      mockedPost.mockResolvedValue({} as never);
      mockedGet.mockResolvedValue({} as never);
      mockedDelete.mockResolvedValue({} as never);

      await downloadsApi.downloadTickers();
      await downloadsApi.downloadSubmissions('123456', 'CustomAgent/1.0');
      await downloadsApi.downloadUsaSpendingAwards({ dateFrom: '2026-01-01', dateTo: '2026-01-31' });
      await downloadsApi.getJobs();
      await downloadsApi.getJobById('job-1');
      await downloadsApi.cancelJob('job-1');

      expect(mockedPost).toHaveBeenNthCalledWith(1, '/downloads/tickers?type=TICKERS_ALL');
      expect(mockedPost).toHaveBeenNthCalledWith(2, '/downloads/submissions', {
        type: 'SUBMISSIONS',
        cik: '123456',
        userAgent: 'CustomAgent/1.0',
      });
      expect(mockedPost).toHaveBeenNthCalledWith(3, '/downloads/usaspending/awards', {
        type: 'USA_SPENDING_AWARDS',
        dateFrom: '2026-01-01',
        dateTo: '2026-01-31',
      });
      expect(mockedGet).toHaveBeenNthCalledWith(1, '/downloads/jobs?limit=10');
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/downloads/jobs/job-1');
      expect(mockedDelete).toHaveBeenCalledWith('/downloads/jobs/job-1');
    });

    it('builds remote filings and csv coverage URLs', async () => {
      mockedPost.mockResolvedValue({} as never);
      mockedGet.mockResolvedValue({} as never);

      await downloadsApi.downloadRemoteFilings({
        formType: '8-K',
        dateFrom: '2026-01-01',
        dateTo: '2026-01-31',
      });
      await downloadsApi.getUsaSpendingCsvPage('job-1', 5, 100);
      await downloadsApi.getUsaSpendingCoverage('2026-01-01', '2026-01-31');

      expect(mockedPost).toHaveBeenCalledWith('/downloads/remote-filings', {
        type: 'REMOTE_FILINGS_SYNC',
        formType: '8-K',
        dateFrom: '2026-01-01',
        dateTo: '2026-01-31',
        remoteFilingSyncMode: undefined,
        chunkDays: undefined,
        pauseSeconds: undefined,
        userAgent: undefined,
      });
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/downloads/jobs/job-1/usaspending-csv?page=5&size=100');
      expect(mockedGet).toHaveBeenCalledWith('/downloads/usaspending/coverage?from=2026-01-01&to=2026-01-31');
    });
  });

  describe('exportApi', () => {
    it('triggers CSV download flow with generated filename', async () => {
      const request: ExportRequest = { format: 'CSV', filingIds: ['f1', 'f2'] };
      const url = window.URL as URL & { createObjectURL: any; revokeObjectURL: any };
      const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      const createObjectURL = vi.fn(() => 'blob:mock-csv');
      const revokeObjectURL = vi.fn();
      const appendChild = vi.spyOn(document.body, 'appendChild').mockImplementation(() => null as any);
      const removeChild = vi.spyOn(document.body, 'removeChild').mockImplementation(() => null as any);

      mockedDownloadFile.mockResolvedValue(new Blob(['csv']));

      url.createObjectURL = createObjectURL;
      url.revokeObjectURL = revokeObjectURL;

      await exportApi.exportToCsv(request);

      expect(mockedDownloadFile).toHaveBeenCalledWith('/export/csv', request);
      expect(timestampedFilename).toHaveBeenCalledWith('filings-export', 'csv');
      expect(createObjectURL).toHaveBeenCalledTimes(1);
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-csv');
      expect(clickSpy).toHaveBeenCalledTimes(1);
      expect(appendChild).toHaveBeenCalledTimes(1);
      expect(removeChild).toHaveBeenCalledTimes(1);

      clickSpy.mockRestore();
      appendChild.mockRestore();
      removeChild.mockRestore();
    });

    it('triggers JSON export flow with generated filename', async () => {
      const request: ExportRequest = { format: 'JSON' };
      const url = window.URL as URL & { createObjectURL: any; revokeObjectURL: any };
      const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      const createObjectURL = vi.fn(() => 'blob:mock-json');
      const revokeObjectURL = vi.fn();

      mockedDownloadFile.mockResolvedValue(new Blob(['{}']));
      url.createObjectURL = createObjectURL;
      url.revokeObjectURL = revokeObjectURL;

      await exportApi.exportToJson(request);

      expect(mockedDownloadFile).toHaveBeenCalledWith('/export/json', request);
      expect(timestampedFilename).toHaveBeenCalledWith('filings-export', 'json');
      expect(createObjectURL).toHaveBeenCalledTimes(1);
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-json');
      expect(clickSpy).toHaveBeenCalledTimes(1);

      clickSpy.mockRestore();
    });
  });

  describe('filingsApi', () => {
    it('builds dynamic filing query and search endpoints', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await filingsApi.getFilings({ cik: '123', formType: '8-K', page: 1, size: 20, sortBy: 'filedDate', sortDir: 'desc' });
      await filingsApi.searchFilings({ q: 'search' } as any, { timeout: 2000 });
      await filingsApi.getFilingById('id-1');
      await filingsApi.getRecentFilings();

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/filings?cik=123&formType=8-K&page=1&size=20&sortBy=filedDate&sortDir=desc');
      expect(mockedPost).toHaveBeenCalledWith('/filings/search', { q: 'search' }, { timeout: 2000 });
      expect(mockedGet).toHaveBeenNthCalledWith(3, '/filings/id-1');
      expect(mockedGet).toHaveBeenNthCalledWith(4, '/filings/recent?limit=10');
    });

    it('gets filings by accession number endpoint', async () => {
      mockedGet.mockResolvedValue({} as never);

      await filingsApi.getFilingByAccessionNumber('0000320193-20-000010');

      expect(mockedGet).toHaveBeenCalledWith('/filings/accession/0000320193-20-000010');
    });
  });

  describe('form3Api', () => {
    it('builds form 3 URLs and download query payload', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await form3Api.getById('123');
      await form3Api.getByCik('0000320193', 2, 30);
      await form3Api.downloadAndParse('0000320193', '0000320193-25-000001', 'primary.pdf', 'Acme, Inc.', '2026-01-01');

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/form3/123');
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/form3/cik/0000320193?page=2&size=30');
      expect(mockedPost).toHaveBeenCalledWith(
        '/form3/download?cik=0000320193&accessionNumber=0000320193-25-000001&primaryDocument=primary.pdf&companyName=Acme%2C+Inc.&filedDate=2026-01-01',
        undefined
      );
    });
  });

  describe('form4Api', () => {
    it('builds form 4 symbol/date endpoints', async () => {
      mockedGet.mockResolvedValue({} as never);

      await form4Api.getBySymbolAndDateRange('AAPL', '2026-01-01', '2026-03-31', 1, 20);
      await form4Api.searchByOwner('Acme Holdings');

      expect(mockedGet).toHaveBeenCalledWith('/form4/symbol/AAPL/date-range?startDate=2026-01-01&endDate=2026-03-31&page=1&size=20');
      expect(mockedGet).toHaveBeenCalledWith('/form4/owner?name=Acme%20Holdings');
    });
  });

  describe('form5Api', () => {
    it('builds form 5 CIK and date range routes', async () => {
      mockedGet.mockResolvedValue({} as never);

      await form5Api.getByCik('0000320193', 4, 30);
      await form5Api.getByDateRange('2026-01-01', '2026-01-31', 1, 10);

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/form5/cik/0000320193?page=4&size=30');
      expect(mockedGet).toHaveBeenNthCalledWith(2, '/form5/date-range?startDate=2026-01-01&endDate=2026-01-31&page=1&size=10');
    });
  });

  describe('form6kApi', () => {
    it('builds form 6-K download URL with optional fields', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await form6kApi.downloadAndParse(
        '0000320193',
        '0000320193-25-000001',
        'primary.pdf',
        undefined,
        '2026-01-01',
        '2026-01-31'
      );

      expect(mockedPost).toHaveBeenCalledWith(
        '/form6k/download?cik=0000320193&accessionNumber=0000320193-25-000001&primaryDocument=primary.pdf&filedDate=2026-01-01&reportDate=2026-01-31',
        undefined
      );
    });
  });

  describe('form8kApi', () => {
    it('builds form 8-K endpoints and optional item filter', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await form8kApi.getBySymbol('MSFT', 1, 15);
      await form8kApi.downloadAndParse('0000320193', '0000320193-25-000001', 'doc.pdf', 'Acme & Co', '2026-01-01', '2026-01-31', '5.02');

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/form8k/symbol/MSFT?page=1&size=15');
      expect(mockedPost).toHaveBeenCalledWith(
        '/form8k/download?cik=0000320193&accessionNumber=0000320193-25-000001&primaryDocument=doc.pdf&companyName=Acme+%26+Co&filedDate=2026-01-01&reportDate=2026-01-31&items=5.02',
        undefined
      );
    });
  });

  describe('form20fApi', () => {
    it('builds form 20-F CIK route', async () => {
      mockedGet.mockResolvedValue({} as never);

      await form20fApi.getByAccessionNumber('0000320193-20-000010');

      expect(mockedGet).toHaveBeenCalledWith('/form20f/accession/0000320193-20-000010');
    });
  });

  describe('marketDataApi', () => {
    it('encodes ticker and includes date params', async () => {
      mockedGet.mockResolvedValue({} as never);

      await marketDataApi.getPriceHistory('BRK.B', '2026-01-01', '2026-01-31');

      expect(mockedGet).toHaveBeenCalledWith('/market-data/prices/BRK.B?startDate=2026-01-01&endDate=2026-01-31', {
        timeout: 60000,
      });
    });
  });

  describe('remoteEdgarApi', () => {
    it('builds ticker lookup and remote filing search routes', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPost.mockResolvedValue({} as never);

      await remoteEdgarApi.getTickers({ source: 'all', search: 'AAPL', limit: 25 });
      await remoteEdgarApi.searchFilings({
        formType: '8-K',
        companyName: 'Acme Holdings',
        filingsLimit: undefined,
      } as any);

      expect(mockedGet).toHaveBeenCalledWith('/remote-edgar/tickers?source=all&search=AAPL&limit=25');
      expect(mockedPost).toHaveBeenCalledWith('/remote-edgar/filings/search', {
        formType: '8-K',
        companyName: 'Acme Holdings',
        filingsLimit: undefined,
      });
    });
  });

  describe('settingsApi', () => {
    it('calls settings and health routes', async () => {
      mockedGet.mockResolvedValue({} as never);
      mockedPut.mockResolvedValue({} as never);

      await settingsApi.getSettings();
      await settingsApi.updateSettings({ marketDataProvider: 'fmp' } as any);
      await settingsApi.checkMongoDbHealth();
      await settingsApi.checkElasticsearchHealth();

      expect(mockedGet).toHaveBeenNthCalledWith(1, '/settings');
      expect(mockedPut).toHaveBeenCalledWith('/settings', { marketDataProvider: 'fmp' });
      expect(mockedGet).toHaveBeenNthCalledWith(3, '/settings/health/mongodb');
      expect(mockedGet).toHaveBeenCalledWith('/settings/health/elasticsearch');
    });
  });
});
