import { useState, useEffect, useCallback } from 'react';
import { form5Api } from '../api/endpoints/form5';
import { Form5, PaginatedResponse } from '../api/types';

export function useForm5(id: string | undefined) {
  const [form5, setForm5] = useState<Form5 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form5Api.getById(id)
      .then(setForm5)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form5, loading, error };
}

export function useForm5ByAccession(accessionNumber: string | undefined) {
  const [form5, setForm5] = useState<Form5 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form5Api.getByAccessionNumber(accessionNumber)
      .then(setForm5)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form5, loading, error };
}

export function useForm5ByCik(cik: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form5> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form5Api.getByCik(cik, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, page, size]);

  return { filings, loading, error };
}

export function useForm5BySymbol(symbol: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form5> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form5Api.getBySymbol(symbol, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [symbol, page, size]);

  return { filings, loading, error };
}

export function useRecentForm5(limit = 10) {
  const [filings, setFilings] = useState<Form5[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form5Api.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm5Search() {
  const [filings, setFilings] = useState<Form5[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByCik = useCallback(async (cik: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form5Api.getByCik(cik, page, size);
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
      const data = await form5Api.getBySymbol(symbol, page, size);
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
      const data = await form5Api.getByDateRange(startDate, endDate, page, size);
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
