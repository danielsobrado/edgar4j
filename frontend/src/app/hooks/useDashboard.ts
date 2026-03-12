import { useState, useEffect, useCallback } from 'react';
import { dashboardApi, DashboardStats, RecentSearch, Filing } from '../api';

interface DashboardState {
  stats: DashboardStats | null;
  recentSearches: RecentSearch[];
  recentFilings: Filing[];
  loading: boolean;
  error: string | null;
}

export function useDashboard() {
  const [state, setState] = useState<DashboardState>({
    stats: null,
    recentSearches: [],
    recentFilings: [],
    loading: true,
    error: null,
  });

  const fetchDashboardData = useCallback(async () => {
    setState(prev => ({ ...prev, loading: true, error: null }));

    try {
      const [stats, recentSearches, recentFilings] = await Promise.all([
        dashboardApi.getStats(),
        dashboardApi.getRecentSearches(5),
        dashboardApi.getRecentFilings(5),
      ]);

      setState({
        stats,
        recentSearches,
        recentFilings,
        loading: false,
        error: null,
      });
    } catch (error) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error instanceof Error ? error.message : 'Failed to load dashboard data',
      }));
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  return {
    ...state,
    refresh: fetchDashboardData,
  };
}

export function useDashboardStats() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi.getStats()
      .then(setStats)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return { stats, loading, error };
}

export function useRecentSearches(limit: number = 10) {
  const [searches, setSearches] = useState<RecentSearch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi.getRecentSearches(limit)
      .then(setSearches)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  return { searches, loading, error };
}

export function useRecentFilings(limit: number = 10) {
  const [filings, setFilings] = useState<Filing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  return { filings, loading, error };
}
