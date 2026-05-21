import { useCallback, useEffect, useRef, useState } from 'react';
import { politicalTradesApi } from '../api';
import type { PaginatedResponse, PoliticalTrade, PoliticalTradeFilter } from '../api';

interface PoliticalTradesState {
  results: PaginatedResponse<PoliticalTrade> | null;
  loading: boolean;
  error: string | null;
}

export function usePoliticalTrades(filter: PoliticalTradeFilter) {
  const [state, setState] = useState<PoliticalTradesState>({
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
      const results = await politicalTradesApi.screen(filter);
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
        error: error instanceof Error ? error.message : 'Failed to load political trades',
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
