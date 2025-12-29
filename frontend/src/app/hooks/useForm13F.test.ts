import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm13F, useRecentForm13F, useForm13FSearch } from './useForm13F';
import { mockForm13F, mockForm13FList, mockForm13FPaginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form13f', () => ({
  form13fApi: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    searchByFilerName: vi.fn(),
    getByIssuerName: vi.fn(),
    getByCusip: vi.fn(),
    getByQuarter: vi.fn(),
  },
}));

import { form13fApi } from '../api/endpoints/form13f';

describe('useForm13F', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm13F hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm13F(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13f).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form13f by id', async () => {
      vi.mocked(form13fApi.getById).mockResolvedValue(mockForm13F);

      const { result } = renderHook(() => useForm13F('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13f).toEqual(mockForm13F);
      expect(result.current.error).toBeNull();
      expect(form13fApi.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form13fApi.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm13F('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13f).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm13F hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form13fApi.getRecentFilings).mockResolvedValue(mockForm13FList);

      const { result } = renderHook(() => useRecentForm13F(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm13FList);
      expect(result.current.error).toBeNull();
      expect(form13fApi.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should refresh filings', async () => {
      vi.mocked(form13fApi.getRecentFilings).mockResolvedValue(mockForm13FList);

      const { result } = renderHook(() => useRecentForm13F(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      // Call refresh
      result.current.refresh();

      await waitFor(() => {
        expect(form13fApi.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm13FSearch hook', () => {
    it('should search by filer name', async () => {
      vi.mocked(form13fApi.searchByFilerName).mockResolvedValue(mockForm13FPaginated);

      const { result } = renderHook(() => useForm13FSearch());

      expect(result.current.loading).toBe(false);
      expect(result.current.filings).toEqual([]);

      // Trigger search
      await result.current.searchByFilerName('Vanguard');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm13FPaginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form13fApi.searchByFilerName).toHaveBeenCalledWith('Vanguard', 0, 20);
    });

    it('should search by CUSIP', async () => {
      vi.mocked(form13fApi.getByCusip).mockResolvedValue(mockForm13FPaginated);

      const { result } = renderHook(() => useForm13FSearch());

      await result.current.searchByCusip('037833100');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm13FPaginated.content);
      expect(form13fApi.getByCusip).toHaveBeenCalledWith('037833100', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form13fApi.searchByFilerName).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm13FSearch());

      await result.current.searchByFilerName('Test');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBe('Search failed');
      expect(result.current.filings).toEqual([]);
    });
  });
});
