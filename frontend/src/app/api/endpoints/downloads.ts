import { apiClient } from '../client';
import { DownloadJob, DownloadRequest, DownloadType } from '../types';

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

  downloadBulk: (request: DownloadRequest): Promise<DownloadJob> => {
    return apiClient.post<DownloadJob>('/downloads/bulk', request);
  },

  getJobs: (limit: number = 10): Promise<DownloadJob[]> => {
    return apiClient.get<DownloadJob[]>(`/downloads/jobs?limit=${limit}`);
  },

  getActiveJobs: (): Promise<DownloadJob[]> => {
    return apiClient.get<DownloadJob[]>('/downloads/jobs/active');
  },

  getJobById: (id: string): Promise<DownloadJob> => {
    return apiClient.get<DownloadJob>(`/downloads/jobs/${id}`);
  },

  cancelJob: (id: string): Promise<void> => {
    return apiClient.delete<void>(`/downloads/jobs/${id}`);
  },
};
