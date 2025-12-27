import { useState, useEffect, useCallback } from 'react';
import { filingsApi, Filing, FilingDetail, FilingSearchRequest, PaginatedResponse } from '../api';

export function useFilingSearch(initialRequest: FilingSearchRequest = {}) {
  const [filings, setFilings] = useState<PaginatedResponse<Filing> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [request, setRequest] = useState<FilingSearchRequest>({
    page: 0,
    size: 10,
    sortBy: 'filingDate',
    sortDir: 'desc',
    ...initialRequest,
  });

  const search = useCallback(async (searchRequest: FilingSearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const data = await filingsApi.searchFilings(searchRequest);
      setFilings(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const updateRequest = useCallback((updates: Partial<FilingSearchRequest>) => {
    setRequest(prev => {
      const newRequest = { ...prev, ...updates };
      if (updates.page === undefined && Object.keys(updates).some(key => key !== 'page')) {
        newRequest.page = 0;
      }
      return newRequest;
    });
  }, []);

  const setPage = useCallback((page: number) => {
    setRequest(prev => ({ ...prev, page }));
  }, []);

  const setPageSize = useCallback((size: number) => {
    setRequest(prev => ({ ...prev, size, page: 0 }));
  }, []);

  const setSort = useCallback((sortBy: string, sortDir: 'asc' | 'desc') => {
    setRequest(prev => ({ ...prev, sortBy, sortDir }));
  }, []);

  const executeSearch = useCallback(() => {
    search(request);
  }, [search, request]);

  return {
    filings,
    loading,
    error,
    request,
    updateRequest,
    setPage,
    setPageSize,
    setSort,
    search: executeSearch,
  };
}

export function useFiling(id: string | undefined) {
  const [filing, setFiling] = useState<FilingDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    filingsApi.getFilingById(id)
      .then(setFiling)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { filing, loading, error };
}

export function useFilingByAccession(accessionNumber: string | undefined) {
  const [filing, setFiling] = useState<FilingDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    filingsApi.getFilingByAccessionNumber(accessionNumber)
      .then(setFiling)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { filing, loading, error };
}

export function useRecentFilings(limit: number = 10) {
  const [filings, setFilings] = useState<Filing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    filingsApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  return { filings, loading, error };
}

// Hook used by FilingSearch page
export function useFilings() {
  const [filings, setFilings] = useState<Filing[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const search = useCallback(async (request: FilingSearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const data = await filingsApi.searchFilings(request);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const refresh = useCallback(() => {
    // Refresh with current state - caller handles request
  }, []);

  return { filings, loading, error, totalElements, totalPages, search, refresh };
}

// Form types for dropdown
interface FormType {
  code: string;
  name: string;
  description?: string;
}

export function useFormTypes() {
  const [formTypes, setFormTypes] = useState<FormType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Common SEC form types - could be fetched from API
    const commonFormTypes: FormType[] = [
      { code: '10-K', name: 'Annual Report' },
      { code: '10-Q', name: 'Quarterly Report' },
      { code: '8-K', name: 'Current Report' },
      { code: '20-F', name: 'Annual Report (Foreign)' },
      { code: '6-K', name: 'Report of Foreign Issuer' },
      { code: 'S-1', name: 'Registration Statement' },
      { code: 'S-3', name: 'Registration Statement (Shelf)' },
      { code: '4', name: 'Statement of Changes (Insider)' },
      { code: 'DEF 14A', name: 'Proxy Statement' },
      { code: '13F-HR', name: 'Institutional Holdings' },
      { code: '13D', name: 'Beneficial Ownership (>5%)' },
      { code: '13G', name: 'Beneficial Ownership (Passive)' },
      { code: 'SC 13D', name: 'Schedule 13D' },
      { code: 'SC 13G', name: 'Schedule 13G' },
      { code: '424B', name: 'Prospectus' },
    ];
    setFormTypes(commonFormTypes);
    setLoading(false);
  }, []);

  return { formTypes, loading, error };
}
