import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm3, useRecentForm3, useForm3Search } from './useForm3';
import { mockForm3, mockForm3List, mockForm3Paginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api/endpoints/form3', () => ({
  form3Api: {
    getById: vi.fn(),
    getRecentFilings: vi.fn(),
    getByCik: vi.fn(),
    getBySymbol: vi.fn(),
    getByDateRange: vi.fn(),
  },
}));

import { form3Api } from '../api/endpoints/form3';

describe('useForm3', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm3 hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm3(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form3).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form3 by id', async () => {
      vi.mocked(form3Api.getById).mockResolvedValue(mockForm3);

      const { result } = renderHook(() => useForm3('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form3).toEqual(mockForm3);
      expect(result.current.error).toBeNull();
      expect(form3Api.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form3Api.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm3('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form3).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm3 hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form3Api.getRecentFilings).mockResolvedValue(mockForm3List);

      const { result } = renderHook(() => useRecentForm3(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm3List);
      expect(result.current.error).toBeNull();
      expect(form3Api.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form3Api.getRecentFilings).mockResolvedValue(mockForm3List);

      const { result } = renderHook(() => useRecentForm3(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form3Api.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm3Search hook', () => {
    it('should search by CIK', async () => {
      vi.mocked(form3Api.getByCik).mockResolvedValue(mockForm3Paginated);

      const { result } = renderHook(() => useForm3Search());

      await result.current.searchByCik('0001234567');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm3Paginated.content);
      expect(result.current.totalElements).toBe(1);
      expect(form3Api.getByCik).toHaveBeenCalledWith('0001234567', 0, 20);
    });

    it('should search by symbol', async () => {
      vi.mocked(form3Api.getBySymbol).mockResolvedValue(mockForm3Paginated);

      const { result } = renderHook(() => useForm3Search());

      await result.current.searchBySymbol('ACME');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(result.current.filings).toEqual(mockForm3Paginated.content);
      expect(form3Api.getBySymbol).toHaveBeenCalledWith('ACME', 0, 20);
    });

    it('should search by date range', async () => {
      vi.mocked(form3Api.getByDateRange).mockResolvedValue(mockForm3Paginated);

      const { result } = renderHook(() => useForm3Search());

      await result.current.searchByDateRange('2024-01-01', '2024-12-31');

      await waitFor(() => {
        expect(result.current.filings.length).toBeGreaterThan(0);
      });

      expect(form3Api.getByDateRange).toHaveBeenCalledWith('2024-01-01', '2024-12-31', 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form3Api.getByCik).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm3Search());

      await result.current.searchByCik('test');

      await waitFor(() => {
        expect(result.current.error).not.toBeNull();
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
