import { useState, useCallback } from 'react';
import { exportApi, ExportRequest, FilingSearchRequest } from '../api';

export function useExport() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const exportToCsv = useCallback(async (filingIds?: string[], searchCriteria?: FilingSearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const request: ExportRequest = {
        filingIds,
        searchCriteria,
        format: 'CSV',
      };
      await exportApi.exportToCsv(request);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to export to CSV';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const exportToJson = useCallback(async (filingIds?: string[], searchCriteria?: FilingSearchRequest) => {
    setLoading(true);
    setError(null);
    try {
      const request: ExportRequest = {
        filingIds,
        searchCriteria,
        format: 'JSON',
      };
      await exportApi.exportToJson(request);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to export to JSON';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    loading,
    error,
    exportToCsv,
    exportToJson,
  };
}
