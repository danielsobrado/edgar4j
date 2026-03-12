import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm8K, useRecentForm8K, useForm8KSearch } from './useForm8K';
import { mockForm8K, mockForm8KList, mockForm8KPaginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form8k', () => ({
  form8kApi: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    getByCik: vi.fn(),
    getBySymbol: vi.fn(),
    getByDateRange: vi.fn(),
  },
}));

import { form8kApi } from '../api/endpoints/form8k';

describe('useForm8K', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm8K hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm8K(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form8k).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form8k by id', async () => {
      vi.mocked(form8kApi.getById).mockResolvedValue(mockForm8K);

      const { result } = renderHook(() => useForm8K('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form8k).toEqual(mockForm8K);
      expect(result.current.error).toBeNull();
      expect(form8kApi.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form8kApi.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm8K('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form8k).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm8K hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form8kApi.getRecentFilings).mockResolvedValue(mockForm8KList);

      const { result } = renderHook(() => useRecentForm8K(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm8KList);
      expect(result.current.error).toBeNull();
      expect(form8kApi.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form8kApi.getRecentFilings).mockResolvedValue(mockForm8KList);

      const { result } = renderHook(() => useRecentForm8K(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form8kApi.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm8KSearch hook', () => {
    it('should search by CIK', async () => {
      vi.mocked(form8kApi.getByCik).mockResolvedValue(mockForm8KPaginated);

      const { result } = renderHook(() => useForm8KSearch());

      await result.current.searchByCik('0001234567');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm8KPaginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form8kApi.getByCik).toHaveBeenCalledWith('0001234567', 0, 20);
    });

    it('should search by symbol', async () => {
      vi.mocked(form8kApi.getBySymbol).mockResolvedValue(mockForm8KPaginated);

      const { result } = renderHook(() => useForm8KSearch());

      await result.current.searchBySymbol('AAPL');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm8KPaginated.content);
      expect(form8kApi.getBySymbol).toHaveBeenCalledWith('AAPL', 0, 20);
    });

    it('should search by date range', async () => {
      vi.mocked(form8kApi.getByDateRange).mockResolvedValue(mockForm8KPaginated);

      const { result } = renderHook(() => useForm8KSearch());

      await result.current.searchByDateRange('2024-01-01', '2024-12-31');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(form8kApi.getByDateRange).toHaveBeenCalledWith('2024-01-01', '2024-12-31', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form8kApi.getByCik).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm8KSearch());

      await result.current.searchByCik('test');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
