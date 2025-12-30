import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm6K, useRecentForm6K, useForm6KSearch } from './useForm6K';
import { mockForm6K, mockForm6KList, mockForm6KPaginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form6k', () => ({
  form6kApi: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    getByCik: vi.fn(),
    getBySymbol: vi.fn(),
    getByDateRange: vi.fn(),
  },
}));

import { form6kApi } from '../api/endpoints/form6k';

describe('useForm6K', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm6K hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm6K(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form6k).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form6k by id', async () => {
      vi.mocked(form6kApi.getById).mockResolvedValue(mockForm6K);

      const { result } = renderHook(() => useForm6K('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form6k).toEqual(mockForm6K);
      expect(result.current.error).toBeNull();
      expect(form6kApi.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form6kApi.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm6K('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form6k).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm6K hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form6kApi.getRecentFilings).mockResolvedValue(mockForm6KList);

      const { result } = renderHook(() => useRecentForm6K(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm6KList);
      expect(result.current.error).toBeNull();
      expect(form6kApi.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form6kApi.getRecentFilings).mockResolvedValue(mockForm6KList);

      const { result } = renderHook(() => useRecentForm6K(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form6kApi.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm6KSearch hook', () => {
    it('should search by CIK', async () => {
      vi.mocked(form6kApi.getByCik).mockResolvedValue(mockForm6KPaginated);

      const { result } = renderHook(() => useForm6KSearch());

      await result.current.searchByCik('0001234567');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm6KPaginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form6kApi.getByCik).toHaveBeenCalledWith('0001234567', 0, 20);
    });

    it('should search by symbol', async () => {
      vi.mocked(form6kApi.getBySymbol).mockResolvedValue(mockForm6KPaginated);

      const { result } = renderHook(() => useForm6KSearch());

      await result.current.searchBySymbol('FCRP');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm6KPaginated.content);
      expect(form6kApi.getBySymbol).toHaveBeenCalledWith('FCRP', 0, 20);
    });

    it('should search by date range', async () => {
      vi.mocked(form6kApi.getByDateRange).mockResolvedValue(mockForm6KPaginated);

      const { result } = renderHook(() => useForm6KSearch());

      await result.current.searchByDateRange('2024-01-01', '2024-12-31');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(form6kApi.getByDateRange).toHaveBeenCalledWith('2024-01-01', '2024-12-31', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form6kApi.getByCik).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm6KSearch());

      await result.current.searchByCik('test');

      await waitFor(() => {
        expect(result.current.error).not.toBeNull();
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
