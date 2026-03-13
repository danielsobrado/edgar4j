import { useCallback, useEffect, useState } from 'react';
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

  const fetchData = useCallback(async () => {
    setState((prev) => ({ ...prev, loading: true, error: null }));

    try {
      const [purchases, summary] = await Promise.all([
        insiderPurchasesApi.getInsiderPurchases(filter),
        insiderPurchasesApi.getSummary(filter.lookbackDays ?? 30),
      ]);

      setState({
        purchases,
        summary,
        loading: false,
        error: null,
      });
    } catch (error) {
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

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await insiderPurchasesApi.getTopInsiderPurchases(limit);
      setPurchases(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load top insider purchases');
    } finally {
      setLoading(false);
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
