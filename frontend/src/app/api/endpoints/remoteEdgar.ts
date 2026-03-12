import { apiClient } from '../client';
import { RemoteFilingSearchResult, RemoteSubmission, RemoteTicker } from '../types';

export const remoteEdgarApi = {
  getTickers: (params: {
    source?: 'all' | 'exchanges' | 'mf';
    search?: string;
    limit?: number;
  } = {}): Promise<RemoteTicker[]> => {
    const queryParams = new URLSearchParams();
    if (params.source) queryParams.append('source', params.source);
    if (params.search) queryParams.append('search', params.search);
    if (params.limit !== undefined) queryParams.append('limit', params.limit.toString());
    const query = queryParams.toString();
    return apiClient.get<RemoteTicker[]>(`/remote-edgar/tickers${query ? `?${query}` : ''}`);
  },

  getSubmissionByCik: (cik: string, filingsLimit: number = 50): Promise<RemoteSubmission> => {
    return apiClient.get<RemoteSubmission>(`/remote-edgar/submissions/${encodeURIComponent(cik)}?filingsLimit=${filingsLimit}`);
  },

  searchFilings: (request: {
    formType: string;
    companyName?: string;
    ticker?: string;
    cik?: string;
    dateFrom?: string;
    dateTo?: string;
    limit?: number;
  }): Promise<RemoteFilingSearchResult> => {
    return apiClient.post<RemoteFilingSearchResult>('/remote-edgar/filings/search', request);
  },
};

