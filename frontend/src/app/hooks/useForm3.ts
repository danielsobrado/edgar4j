import { useState, useEffect, useCallback } from 'react';
import { form3Api } from '../api/endpoints/form3';
import { Form3, PaginatedResponse } from '../api/types';

export function useForm3(id: string | undefined) {
  const [form3, setForm3] = useState<Form3 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form3Api.getById(id)
      .then(setForm3)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form3, loading, error };
}

export function useForm3ByAccession(accessionNumber: string | undefined) {
  const [form3, setForm3] = useState<Form3 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form3Api.getByAccessionNumber(accessionNumber)
      .then(setForm3)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form3, loading, error };
}

export function useForm3ByCik(cik: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form3> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form3Api.getByCik(cik, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, page, size]);

  return { filings, loading, error };
}

export function useForm3BySymbol(symbol: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form3> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form3Api.getBySymbol(symbol, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [symbol, page, size]);

  return { filings, loading, error };
}

export function useRecentForm3(limit = 10) {
  const [filings, setFilings] = useState<Form3[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form3Api.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm3Search() {
  const [filings, setFilings] = useState<Form3[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByCik = useCallback(async (cik: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form3Api.getByCik(cik, page, size);
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
      const data = await form3Api.getBySymbol(symbol, page, size);
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
      const data = await form3Api.getByDateRange(startDate, endDate, page, size);
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
