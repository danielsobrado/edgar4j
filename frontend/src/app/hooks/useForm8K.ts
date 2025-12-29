import { useState, useEffect, useCallback } from 'react';
import { form8kApi } from '../api/endpoints/form8k';
import { Form8K, PaginatedResponse } from '../api/types';

export function useForm8K(id: string | undefined) {
  const [form8k, setForm8k] = useState<Form8K | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form8kApi.getById(id)
      .then(setForm8k)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form8k, loading, error };
}

export function useForm8KByAccession(accessionNumber: string | undefined) {
  const [form8k, setForm8k] = useState<Form8K | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form8kApi.getByAccessionNumber(accessionNumber)
      .then(setForm8k)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form8k, loading, error };
}

export function useForm8KByCik(cik: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form8K> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form8kApi.getByCik(cik, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, page, size]);

  return { filings, loading, error };
}

export function useForm8KBySymbol(symbol: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form8K> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form8kApi.getBySymbol(symbol, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [symbol, page, size]);

  return { filings, loading, error };
}

export function useRecentForm8K(limit = 10) {
  const [filings, setFilings] = useState<Form8K[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form8kApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm8KSearch() {
  const [filings, setFilings] = useState<Form8K[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByCik = useCallback(async (cik: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form8kApi.getByCik(cik, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchBySymbol = useCallback(async (symbol: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form8kApi.getBySymbol(symbol, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByDateRange = useCallback(async (startDate: string, endDate: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form8kApi.getByDateRange(startDate, endDate, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
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
  };
}
