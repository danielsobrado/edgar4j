import { useState, useCallback } from 'react';
import {
  xbrlApi,
  XbrlSummary,
  SecFilingMetadata,
  FinancialStatements,
  KeyFinancials,
  ComprehensiveAnalysis,
  XbrlFact,
  CalculationValidation,
} from '../api';

/**
 * Hook for parsing XBRL from a URL and getting summary
 */
export function useXbrlParse() {
  const [data, setData] = useState<XbrlSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const parse = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.parseFromUrl(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to parse XBRL';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, parse };
}

/**
 * Hook for getting comprehensive XBRL analysis
 */
export function useXbrlAnalysis() {
  const [data, setData] = useState<ComprehensiveAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const analyze = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.getComprehensiveAnalysis(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to analyze XBRL';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, analyze };
}

/**
 * Hook for getting reconstructed financial statements
 */
export function useFinancialStatements() {
  const [data, setData] = useState<FinancialStatements | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.getStatements(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load statements';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}

/**
 * Hook for getting SEC filing metadata
 */
export function useSecMetadata() {
  const [data, setData] = useState<SecFilingMetadata | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.getSecMetadata(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load SEC metadata';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}

/**
 * Hook for getting key financial metrics
 */
export function useKeyFinancials() {
  const [data, setData] = useState<KeyFinancials | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.getFinancialsFromUrl(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load financials';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}

/**
 * Hook for validating XBRL calculations
 */
export function useCalculationValidation() {
  const [data, setData] = useState<CalculationValidation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validate = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.validateFromUrl(url);
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to validate';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, validate };
}

/**
 * Hook for searching/exporting XBRL facts
 */
export function useXbrlFacts() {
  const [facts, setFacts] = useState<XbrlFact[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadFacts = useCallback(async (url: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.exportFacts(url);
      setFacts(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load facts';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const searchFacts = useCallback(async (url: string, query: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await xbrlApi.searchFacts(url, query);
      setFacts(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to search facts';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { facts, loading, error, loadFacts, searchFacts };
}
