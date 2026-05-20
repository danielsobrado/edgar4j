import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { InsiderActivityPage } from './InsiderActivity';

vi.mock('../hooks', () => ({
  useInsiderActivity: vi.fn(),
}));

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    insiderActivityApi: {
      export: vi.fn(),
    },
  };
});

import { insiderActivityApi } from '../api';
import { useInsiderActivity } from '../hooks';

const mockRefresh = vi.fn();

const aggregateResults = {
  content: [
    {
      view: 'AGGREGATE',
      side: 'BUY',
      ticker: 'ACME',
      companyName: 'Acme Corporation',
      cik: '0001234567',
      latestTransactionDate: '2026-05-18',
      transactionDate: null,
      insiderName: null,
      insiderTitle: null,
      ownerType: null,
      insiderCount: 2,
      transactionCount: 3,
      totalShares: 20000,
      transactionShares: null,
      averagePrice: 50,
      transactionPrice: null,
      totalValue: 1_000_000,
      transactionValue: null,
      currentPrice: 55,
      percentChange: 10,
      marketCap: 2_000_000_000,
      marketCapSource: 'PROVIDER_MARKET_CAP',
      sp500: false,
      accessionNumber: null,
      transactionCode: null,
      transactionCodes: ['P'],
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

describe('InsiderActivityPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useInsiderActivity).mockReturnValue({
      results: aggregateResults,
      loading: false,
      error: null,
      refresh: mockRefresh,
    } as never);
  });

  it('renders aggregate columns for stock view', () => {
    render(
      <MemoryRouter initialEntries={['/insider-activity?preset=MULTI_INSIDER_BUYS']}>
        <Routes>
          <Route path="/insider-activity" element={<InsiderActivityPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Insider Activity Screener' })).toBeInTheDocument();
    expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
    expect(screen.getAllByText('Insiders').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Transactions').length).toBeGreaterThan(0);
    expect(screen.getByText('Stock aggregate')).toBeInTheDocument();
  });

  it('changes presets through URL-driven filters', () => {
    render(
      <MemoryRouter initialEntries={['/insider-activity?preset=MULTI_INSIDER_BUYS']}>
        <Routes>
          <Route path="/insider-activity" element={<InsiderActivityPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: /Latest Sales/ }));

    expect(useInsiderActivity).toHaveBeenLastCalledWith(expect.objectContaining({
      preset: 'LATEST_SALES',
      view: 'TRANSACTION',
      side: 'SELL',
      transactionCodes: ['S'],
    }));
  });

  it('exports the active result set as JSON', async () => {
    vi.mocked(insiderActivityApi.export).mockResolvedValue(undefined);

    render(
      <MemoryRouter initialEntries={['/insider-activity?preset=MULTI_INSIDER_BUYS']}>
        <Routes>
          <Route path="/insider-activity" element={<InsiderActivityPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'JSON' }));

    await waitFor(() => {
      expect(insiderActivityApi.export).toHaveBeenCalledWith(expect.objectContaining({
        preset: 'MULTI_INSIDER_BUYS',
      }), 'JSON');
    });
  });
});
