import { useState, useEffect, useCallback } from 'react';
import { companiesApi, Company, CompanyListItem, CompanySearchRequest, Filing } from '../api';

export function useCompanies(initialParams: CompanySearchRequest = {}) {
  const [companies, setCompanies] = useState<CompanyListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [params, setParams] = useState<CompanySearchRequest>({
    page: 0,
    size: 20,
    sortBy: 'name',
    sortDir: 'asc',
    ...initialParams,
  });

  const fetchCompanies = useCallback(async (searchParams: CompanySearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const data = await companiesApi.getCompanies(searchParams);
      setCompanies(data?.content ?? []);
      setTotalElements(data?.totalElements ?? 0);
      setTotalPages(data?.totalPages ?? 0);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load companies');
      setCompanies([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCompanies(params);
  }, [params, fetchCompanies]);

  const search = useCallback((searchInput: CompanySearchRequest & { name?: string }) => {
    setParams(prev => ({
      ...prev,
      ...searchInput,
      searchTerm: searchInput.searchTerm ?? searchInput.name ?? prev.searchTerm,
      page: searchInput.page ?? 0,
    }));
  }, []);

  const setPage = useCallback((page: number) => {
    setParams(prev => ({ ...prev, page }));
  }, []);

  const setPageSize = useCallback((size: number) => {
    setParams(prev => ({ ...prev, size, page: 0 }));
  }, []);

  const setSort = useCallback((sortBy: string, sortDir: 'asc' | 'desc') => {
    setParams(prev => ({ ...prev, sortBy, sortDir }));
  }, []);

  return {
    companies,
    loading,
    error,
    totalElements,
    totalPages,
    params,
    search,
    setPage,
    setPageSize,
    setSort,
    refresh: () => fetchCompanies(params),
  };
}

export function useCompany(id: string | undefined) {
  const [company, setCompany] = useState<Company | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    companiesApi.getCompanyById(id)
      .then(setCompany)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { company, loading, error };
}

export function useCompanyByCik(cik: string | undefined) {
  const [company, setCompany] = useState<Company | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    companiesApi.getCompanyByCik(cik)
      .then(setCompany)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik]);

  return { company, loading, error };
}

export function useCompanyFilings() {
  const [filings, setFilings] = useState<Filing[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchByCompany = useCallback(async (cik: string, page: number = 0, size: number = 10) => {
    if (!cik) {
      setFilings([]);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await companiesApi.getCompanyFilingsByCik(cik, page, size);
      setFilings(data?.content ?? []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load company filings');
      setFilings([]);
    } finally {
      setLoading(false);
    }
  }, []);

  return { filings, loading, error, fetchByCompany };
}
