import { useState, useEffect, useCallback } from 'react';
import { form6kApi } from '../api/endpoints/form6k';
import { Form6K, PaginatedResponse } from '../api/types';

export function useForm6K(id: string | undefined) {
  const [form6k, setForm6k] = useState<Form6K | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form6kApi.getById(id)
      .then(setForm6k)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form6k, loading, error };
}

export function useForm6KByAccession(accessionNumber: string | undefined) {
  const [form6k, setForm6k] = useState<Form6K | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form6kApi.getByAccessionNumber(accessionNumber)
      .then(setForm6k)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form6k, loading, error };
}

export function useForm6KByCik(cik: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form6K> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form6kApi.getByCik(cik, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, page, size]);

  return { filings, loading, error };
}

export function useForm6KBySymbol(symbol: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form6K> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form6kApi.getBySymbol(symbol, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [symbol, page, size]);

  return { filings, loading, error };
}

export function useRecentForm6K(limit = 10) {
  const [filings, setFilings] = useState<Form6K[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form6kApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm6KSearch() {
  const [filings, setFilings] = useState<Form6K[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByCik = useCallback(async (cik: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form6kApi.getByCik(cik, page, size);
      setFilings(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 0);
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
      const data = await form6kApi.getBySymbol(symbol, page, size);
      setFilings(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 0);
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
      const data = await form6kApi.getByDateRange(startDate, endDate, page, size);
      setFilings(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 0);
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
