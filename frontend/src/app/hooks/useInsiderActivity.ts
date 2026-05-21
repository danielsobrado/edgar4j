import { useCallback, useEffect, useRef, useState } from 'react';
import { insiderActivityApi } from '../api';
import type { InsiderActivity, InsiderActivityFilter, PaginatedResponse } from '../api';

interface InsiderActivityState {
  results: PaginatedResponse<InsiderActivity> | null;
  loading: boolean;
  error: string | null;
}

export function useInsiderActivity(filter: InsiderActivityFilter) {
  const [state, setState] = useState<InsiderActivityState>({
    results: null,
    loading: true,
    error: null,
  });
  const requestIdRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => () => {
    mountedRef.current = false;
  }, []);

  const fetchData = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setState((prev) => ({ ...prev, loading: true, error: null }));

    try {
      const results = await insiderActivityApi.screen(filter);
      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }
      setState({ results, loading: false, error: null });
    } catch (error) {
      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }
      setState((prev) => ({
        ...prev,
        loading: false,
        error: error instanceof Error ? error.message : 'Failed to load insider activity',
      }));
    }
  }, [filter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    ...state,
    refresh: fetchData,
  };
}
