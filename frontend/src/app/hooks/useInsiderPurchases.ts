import { useCallback, useEffect, useRef, useState } from 'react';
import { insiderPurchasesApi } from '../api';
import {
  InsiderPurchase,
  InsiderPurchaseFilter,
  InsiderPurchaseSummary,
  PaginatedResponse,
} from '../api/types';

interface InsiderPurchasesState {
  purchases: PaginatedResponse<InsiderPurchase> | null;
  summary: InsiderPurchaseSummary | null;
  loading: boolean;
  error: string | null;
}

export function useInsiderPurchases(initialFilter: InsiderPurchaseFilter = {}) {
  const [filter, setFilter] = useState<InsiderPurchaseFilter>({
    lookbackDays: 30,
    sortBy: 'percentChange',
    sortDir: 'desc',
    page: 0,
    size: 50,
    ...initialFilter,
  });

  const [state, setState] = useState<InsiderPurchasesState>({
    purchases: null,
    summary: null,
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
      const [purchases, summary] = await Promise.all([
        insiderPurchasesApi.getInsiderPurchases(filter),
        insiderPurchasesApi.getSummary(filter.lookbackDays ?? 30),
      ]);

      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }

      setState({
        purchases,
        summary,
        loading: false,
        error: null,
      });
    } catch (error) {
      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }

      setState((prev) => ({
        ...prev,
        loading: false,
        error: error instanceof Error ? error.message : 'Failed to load insider purchases',
      }));
    }
  }, [filter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    ...state,
    filter,
    setFilter,
    refresh: fetchData,
  };
}

export function useTopInsiderPurchases(limit: number = 10) {
  const [purchases, setPurchases] = useState<InsiderPurchase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestIdRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => () => {
    mountedRef.current = false;
  }, []);

  const fetchData = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);

    try {
      const data = await insiderPurchasesApi.getTopInsiderPurchases(limit);
      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }
      setPurchases(data);
    } catch (err) {
      if (!mountedRef.current || requestId !== requestIdRef.current) {
        return;
      }
      setError(err instanceof Error ? err.message : 'Failed to load top insider purchases');
    } finally {
      if (mountedRef.current && requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [limit]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    purchases,
    loading,
    error,
    refresh: fetchData,
  };
}
