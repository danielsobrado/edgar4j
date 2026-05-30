import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PoliticalTradesPage } from './PoliticalTrades';

vi.mock('../hooks', () => ({
  usePoliticalTrades: vi.fn(),
}));

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    politicalTradesApi: {
      export: vi.fn(),
      sync: vi.fn(),
      politicians: vi.fn().mockResolvedValue([]),
    },
  };
});

import { politicalTradesApi } from '../api';
import { usePoliticalTrades } from '../hooks';

const mockRefresh = vi.fn();

const results = {
  content: [
    {
      id: 'CAPITOL_TRADES-20003798315',
      sourceTradeId: 'CAPITOL_TRADES:20003798315',
      politicianName: 'Tim Moore',
      party: 'Republican',
      chamber: 'House',
      state: 'NC',
      issuerName: 'AT&T Inc',
      ticker: 'T',
      disclosureDate: '2026-05-20',
      tradedDate: '2026-05-18',
      filedAfterDays: 1,
      owner: 'Undisclosed',
      transactionType: 'BUY',
      amountLabel: '15K-50K',
      amountMin: 15_000,
      amountMax: 50_000,
      price: 24.43,
      assetType: 'stock',
      sourceTradeUrl: 'https://www.capitoltrades.com/trades/20003798315',
      source: 'CAPITOL_TRADES',
      firstSeenAt: '2026-05-21T10:00:00Z',
      updatedAt: '2026-05-21T10:00:00Z',
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

describe('PoliticalTradesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(usePoliticalTrades).mockReturnValue({
      results,
      loading: false,
      error: null,
      refresh: mockRefresh,
    } as never);
  });

  it('renders the dense political trades table without images', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/political-trades']}>
        <Routes>
          <Route path="/political-trades" element={<PoliticalTradesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Political Trades' })).toBeInTheDocument();
    expect(screen.getByText('Tim Moore')).toBeInTheDocument();
    expect(screen.getByText('$T')).toBeInTheDocument();
    expect(screen.getByText('AT&T Inc')).toBeInTheDocument();
    expect(screen.getByText('$15K - $50K')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('updates URL-driven filters', () => {
    render(
      <MemoryRouter initialEntries={['/political-trades']}>
        <Routes>
          <Route path="/political-trades" element={<PoliticalTradesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Ticker'), { target: { value: 'MLM' } });

    expect(usePoliticalTrades).toHaveBeenLastCalledWith(expect.objectContaining({
      ticker: 'MLM',
      assetType: 'stock',
      sortBy: 'disclosureDate',
    }));
  });

  it('exports the active result set as JSON', async () => {
    vi.mocked(politicalTradesApi.export).mockResolvedValue(undefined);

    render(
      <MemoryRouter initialEntries={['/political-trades?assetType=stock&ticker=T']}>
        <Routes>
          <Route path="/political-trades" element={<PoliticalTradesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'JSON' }));

    await waitFor(() => {
      expect(politicalTradesApi.export).toHaveBeenCalledWith(expect.objectContaining({
        assetType: 'stock',
        ticker: 'T',
      }), 'JSON');
    });
  });

  it('syncs current asset type and refreshes results', async () => {
    vi.mocked(politicalTradesApi.sync).mockResolvedValue({
      source: 'CAPITOL_TRADES',
      assetType: 'stock',
      requestedPages: 25,
      fetchedPages: 25,
      fetchedRows: 1,
      insertedRows: 1,
      updatedRows: 0,
      skippedRows: 0,
      totalCachedRows: 1,
      forced: false,
      syncedAt: '2026-05-21T10:00:00Z',
      durationMillis: 1200,
    });

    render(
      <MemoryRouter initialEntries={['/political-trades?assetType=stock']}>
        <Routes>
          <Route path="/political-trades" element={<PoliticalTradesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Sync Latest' }));

    await waitFor(() => {
      expect(politicalTradesApi.sync).toHaveBeenCalledWith(expect.objectContaining({
        assetType: 'stock',
        maxPages: 25,
        force: false,
      }));
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  it('shows sync failures without refreshing stale results', async () => {
    vi.mocked(politicalTradesApi.sync).mockRejectedValue(new Error('Political trade sync is already running'));

    render(
      <MemoryRouter initialEntries={['/political-trades?assetType=stock']}>
        <Routes>
          <Route path="/political-trades" element={<PoliticalTradesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Sync Latest' }));

    expect(await screen.findByText('Failed to sync political trades')).toBeInTheDocument();
    expect(screen.getByText('Political trade sync is already running')).toBeInTheDocument();
    expect(mockRefresh).not.toHaveBeenCalled();
  });
});
