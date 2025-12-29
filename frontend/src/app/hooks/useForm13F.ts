import { useState, useEffect, useCallback } from 'react';
import { form13fApi } from '../api';
import {
  Form13F,
  Form13FHolding,
  FilerSummary,
  HoldingSummary,
  PortfolioSnapshot,
  InstitutionalOwnershipStats,
  HoldingsComparison,
  PaginatedResponse,
} from '../api/types';

export function useForm13F(id: string | undefined) {
  const [form13f, setForm13f] = useState<Form13F | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getById(id)
      .then(setForm13f)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { form13f, loading, error };
}

export function useForm13FByAccession(accessionNumber: string | undefined) {
  const [form13f, setForm13f] = useState<Form13F | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getByAccessionNumber(accessionNumber)
      .then(setForm13f)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { form13f, loading, error };
}

export function useForm13FByCik(cik: string | undefined, page = 0, size = 20) {
  const [filings, setFilings] = useState<PaginatedResponse<Form13F> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getByCik(cik, page, size)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, page, size]);

  return { filings, loading, error };
}

export function useRecentForm13F(limit = 10) {
  const [filings, setFilings] = useState<Form13F[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    form13fApi.getRecentFilings(limit)
      .then(setFilings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [limit]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { filings, loading, error, refresh };
}

export function useForm13FHoldings(accessionNumber: string | undefined) {
  const [holdings, setHoldings] = useState<Form13FHolding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessionNumber) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getHoldings(accessionNumber)
      .then(setHoldings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessionNumber]);

  return { holdings, loading, error };
}

export function useForm13FSearch() {
  const [filings, setFilings] = useState<Form13F[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const searchByFilerName = useCallback(async (name: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13fApi.searchByFilerName(name, page, size);
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
      const data = await form13fApi.getByIssuerName(name, page, size);
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
      const data = await form13fApi.getByCusip(cusip, page, size);
      setFilings(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to search filings');
    } finally {
      setLoading(false);
    }
  }, []);

  const searchByQuarter = useCallback(async (period: string, page = 0, size = 20) => {
    setLoading(true);
    setError(null);
    try {
      const data = await form13fApi.getByQuarter(period, page, size);
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
    searchByFilerName,
    searchByIssuerName,
    searchByCusip,
    searchByQuarter,
  };
}

export function useTopFilers(period: string, limit = 10) {
  const [filers, setFilers] = useState<FilerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!period) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getTopFilers(period, limit)
      .then(setFilers)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [period, limit]);

  return { filers, loading, error };
}

export function useTopHoldings(period: string, limit = 10) {
  const [holdings, setHoldings] = useState<HoldingSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!period) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getTopHoldings(period, limit)
      .then(setHoldings)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [period, limit]);

  return { holdings, loading, error };
}

export function usePortfolioHistory(cik: string | undefined) {
  const [history, setHistory] = useState<PortfolioSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getPortfolioHistory(cik)
      .then(setHistory)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik]);

  return { history, loading, error };
}

export function useInstitutionalOwnership(cusip: string | undefined, period: string) {
  const [stats, setStats] = useState<InstitutionalOwnershipStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cusip || !period) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.getInstitutionalOwnership(cusip, period)
      .then(setStats)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cusip, period]);

  return { stats, loading, error };
}

export function useHoldingsComparison(cik: string | undefined, period1: string, period2: string) {
  const [comparison, setComparison] = useState<HoldingsComparison | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cik || !period1 || !period2) {
      setLoading(false);
      return;
    }

    setLoading(true);
    form13fApi.compareHoldings(cik, period1, period2)
      .then(setComparison)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [cik, period1, period2]);

  return { comparison, loading, error };
}
