import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm20F, useRecentForm20F, useForm20FSearch } from './useForm20F';
import { mockForm20F, mockForm20FList, mockForm20FPaginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form20f', () => ({
  form20fApi: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    getByCik: vi.fn(),
    getBySymbol: vi.fn(),
    getByDateRange: vi.fn(),
  },
}));

import { form20fApi } from '../api/endpoints/form20f';

describe('useForm20F', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm20F hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm20F(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form20f).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form20f by id', async () => {
      vi.mocked(form20fApi.getById).mockResolvedValue(mockForm20F);

      const { result } = renderHook(() => useForm20F('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form20f).toEqual(mockForm20F);
      expect(result.current.error).toBeNull();
      expect(form20fApi.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form20fApi.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm20F('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form20f).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm20F hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form20fApi.getRecentFilings).mockResolvedValue(mockForm20FList);

      const { result } = renderHook(() => useRecentForm20F(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm20FList);
      expect(result.current.error).toBeNull();
      expect(form20fApi.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form20fApi.getRecentFilings).mockResolvedValue(mockForm20FList);

      const { result } = renderHook(() => useRecentForm20F(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form20fApi.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm20FSearch hook', () => {
    it('should search by CIK', async () => {
      vi.mocked(form20fApi.getByCik).mockResolvedValue(mockForm20FPaginated);

      const { result } = renderHook(() => useForm20FSearch());

      await result.current.searchByCik('0001234567');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm20FPaginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form20fApi.getByCik).toHaveBeenCalledWith('0001234567', 0, 20);
    });

    it('should search by symbol', async () => {
      vi.mocked(form20fApi.getBySymbol).mockResolvedValue(mockForm20FPaginated);

      const { result } = renderHook(() => useForm20FSearch());

      await result.current.searchBySymbol('FCRP');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm20FPaginated.content);
      expect(form20fApi.getBySymbol).toHaveBeenCalledWith('FCRP', 0, 20);
    });

    it('should search by date range', async () => {
      vi.mocked(form20fApi.getByDateRange).mockResolvedValue(mockForm20FPaginated);

      const { result } = renderHook(() => useForm20FSearch());

      await result.current.searchByDateRange('2024-01-01', '2024-12-31');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(form20fApi.getByDateRange).toHaveBeenCalledWith('2024-01-01', '2024-12-31', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form20fApi.getByCik).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm20FSearch());

      await result.current.searchByCik('test');

      await waitFor(() => {
        expect(result.current.error).not.toBeNull();
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
