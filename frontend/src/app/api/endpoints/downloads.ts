import { apiClient } from '../client';
import { DownloadJob, DownloadRequest, DownloadSummary, DownloadType } from '../types';

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
    userAgent?: string;
  }): Promise<DownloadJob> => {
    const payload: DownloadRequest = {
      type: 'REMOTE_FILINGS_SYNC',
      formType: request.formType,
      dateFrom: request.dateFrom,
      dateTo: request.dateTo,
      userAgent: request.userAgent,
    };
    return apiClient.post<DownloadJob>('/downloads/remote-filings', payload);
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

  cancelJob: (id: string): Promise<void> => {
    return apiClient.delete<void>(`/downloads/jobs/${id}`);
  },
};
