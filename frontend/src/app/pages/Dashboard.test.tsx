import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Dashboard } from './Dashboard';

vi.mock('../hooks', () => ({
  useDashboard: vi.fn(),
  useTopInsiderPurchases: vi.fn(),
}));

import { useDashboard, useTopInsiderPurchases } from '../hooks';

const dashboardData = {
  stats: {
    totalFilings: 10_000,
    companiesTracked: 500,
    lastSync: '2026-03-13T12:30:00Z',
    filingsTodayCount: 25,
    form4Count: 100,
    form10KCount: 50,
    form10QCount: 75,
  },
  recentSearches: [],
  recentFilings: [],
  loading: false,
  error: null,
  refresh: vi.fn(),
};

describe('Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(useDashboard).mockReturnValue(dashboardData as never);
    vi.mocked(useTopInsiderPurchases).mockReturnValue({
      purchases: [
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
          sp500: false,
          accessionNumber: '0001234567-26-000001',
          transactionCode: 'P',
        },
      ],
      loading: false,
      error: null,
      refresh: vi.fn(),
    } as never);
  });

  it('renders the top insider purchases widget on the dashboard', () => {
    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Top Insider Purchases' })).toBeInTheDocument();
    expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View All' })).toBeInTheDocument();
  });

  it('renders a widget-level error when insider purchases fail to load', () => {
    vi.mocked(useTopInsiderPurchases).mockReturnValue({
      purchases: [],
      loading: false,
      error: 'Top purchases unavailable',
      refresh: vi.fn(),
    } as never);

    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>,
    );

    expect(screen.getByText('Failed to load insider purchases')).toBeInTheDocument();
    expect(screen.getByText('Top purchases unavailable')).toBeInTheDocument();
  });
});

