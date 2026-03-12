import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useForm13DG, useRecentForm13DG, useForm13DGSearch } from './useForm13DG';
import { mockForm13DG, mockForm13DGList, mockForm13DGPaginated } from '../../test/mocks/apiMocks';

// Mock the API module
vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    form13dgApi: {
      getById: vi.fn(),
      getByAccessionNumber: vi.fn(),
      getRecentFilings: vi.fn(),
      searchByFilerName: vi.fn(),
      searchByIssuerName: vi.fn(),
      getByCusip: vi.fn(),
      getByScheduleType: vi.fn(),
      getActivistFilings: vi.fn(),
      getAboveThreshold: vi.fn(),
      getBeneficialOwners: vi.fn(),
      getOwnershipHistory: vi.fn(),
      getFilerPortfolio: vi.fn(),
      getOwnershipSnapshot: vi.fn(),
      getTopActivistInvestors: vi.fn(),
    },
  };
});

import { form13dgApi } from '../api';

describe('useForm13DG', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useForm13DG hook', () => {
    it('should return null when id is undefined', async () => {
      const { result } = renderHook(() => useForm13DG(undefined));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13dg).toBeNull();
      expect(result.current.error).toBeNull();
    });

    it('should fetch form13dg by id', async () => {
      vi.mocked(form13dgApi.getById).mockResolvedValue(mockForm13DG);

      const { result } = renderHook(() => useForm13DG('1'));

      expect(result.current.loading).toBe(true);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13dg).toEqual(mockForm13DG);
      expect(result.current.error).toBeNull();
      expect(form13dgApi.getById).toHaveBeenCalledWith('1');
    });

    it('should handle error when fetching fails', async () => {
      vi.mocked(form13dgApi.getById).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useForm13DG('1'));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.form13dg).toBeNull();
      expect(result.current.error).toBe('Network error');
    });
  });

  describe('useRecentForm13DG hook', () => {
    it('should fetch recent filings', async () => {
      vi.mocked(form13dgApi.getRecentFilings).mockResolvedValue(mockForm13DGList);

      const { result } = renderHook(() => useRecentForm13DG(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm13DGList);
      expect(result.current.error).toBeNull();
      expect(form13dgApi.getRecentFilings).toHaveBeenCalledWith(10);
    });

    it('should support refresh', async () => {
      vi.mocked(form13dgApi.getRecentFilings).mockResolvedValue(mockForm13DGList);

      const { result } = renderHook(() => useRecentForm13DG(10));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refresh();

      await waitFor(() => {
        expect(form13dgApi.getRecentFilings).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('useForm13DGSearch hook', () => {
    it('should search by filer name', async () => {
      vi.mocked(form13dgApi.searchByFilerName).mockResolvedValue(mockForm13DGPaginated);

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.searchByFilerName('Carl Icahn');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.filings).toEqual(mockForm13DGPaginated.content);
      expect(result.current.totalElements).toBe(1);
    });

    it('should search by issuer name', async () => {
      vi.mocked(form13dgApi.searchByIssuerName).mockResolvedValue(mockForm13DGPaginated);

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.searchByIssuerName('Apple');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.filings).toEqual(mockForm13DGPaginated.content);
      });
    });

    it('should search by schedule type', async () => {
      vi.mocked(form13dgApi.getByScheduleType).mockResolvedValue(mockForm13DGPaginated);

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.searchByScheduleType('13D');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(form13dgApi.getByScheduleType).toHaveBeenCalledWith('13D', 0, 20);
    });

    it('should get activist filings', async () => {
      vi.mocked(form13dgApi.getActivistFilings).mockResolvedValue(mockForm13DGPaginated);

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.getActivistFilings();

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(form13dgApi.getActivistFilings).toHaveBeenCalledWith(0, 20);
    });

    it('should get filings above threshold', async () => {
      vi.mocked(form13dgApi.getAboveThreshold).mockResolvedValue(mockForm13DGPaginated);

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.getAboveThreshold(10);

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(form13dgApi.getAboveThreshold).toHaveBeenCalledWith(10, 0, 20);
    });

    it('should handle search error', async () => {
      vi.mocked(form13dgApi.searchByFilerName).mockRejectedValue(new Error('Search failed'));

      const { result } = renderHook(() => useForm13DGSearch());

      await result.current.searchByFilerName('Test');

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBe('Search failed');
    });
  });
});
