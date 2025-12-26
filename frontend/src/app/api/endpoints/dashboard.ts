import { apiClient } from '../client';
import { DashboardStats, RecentSearch, Filing } from '../types';

export const dashboardApi = {
  getStats: (): Promise<DashboardStats> => {
    return apiClient.get<DashboardStats>('/dashboard/stats');
  },

  getRecentSearches: (limit: number = 10): Promise<RecentSearch[]> => {
    return apiClient.get<RecentSearch[]>(`/dashboard/recent-searches?limit=${limit}`);
  },

  getRecentFilings: (limit: number = 10): Promise<Filing[]> => {
    return apiClient.get<Filing[]>(`/dashboard/recent-filings?limit=${limit}`);
  },
};
