import { useState, useEffect, useCallback } from 'react';
import { companiesApi, Company, CompanyListItem, CompanySearchRequest, Filing, PaginatedResponse } from '../api';

export function useCompanies(initialParams: CompanySearchRequest = {}) {
  const [companies, setCompanies] = useState<PaginatedResponse<CompanyListItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [params, setParams] = useState<CompanySearchRequest>(initialParams);

  const fetchCompanies = useCallback(async (searchParams: CompanySearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const data = await companiesApi.getCompanies(searchParams);
      setCompanies(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load companies');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCompanies(params);
  }, [params, fetchCompanies]);

  const search = useCallback((searchTerm: string) => {
    setParams(prev => ({ ...prev, searchTerm, page: 0 }));
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

export function useCompanyFilings(companyId: string | undefined, page: number = 0, size: number = 10) {
  const [filings, setFilings] = useState<PaginatedResponse<Filing> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!companyId) {
      setLoading(false);
      return;
    }

    setLoading(true);
    companiesApi.getCompanyFilings(companyId, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [companyId, page, size]);

  return { filings, loading, error };
}
