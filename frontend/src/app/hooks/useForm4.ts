import { useState, useCallback } from 'react';
import { form4Api, SpringPage } from '../api/endpoints/form4';
import { Form4 } from '../api/types';

export function useForm4Search() {
  const [filings, setFilings] = useState<Form4[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const handlePage = (data: SpringPage<Form4>) => {
    setFilings(data.content ?? []);
    setTotalElements(data.totalElements ?? 0);
    setTotalPages(data.totalPages ?? 0);
  };

  const searchByCik = useCallback(async (cik: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      handlePage(await form4Api.getByCik(cik, page, size));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
      setFilings([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  }, []);

  const searchBySymbol = useCallback(async (symbol: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      handlePage(await form4Api.getBySymbol(symbol, page, size));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
      setFilings([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByDateRange = useCallback(async (startDate: string, endDate: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      handlePage(await form4Api.getByDateRange(startDate, endDate, page, size));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
      setFilings([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  }, []);

  const searchBySymbolAndDateRange = useCallback(async (
    symbol: string,
    startDate: string,
    endDate: string,
    page = 0,
    size = 20
  ) => {
    setLoading(true);
    setError(null);
    try {
      handlePage(await form4Api.getBySymbolAndDateRange(symbol, startDate, endDate, page, size));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
      setFilings([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    filings,
    loading,
    error,
    totalElements,
    totalPages,
    searchByCik,
    searchBySymbol,
    searchByDateRange,
    searchBySymbolAndDateRange,
  };
}
