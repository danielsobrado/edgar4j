import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useInsiderPurchases, useTopInsiderPurchases } from './useInsiderPurchases';

vi.mock('../api/endpoints/insiderPurchases', () => ({
  insiderPurchasesApi: {
    getInsiderPurchases: vi.fn(),
    getTopInsiderPurchases: vi.fn(),
    getSummary: vi.fn(),
  },
}));

import { insiderPurchasesApi } from '../api/endpoints/insiderPurchases';

const mockPurchase = {
  ticker: 'ACME',
  companyName: 'Acme Corporation',
  cik: '0001234567',
  insiderName: 'Jane Doe',
  insiderTitle: 'Chief Executive Officer',
  ownerType: 'Officer',
  transactionDate: '2026-03-01',
  purchasePrice: 12.5,
  transactionShares: 10_000,
  transactionValue: 125_000,
  currentPrice: 18.75,
  percentChange: 50,
  marketCap: 2_500_000_000,
  sp500: false,
  accessionNumber: '0001234567-26-000001',
  transactionCode: 'P',
};

const mockPaginatedPurchases = {
  content: [mockPurchase],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

const mockSummary = {
  totalPurchases: 1,
  uniqueCompanies: 1,
  totalPurchaseValue: 125_000,
  averagePercentChange: 50,
  positiveChangeCount: 1,
  negativeChangeCount: 0,
};

describe('useInsiderPurchases', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads paginated purchases and summary with default filter values', async () => {
    vi.mocked(insiderPurchasesApi.getInsiderPurchases).mockResolvedValue(mockPaginatedPurchases);
    vi.mocked(insiderPurchasesApi.getSummary).mockResolvedValue(mockSummary);

    const { result } = renderHook(() => useInsiderPurchases());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.purchases).toEqual(mockPaginatedPurchases);
    expect(result.current.summary).toEqual(mockSummary);
    expect(result.current.filter.lookbackDays).toBe(30);
    expect(result.current.filter.sortBy).toBe('percentChange');
    expect(insiderPurchasesApi.getInsiderPurchases).toHaveBeenCalledWith({
      lookbackDays: 30,
      sortBy: 'percentChange',
      sortDir: 'desc',
      page: 0,
      size: 50,
    });
    expect(insiderPurchasesApi.getSummary).toHaveBeenCalledWith(30);
  });

  it('refreshes when the filter changes', async () => {
    vi.mocked(insiderPurchasesApi.getInsiderPurchases).mockResolvedValue(mockPaginatedPurchases);
    vi.mocked(insiderPurchasesApi.getSummary).mockResolvedValue(mockSummary);

    const { result } = renderHook(() => useInsiderPurchases({ lookbackDays: 14 }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    act(() => {
      result.current.setFilter((prev) => ({
        ...prev,
        sp500Only: true,
        page: 1,
      }));
    });

    await waitFor(() => {
      expect(insiderPurchasesApi.getInsiderPurchases).toHaveBeenCalledTimes(2);
    });

    expect(insiderPurchasesApi.getInsiderPurchases).toHaveBeenLastCalledWith({
      lookbackDays: 14,
      sortBy: 'percentChange',
      sortDir: 'desc',
      page: 1,
      size: 50,
      sp500Only: true,
    });
    expect(insiderPurchasesApi.getSummary).toHaveBeenLastCalledWith(14);
  });

  it('surfaces API errors', async () => {
    vi.mocked(insiderPurchasesApi.getInsiderPurchases).mockRejectedValue(new Error('Failed to load'));
    vi.mocked(insiderPurchasesApi.getSummary).mockResolvedValue(mockSummary);

    const { result } = renderHook(() => useInsiderPurchases());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.error).toBe('Failed to load');
    expect(result.current.purchases).toBeNull();
  });
});

describe('useTopInsiderPurchases', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads top insider purchases', async () => {
    vi.mocked(insiderPurchasesApi.getTopInsiderPurchases).mockResolvedValue([mockPurchase]);

    const { result } = renderHook(() => useTopInsiderPurchases(5));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.purchases).toEqual([mockPurchase]);
    expect(result.current.error).toBeNull();
    expect(insiderPurchasesApi.getTopInsiderPurchases).toHaveBeenCalledWith(5);
  });

  it('supports manual refresh', async () => {
    vi.mocked(insiderPurchasesApi.getTopInsiderPurchases).mockResolvedValue([mockPurchase]);

    const { result } = renderHook(() => useTopInsiderPurchases());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.refresh();
    });

    await waitFor(() => {
      expect(insiderPurchasesApi.getTopInsiderPurchases).toHaveBeenCalledTimes(2);
    });
  });
});
