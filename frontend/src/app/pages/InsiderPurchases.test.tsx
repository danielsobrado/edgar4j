import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { InsiderPurchasesPage } from './InsiderPurchases';

vi.mock('../hooks', () => ({
  useSettings: vi.fn(),
  useInsiderPurchases: vi.fn(),
}));

import { useInsiderPurchases, useSettings } from '../hooks';

const mockRefresh = vi.fn();
const mockSettingsRefresh = vi.fn();
const mockSetFilter = vi.fn();

const settingsDefaults = {
  insiderPurchaseLookbackDays: 14,
  insiderPurchaseMinMarketCap: 500_000_000,
  insiderPurchaseSp500Only: true,
  insiderPurchaseMinTransactionValue: 100_000,
};

const filterState = {
  lookbackDays: 14,
  minMarketCap: 500_000_000,
  sp500Only: true,
  minTransactionValue: 100_000,
  sortBy: 'percentChange' as const,
  sortDir: 'desc' as const,
  page: 0,
  size: 50,
};

const mockPurchases = {
  content: [
    {
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
      marketCapSource: 'SEC_FILING_XBRL_SHARES_OUTSTANDING',
      sp500: true,
      accessionNumber: '0001234567-26-000001',
      transactionCode: 'P',
    },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 2,
  first: true,
  last: false,
  hasNext: true,
  hasPrevious: false,
};

const mockSummary = {
  totalPurchases: 2,
  uniqueCompanies: 1,
  totalPurchaseValue: 125_000,
  averagePercentChange: 50,
  positiveChangeCount: 2,
  negativeChangeCount: 0,
};

describe('InsiderPurchasesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(useSettings).mockReturnValue({
      settings: settingsDefaults,
      loading: false,
      saving: false,
      error: null,
      updateSettings: vi.fn(),
      checkConnections: vi.fn(),
      refresh: mockSettingsRefresh,
    } as never);

    vi.mocked(useInsiderPurchases).mockReturnValue({
      purchases: mockPurchases,
      summary: mockSummary,
      loading: false,
      error: null,
      filter: filterState,
      setFilter: mockSetFilter,
      refresh: mockRefresh,
    } as never);
  });

  it('boots the insider purchases hook from saved settings and renders results', () => {
    render(
      <MemoryRouter>
        <InsiderPurchasesPage />
      </MemoryRouter>,
    );

    expect(useInsiderPurchases).toHaveBeenCalledWith(expect.objectContaining({
      lookbackDays: 14,
      minMarketCap: 500_000_000,
      sp500Only: true,
      minTransactionValue: 100_000,
    }));

    expect(screen.getByRole('heading', { name: 'Stocks with Recent Insider Purchases' })).toBeInTheDocument();
    expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
    expect(screen.getByText('S&P 500')).toBeInTheDocument();
    expect(screen.getByText('Purchase value')).toBeInTheDocument();
    expect(screen.getByText('SEC filing XBRL')).toBeInTheDocument();
  });

  it('falls back to built-in defaults when saved settings cannot be loaded', () => {
    vi.mocked(useSettings).mockReturnValue({
      settings: null,
      loading: false,
      saving: false,
      error: 'Settings API unavailable',
      updateSettings: vi.fn(),
      checkConnections: vi.fn(),
      refresh: mockSettingsRefresh,
    } as never);

    render(
      <MemoryRouter>
        <InsiderPurchasesPage />
      </MemoryRouter>,
    );

    expect(useInsiderPurchases).toHaveBeenCalledWith(expect.objectContaining({
      lookbackDays: 30,
      minMarketCap: 0,
      sp500Only: false,
      minTransactionValue: 0,
    }));
    expect(screen.getByText(/Using built-in filters for this session/)).toBeInTheDocument();
  });

  it('updates the current filter and resets the page when the minimum transaction value changes', () => {
    render(
      <MemoryRouter>
        <InsiderPurchasesPage />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Min transaction value'), {
      target: { value: '500000' },
    });

    expect(mockSetFilter).toHaveBeenCalledTimes(1);
    const update = mockSetFilter.mock.calls[0][0];
    expect(update(filterState)).toEqual({
      ...filterState,
      minTransactionValue: 500_000,
      page: 0,
    });
  });
});
