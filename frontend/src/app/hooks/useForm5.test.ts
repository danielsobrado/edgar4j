import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm5, useRecentForm5, useForm5Search } from './useForm5';
import { mockForm5, mockForm5List, mockForm5Paginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form5', () => ({
  form5Api: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    getByCik: vi.fn(),
    getBySymbol: vi.fn(),
    getByDateRange: vi.fn(),
  },
}));

import { form5Api } from '../api/endpoints/form5';

describe('useForm5', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm5 hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm5(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form5).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form5 by id', async () => {
      vi.mocked(form5Api.getById).mockResolvedValue(mockForm5);

      const { result } = renderHook(() => useForm5('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form5).toEqual(mockForm5);
      expect(result.current.error).toBeNull();
      expect(form5Api.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form5Api.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm5('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form5).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm5 hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form5Api.getRecentFilings).mockResolvedValue(mockForm5List);

      const { result } = renderHook(() => useRecentForm5(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm5List);
      expect(result.current.error).toBeNull();
      expect(form5Api.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form5Api.getRecentFilings).mockResolvedValue(mockForm5List);

      const { result } = renderHook(() => useRecentForm5(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form5Api.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm5Search hook', () => {
    it('should search by CIK', async () => {
      vi.mocked(form5Api.getByCik).mockResolvedValue(mockForm5Paginated);

      const { result } = renderHook(() => useForm5Search());

      await result.current.searchByCik('0001234567');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm5Paginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form5Api.getByCik).toHaveBeenCalledWith('0001234567', 0, 20);
    });

    it('should search by symbol', async () => {
      vi.mocked(form5Api.getBySymbol).mockResolvedValue(mockForm5Paginated);

      const { result } = renderHook(() => useForm5Search());

      await result.current.searchBySymbol('ACME');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm5Paginated.content);
      expect(form5Api.getBySymbol).toHaveBeenCalledWith('ACME', 0, 20);
    });

    it('should search by date range', async () => {
      vi.mocked(form5Api.getByDateRange).mockResolvedValue(mockForm5Paginated);

      const { result } = renderHook(() => useForm5Search());

      await result.current.searchByDateRange('2024-01-01', '2024-12-31');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(form5Api.getByDateRange).toHaveBeenCalledWith('2024-01-01', '2024-12-31', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form5Api.getByCik).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm5Search());

      await result.current.searchByCik('test');

      await waitFor(() => {
        expect(result.current.error).not.toBeNull();
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
