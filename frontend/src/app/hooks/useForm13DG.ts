import { useState, useEffect, useCallback } from 'react';
import { form13dgApi } from '../api';
import {
  Form13DG,
  BeneficialOwnerSummary,
  OwnershipHistoryEntry,
  OwnerPortfolioEntry,
  BeneficialOwnershipSnapshot,
  PaginatedResponse,
} from '../api/types';

export function useForm13DG(id: string | undefined) {
  const [form13dg, setForm13dg] = useState<Form13DG | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getById(id)
      .then(setForm13dg)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form13dg, loading, error };
}

export function useForm13DGByAccession(accessionNumber: string | undefined) {
  const [form13dg, setForm13dg] = useState<Form13DG | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getByAccessionNumber(accessionNumber)
      .then(setForm13dg)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form13dg, loading, error };
}

export function useRecentForm13DG(limit = 10) {
  const [filings, setFilings] = useState<Form13DG[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form13dgApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm13DGSearch() {
  const [filings, setFilings] = useState<Form13DG[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByFilerName = useCallback(async (name: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.searchByFilerName(name, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByIssuerName = useCallback(async (name: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.searchByIssuerName(name, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByCusip = useCallback(async (cusip: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.getByCusip(cusip, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByScheduleType = useCallback(async (scheduleType: '13D' | '13G', page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.getByScheduleType(scheduleType, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const getActivistFilings = useCallback(async (page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.getActivistFilings(page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to get activist filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const getAboveThreshold = useCallback(async (threshold: number, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13dgApi.getAboveThreshold(threshold, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to get filings above threshold');
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
    searchByFilerName,
    searchByIssuerName,
    searchByCusip,
    searchByScheduleType,
    getActivistFilings,
    getAboveThreshold,
  };
}

export function useBeneficialOwners(cusip: string | undefined) {
  const [owners, setOwners] = useState<BeneficialOwnerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cusip) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getBeneficialOwners(cusip)
      .then(setOwners)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cusip]);

  return { owners, loading, error };
}

export function useOwnershipHistory(cusip: string | undefined, filerCik: string | undefined) {
  const [history, setHistory] = useState<OwnershipHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cusip || !filerCik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getOwnershipHistory(cusip, filerCik)
      .then(setHistory)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cusip, filerCik]);

  return { history, loading, error };
}

export function useFilerPortfolio(filerCik: string | undefined) {
  const [portfolio, setPortfolio] = useState<OwnerPortfolioEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!filerCik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getFilerPortfolio(filerCik)
      .then(setPortfolio)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [filerCik]);

  return { portfolio, loading, error };
}

export function useOwnershipSnapshot(cusip: string | undefined) {
  const [snapshot, setSnapshot] = useState<BeneficialOwnershipSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cusip) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13dgApi.getOwnershipSnapshot(cusip)
      .then(setSnapshot)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cusip]);

  return { snapshot, loading, error };
}

export function useTopActivistInvestors(limit = 10) {
  const [investors, setInvestors] = useState<BeneficialOwnerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    form13dgApi.getTopActivistInvestors(limit)
      .then(setInvestors)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  return { investors, loading, error };
}
