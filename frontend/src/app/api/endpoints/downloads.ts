import { apiClient } from '../client';
import { DownloadJob, DownloadRequest, DownloadSummary, DownloadType, UsaSpendingCoverage, UsaSpendingCsvPage } from '../types';

export const downloadsApi = {
  downloadTickers: (type: DownloadType = 'TICKERS_ALL'): Promise<DownloadJob> => {
    return apiClient.post<DownloadJob>(`/downloads/tickers?type=${type}`);
  },

  downloadSubmissions: (cik: string, userAgent?: string): Promise<DownloadJob> => {
    const request: DownloadRequest = {
      type: 'SUBMISSIONS',
      cik,
      userAgent,
    };
    return apiClient.post<DownloadJob>('/downloads/submissions', request);
  },

  downloadRemoteFilings: (request: {
    formType: string;
    dateFrom: string;
    dateTo: string;
    remoteFilingSyncMode?: 'COMPANY' | 'FILING_DATE';
    chunkDays?: number;
    pauseSeconds?: number;
    userAgent?: string;
  }): Promise<DownloadJob> => {
    const payload: DownloadRequest = {
      type: 'REMOTE_FILINGS_SYNC',
      formType: request.formType,
      dateFrom: request.dateFrom,
      dateTo: request.dateTo,
      remoteFilingSyncMode: request.remoteFilingSyncMode,
      chunkDays: request.chunkDays,
      pauseSeconds: request.pauseSeconds,
      userAgent: request.userAgent,
    };
    return apiClient.post<DownloadJob>('/downloads/remote-filings', payload);
  },

  downloadUsaSpendingAwards: (request: {
    dateFrom: string;
    dateTo: string;
  }): Promise<DownloadJob> => {
    const payload: DownloadRequest = {
      type: 'USA_SPENDING_AWARDS',
      dateFrom: request.dateFrom,
      dateTo: request.dateTo,
    };
    return apiClient.post<DownloadJob>('/downloads/usaspending/awards', payload);
  },

  downloadBulk: (request: DownloadRequest): Promise<DownloadJob> => {
    return apiClient.post<DownloadJob>('/downloads/bulk', request);
  },

  getJobs: (limit: number = 10): Promise<DownloadJob[]> => {
    return apiClient.get<DownloadJob[]>(`/downloads/jobs?limit=${limit}`);
  },

  getActiveJobs: (): Promise<DownloadJob[]> => {
    return apiClient.get<DownloadJob[]>('/downloads/jobs/active');
  },

  getSummary: (): Promise<DownloadSummary> => {
    return apiClient.get<DownloadSummary>('/downloads/summary');
  },

  getJobById: (id: string): Promise<DownloadJob> => {
    return apiClient.get<DownloadJob>(`/downloads/jobs/${id}`);
  },

  getUsaSpendingCsvPage: (id: string, page: number = 0, size: number = 25): Promise<UsaSpendingCsvPage> => {
    return apiClient.get<UsaSpendingCsvPage>(`/downloads/jobs/${id}/usaspending-csv?page=${page}&size=${size}`);
  },

  getUsaSpendingCoverage: (from: string, to: string): Promise<UsaSpendingCoverage> => {
    return apiClient.get<UsaSpendingCoverage>(`/downloads/usaspending/coverage?from=${from}&to=${to}`);
  },

  cancelJob: (id: string): Promise<void> => {
    return apiClient.delete<void>(`/downloads/jobs/${id}`);
  },
};
