import type { ReactNode } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DividendViabilityDashboard } from './DividendViabilityDashboard';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  LineChart: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  CartesianGrid: () => null,
  Legend: () => null,
  Line: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

vi.mock('../api', () => ({
  companiesApi: {
    getCompanyByTicker: vi.fn(),
    getCompanies: vi.fn(),
  },
  dividendApi: {
    getOverview: vi.fn(),
    getHistory: vi.fn(),
    getAlerts: vi.fn(),
    getEvents: vi.fn(),
    getEvidence: vi.fn(),
    reconcileCompany: vi.fn(),
    compare: vi.fn(),
    screen: vi.fn(),
  },
}));

import { companiesApi, dividendApi } from '../api';

describe('DividendViabilityDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(companiesApi.getCompanyByTicker).mockResolvedValue({
      id: '1',
      name: 'Apple Inc.',
      ticker: 'AAPL',
      cik: '0000320193',
      sic: '3571',
      sicDescription: 'Electronic Computers',
      entityType: 'operating',
      stateOfIncorporation: 'CA',
      stateOfIncorporationDescription: 'California',
      fiscalYearEnd: 930,
      ein: '00-0000000',
      description: '',
      website: '',
      investorWebsite: '',
      category: '',
      tickers: ['AAPL'],
      exchanges: ['NASDAQ'],
      filingCount: 100,
      hasInsiderTransactions: true,
    });

    vi.mocked(dividendApi.getOverview).mockResolvedValue({
      company: {
        cik: '0000320193',
        ticker: 'AAPL',
        name: 'Apple Inc.',
        sector: 'Technology',
        fiscalYearEnd: '0930',
        lastFilingDate: '2026-02-01',
        dataFreshness: '2026-03-14T12:00:00Z',
      },
      viability: {
        rating: 'SAFE',
        activeAlerts: 0,
        score: 92,
      },
      snapshot: {
        dpsLatest: 1.26,
        dpsCagr5y: 0.058,
        fcfPayoutRatio: 0.15,
        uninterruptedYears: 12,
        consecutiveRaises: 12,
        netDebtToEbitda: 0.4,
        interestCoverage: 42.5,
        currentRatio: 1.07,
        fcfMargin: 0.27,
        dividendYield: 0.018,
        shareholderYield: 0.035,
        buybackYield: 0.017,
      },
      confidence: {
        dpsLatest: 'HIGH',
      },
      alerts: [],
      coverage: {
        revenue: 100_000_000_000,
        operatingCashFlow: 25_000_000_000,
        capitalExpenditures: 5_000_000_000,
        freeCashFlow: 20_000_000_000,
        dividendsPaid: 3_000_000_000,
        cashCoverage: 6.67,
        retainedCash: 17_000_000_000,
      },
      balance: {
        cash: 30_000_000_000,
        grossDebt: 40_000_000_000,
        netDebt: 10_000_000_000,
        ebitdaProxy: 25_000_000_000,
        netDebtToEbitda: 0.4,
        currentRatio: 1.07,
        interestCoverage: 42.5,
      },
      trend: [
        {
          periodEnd: '2024-09-28',
          filingDate: '2024-11-01',
          accessionNumber: '0000320193-24-000099',
          dividendsPerShare: 1.1,
          earningsPerShare: 6.0,
        },
        {
          periodEnd: '2025-09-27',
          filingDate: '2025-11-01',
          accessionNumber: '0000320193-25-000106',
          dividendsPerShare: 1.26,
          earningsPerShare: 6.42,
        },
      ],
      evidence: {
        latestAnnualReport: {
          formType: '10-K',
          accessionNumber: '0000320193-25-000106',
          filingDate: '2025-11-01',
          url: 'https://www.sec.gov/Archives/edgar/data/320193/annual.htm',
        },
        latestCurrentReport: {
          formType: '8-K',
          accessionNumber: '0000320193-26-000010',
          filingDate: '2026-01-20',
          url: 'https://www.sec.gov/Archives/edgar/data/320193/8k.htm',
        },
      },
      referencePrice: 70,
      warnings: ['Some overview coverage is estimated from the latest annual filing.'],
    });

    vi.mocked(dividendApi.getHistory).mockResolvedValue({
      company: {
        cik: '0000320193',
        ticker: 'AAPL',
        name: 'Apple Inc.',
        sector: 'Technology',
        fiscalYearEnd: '0930',
        lastFilingDate: '2026-02-01',
        dataFreshness: '2026-03-14T12:00:00Z',
      },
      period: 'FY',
      yearsRequested: 15,
      metrics: [
        'dps_declared',
        'eps_diluted',
        'earnings_payout',
        'revenue',
        'free_cash_flow',
        'dividends_paid',
        'fcf_payout',
        'cash_coverage',
        'retained_cash',
        'gross_debt',
        'net_debt_to_ebitda',
        'current_ratio',
        'interest_coverage',
        'fcf_margin',
      ],
      series: [
        {
          metric: 'dps_declared',
          label: 'Dividend Per Share',
          unit: 'USD/share',
          latestValue: 1.26,
          cagr: 0.058,
          volatility: 0.01,
          trend: 'UP',
          points: [
            {
              periodEnd: '2024-09-28',
              filingDate: '2024-11-01',
              accessionNumber: '0000320193-24-000099',
              value: 1.1,
            },
            {
              periodEnd: '2025-09-27',
              filingDate: '2025-11-01',
              accessionNumber: '0000320193-25-000106',
              value: 1.26,
            },
          ],
        },
        {
          metric: 'earnings_payout',
          label: 'Earnings Payout Ratio',
          unit: 'ratio',
          latestValue: 0.196,
          cagr: null,
          volatility: null,
          trend: 'FLAT',
          points: [],
        },
        {
          metric: 'revenue',
          label: 'Revenue',
          unit: 'USD',
          latestValue: 100_000_000_000,
          cagr: 0.041,
          volatility: 0.03,
          trend: 'UP',
          points: [],
        },
        {
          metric: 'fcf_payout',
          label: 'Free Cash Flow Payout Ratio',
          unit: 'ratio',
          latestValue: 0.15,
          cagr: null,
          volatility: null,
          trend: 'FLAT',
          points: [],
        },
        {
          metric: 'cash_coverage',
          label: 'Cash Coverage',
          unit: 'ratio',
          latestValue: 6.67,
          cagr: null,
          volatility: null,
          trend: 'FLAT',
          points: [],
        },
        {
          metric: 'interest_coverage',
          label: 'Interest Coverage',
          unit: 'ratio',
          latestValue: 42.5,
          cagr: null,
          volatility: null,
          trend: 'UP',
          points: [],
        },
        {
          metric: 'fcf_margin',
          label: 'Free Cash Flow Margin',
          unit: 'ratio',
          latestValue: 0.27,
          cagr: null,
          volatility: null,
          trend: 'UP',
          points: [],
        },
      ],
      rows: [
        {
          periodEnd: '2024-09-28',
          filingDate: '2024-11-01',
          accessionNumber: '0000320193-24-000099',
          metrics: {
            dps_declared: 1.1,
            eps_diluted: 6.0,
            earnings_payout: 0.183,
            revenue: 95_000_000_000,
            free_cash_flow: 18_500_000_000,
            dividends_paid: 3_200_000_000,
            fcf_payout: 0.18,
            cash_coverage: 5.78,
            retained_cash: 15_300_000_000,
            gross_debt: 41_000_000_000,
            net_debt_to_ebitda: 0.46,
            current_ratio: 1.05,
            interest_coverage: 39.2,
            fcf_margin: 0.195,
          },
        },
        {
          periodEnd: '2025-09-27',
          filingDate: '2025-11-01',
          accessionNumber: '0000320193-25-000106',
          metrics: {
            dps_declared: 1.26,
            eps_diluted: 6.42,
            earnings_payout: 0.196,
            revenue: 100_000_000_000,
            free_cash_flow: 20_000_000_000,
            dividends_paid: 3_000_000_000,
            fcf_payout: 0.15,
            cash_coverage: 6.67,
            retained_cash: 17_000_000_000,
            gross_debt: 40_000_000_000,
            net_debt_to_ebitda: 0.4,
            current_ratio: 1.07,
            interest_coverage: 42.5,
            fcf_margin: 0.27,
          },
        },
      ],
      warnings: ['History is currently annual-only.'],
    });

    vi.mocked(dividendApi.getAlerts).mockResolvedValue({
      company: {
        cik: '0000320193',
        ticker: 'AAPL',
        name: 'Apple Inc.',
        sector: 'Technology',
        fiscalYearEnd: '0930',
        lastFilingDate: '2026-02-01',
        dataFreshness: '2026-03-14T12:00:00Z',
      },
      activeOnly: false,
      activeAlerts: [],
      historicalAlerts: [
        {
          id: 'fcf-payout',
          severity: 'MEDIUM',
          title: 'Elevated cash payout ratio',
          description: 'Dividends consumed most of free cash flow in an earlier annual period.',
          periodEnd: '2023-09-30',
          filingDate: '2023-11-02',
          accessionNumber: '0000320193-23-000100',
          active: false,
        },
      ],
      warnings: [],
    });

    vi.mocked(dividendApi.getEvents).mockResolvedValue({
      company: {
        cik: '0000320193',
        ticker: 'AAPL',
        name: 'Apple Inc.',
        sector: 'Technology',
        fiscalYearEnd: '0930',
        lastFilingDate: '2026-02-01',
        dataFreshness: '2026-03-14T12:00:00Z',
      },
      events: [
        {
          id: '0000320193-26-000010:DECLARATION:ITEM_8.01:2026-01-20',
          eventType: 'DECLARATION',
          formType: '8-K',
          accessionNumber: '0000320193-26-000010',
          filedDate: '2026-01-20',
          declarationDate: '2026-01-20',
          recordDate: '2026-02-10',
          payableDate: '2026-02-13',
          amountPerShare: 0.25,
          dividendType: 'QUARTERLY',
          confidence: 'HIGH',
          extractionMethod: 'REGEX',
          sourceSection: 'ITEM_8.01',
          textSnippet: 'The Board of Directors declared a quarterly cash dividend of $0.25 per share.',
          policyLanguage: null,
          url: 'https://www.sec.gov/Archives/edgar/data/320193/8k.htm',
        },
      ],
      warnings: [],
    });

    vi.mocked(dividendApi.getEvidence).mockResolvedValue({
      company: {
        cik: '0000320193',
        ticker: 'AAPL',
        name: 'Apple Inc.',
        sector: 'Technology',
        fiscalYearEnd: '0930',
        lastFilingDate: '2026-02-01',
        dataFreshness: '2026-03-14T12:00:00Z',
      },
      filing: {
        formType: '10-K',
        accessionNumber: '0000320193-25-000106',
        filingDate: '2025-11-01',
        url: 'https://www.sec.gov/Archives/edgar/data/320193/annual.htm',
      },
      highlights: [
        {
          id: '0000320193-25-000106:POLICY_CHANGE:DOCUMENT_TEXT:2025-11-01',
          eventType: 'POLICY_CHANGE',
          confidence: 'LOW',
          sourceSection: 'DOCUMENT_TEXT',
          snippet: 'Future dividends remain at the discretion of the board.',
          policyLanguage: 'Future dividends remain at the discretion of the board.',
        },
      ],
      cleanedText: 'Future dividends remain at the discretion of the board and depend on earnings and capital needs.',
      truncated: false,
      warnings: [],
    });

    vi.mocked(dividendApi.compare).mockResolvedValue({
      metrics: [
        {
          id: 'fcf_payout',
          label: 'Free Cash Flow Payout Ratio',
          unit: 'percent',
          formatHint: 'percent',
          group: 'overview',
          description: 'Dividends paid divided by free cash flow.',
        },
        {
          id: 'dps_cagr_5y',
          label: 'Dividend CAGR (5Y)',
          unit: 'percent',
          formatHint: 'percent',
          group: 'overview',
          description: 'Five-year dividend growth.',
        },
        {
          id: 'net_debt_to_ebitda',
          label: 'Net Debt To EBITDA',
          unit: 'x',
          formatHint: 'multiple',
          group: 'overview',
          description: 'Net debt divided by EBITDA proxy.',
        },
      ],
      companies: [
        {
          company: {
            cik: '0000320193',
            ticker: 'AAPL',
            name: 'Apple Inc.',
            sector: 'Technology',
            fiscalYearEnd: '0930',
            lastFilingDate: '2026-02-01',
            dataFreshness: '2026-03-14T12:00:00Z',
          },
          viability: {
            rating: 'SAFE',
            activeAlerts: 0,
            score: 92,
          },
          values: {
            fcf_payout: 0.15,
            dps_cagr_5y: 0.058,
            net_debt_to_ebitda: 0.4,
          },
          warnings: [],
        },
        {
          company: {
            cik: '0000789019',
            ticker: 'MSFT',
            name: 'Microsoft Corp.',
            sector: 'Technology',
            fiscalYearEnd: '0630',
            lastFilingDate: '2026-01-30',
            dataFreshness: '2026-03-14T12:00:00Z',
          },
          viability: {
            rating: 'STABLE',
            activeAlerts: 1,
            score: 78,
          },
          values: {
            fcf_payout: 0.32,
            dps_cagr_5y: 0.091,
            net_debt_to_ebitda: 0.8,
          },
          warnings: [],
        },
        {
          company: {
            cik: '0000200406',
            ticker: 'JNJ',
            name: 'Johnson & Johnson',
            sector: 'Healthcare',
            fiscalYearEnd: '1231',
            lastFilingDate: '2026-02-15',
            dataFreshness: '2026-03-14T12:00:00Z',
          },
          viability: {
            rating: 'SAFE',
            activeAlerts: 0,
            score: 88,
          },
          values: {
            fcf_payout: 0.44,
            dps_cagr_5y: 0.057,
            net_debt_to_ebitda: 1.1,
          },
          warnings: [],
        },
      ],
      warnings: [],
    });

    vi.mocked(dividendApi.screen).mockResolvedValue({
      metrics: [
        {
          id: 'fcf_payout',
          label: 'Free Cash Flow Payout Ratio',
          unit: 'percent',
          formatHint: 'percent',
          group: 'overview',
          description: 'Dividends paid divided by free cash flow.',
        },
        {
          id: 'dps_cagr_5y',
          label: 'Dividend CAGR (5Y)',
          unit: 'percent',
          formatHint: 'percent',
          group: 'overview',
          description: 'Five-year dividend growth.',
        },
        {
          id: 'current_ratio',
          label: 'Current Ratio',
          unit: 'x',
          formatHint: 'multiple',
          group: 'overview',
          description: 'Current assets divided by current liabilities.',
        },
        {
          id: 'net_debt_to_ebitda',
          label: 'Net Debt To EBITDA',
          unit: 'x',
          formatHint: 'multiple',
          group: 'overview',
          description: 'Net debt divided by EBITDA proxy.',
        },
        {
          id: 'dividend_yield',
          label: 'Dividend Yield',
          unit: 'percent',
          formatHint: 'percent',
          group: 'overview',
          description: 'Estimated dividend yield using stored market price.',
        },
      ],
      results: {
        content: [
          {
            company: {
              cik: '0000320193',
              ticker: 'AAPL',
              name: 'Apple Inc.',
              sector: 'Technology',
              fiscalYearEnd: '0930',
              lastFilingDate: '2026-02-01',
              dataFreshness: '2026-03-14T12:00:00Z',
            },
            viability: {
              rating: 'SAFE',
              activeAlerts: 0,
              score: 92,
            },
            values: {
              fcf_payout: 0.15,
              dps_cagr_5y: 0.058,
              current_ratio: 1.07,
              net_debt_to_ebitda: 0.4,
              dividend_yield: 0.018,
            },
            warnings: [],
          },
          {
            company: {
              cik: '0000789019',
              ticker: 'MSFT',
              name: 'Microsoft Corp.',
              sector: 'Technology',
              fiscalYearEnd: '0630',
              lastFilingDate: '2026-01-30',
              dataFreshness: '2026-03-14T12:00:00Z',
            },
            viability: {
              rating: 'STABLE',
              activeAlerts: 1,
              score: 84,
            },
            values: {
              fcf_payout: 0.32,
              dps_cagr_5y: 0.091,
              current_ratio: 1.35,
              net_debt_to_ebitda: 0.8,
              dividend_yield: 0.009,
            },
            warnings: ['Stored market data is slightly stale.'],
          },
        ],
        page: 0,
        size: 12,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
        hasNext: false,
        hasPrevious: false,
      },
      candidatesEvaluated: 8,
      warnings: [],
    });
  });

  it('loads overview, history, and alerts APIs and renders the backend-driven dashboard', async () => {
    render(
      <MemoryRouter initialEntries={['/?company=AAPL']}>
        <DividendViabilityDashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Apple Inc.' })).toBeInTheDocument();
    expect(dividendApi.getOverview).toHaveBeenCalledWith('AAPL');
    expect(dividendApi.getHistory).toHaveBeenCalledWith('AAPL', {
      metrics: [
        'dps_declared',
        'eps_diluted',
        'earnings_payout',
        'revenue',
        'free_cash_flow',
        'dividends_paid',
        'fcf_payout',
        'cash_coverage',
        'retained_cash',
        'gross_debt',
        'net_debt_to_ebitda',
        'current_ratio',
        'interest_coverage',
        'fcf_margin',
      ],
      periods: 'FY',
      years: 15,
    });
    expect(dividendApi.getAlerts).toHaveBeenCalledWith('AAPL', { active: false });
    expect(dividendApi.getEvents).toHaveBeenCalledWith('AAPL');
    expect(dividendApi.getEvidence).not.toHaveBeenCalled();
    expect(dividendApi.compare).not.toHaveBeenCalled();
    expect(dividendApi.screen).not.toHaveBeenCalled();
    expect(screen.getByText('Dividend / Share')).toBeInTheDocument();
    expect(screen.getByText('DPS trend: Rising')).toBeInTheDocument();
    expect(screen.getByText('Alert history')).toBeInTheDocument();
    expect(screen.getByText('Profitability and durability')).toBeInTheDocument();
    expect(screen.getByText('Annual history table')).toBeInTheDocument();
    expect(screen.getByText('Elevated cash payout ratio')).toBeInTheDocument();
    expect(screen.getByText('Filing-text event timeline')).toBeInTheDocument();
    expect(screen.getByText('DECLARATION')).toBeInTheDocument();
    expect(screen.getByText(/The Board of Directors declared a quarterly cash dividend/i)).toBeInTheDocument();
    expect(screen.getByText('Latest annual report')).toBeInTheDocument();
    expect(screen.getByText('Latest 8-K filing')).toBeInTheDocument();
    expect(screen.getByText('Coverage notes')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open company fundamentals/i })).toHaveAttribute(
      'href',
      '/companies/0000320193/fundamentals',
    );
  });

  it('loads filing evidence on demand and renders the inline evidence viewer', async () => {
    render(
      <MemoryRouter initialEntries={['/?company=AAPL']}>
        <DividendViabilityDashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Apple Inc.' })).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: 'View evidence' })[0]);

    expect(dividendApi.getEvidence).toHaveBeenCalledWith('0000320193', '0000320193-25-000106');
    expect(await screen.findByText('Filing Evidence Viewer')).toBeInTheDocument();
    expect(await screen.findByText(/Future dividends remain at the discretion of the board and depend on earnings and capital needs\./i)).toBeInTheDocument();
    expect(screen.getByText('Extracted highlights')).toBeInTheDocument();
    expect(screen.getByText('Cleaned filing text')).toBeInTheDocument();
  });

  it('runs peer comparison on demand and renders the comparison table', async () => {
    render(
      <MemoryRouter initialEntries={['/?company=AAPL']}>
        <DividendViabilityDashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Apple Inc.' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Peer tickers'), { target: { value: 'MSFT, JNJ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Compare peers' }));

    expect(dividendApi.compare).toHaveBeenCalledWith(['AAPL', 'MSFT', 'JNJ'], {
      metrics: ['fcf_payout', 'dps_cagr_5y', 'net_debt_to_ebitda'],
    });
    expect(await screen.findByText('Free Cash Flow Payout Ratio')).toBeInTheDocument();
    expect(screen.getByText('Microsoft Corp.')).toBeInTheDocument();
    expect(screen.getByText('Johnson & Johnson')).toBeInTheDocument();
    expect(screen.getByText('32.0%')).toBeInTheDocument();
    expect(screen.getByText('0.80x')).toBeInTheDocument();
  });

  it('runs the dividend screener on demand and renders ranked results', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <DividendViabilityDashboard />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Screen search'), { target: { value: 'cloud' } });
    fireEvent.change(screen.getByLabelText('Sector filter'), { target: { value: 'Technology' } });
    fireEvent.change(screen.getByLabelText('Max FCF payout (%)'), { target: { value: '55' } });
    fireEvent.change(screen.getByLabelText('Min current ratio'), { target: { value: '1.25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Run screener' }));

    expect(dividendApi.screen).toHaveBeenCalledWith({
      searchTerm: 'cloud',
      filters: {
        metrics: {
          fcf_payout: { max: 55 },
          current_ratio: { min: 1.25 },
        },
        viabilityRatings: ['SAFE', 'STABLE'],
        sectors: ['Technology'],
      },
      metrics: ['fcf_payout', 'dps_cagr_5y', 'current_ratio', 'net_debt_to_ebitda', 'dividend_yield'],
      sort: 'score',
      direction: 'DESC',
      page: 0,
      size: 12,
      candidateLimit: 40,
    });

    expect(await screen.findByText('Microsoft Corp.')).toBeInTheDocument();
    expect(screen.getByText('2 matches')).toBeInTheDocument();
    expect(screen.getByText('8 candidates evaluated')).toBeInTheDocument();
    expect(screen.getByText('Dividend Yield')).toBeInTheDocument();
    expect(screen.getByText('1.35x')).toBeInTheDocument();
    expect(screen.getByText('Stored market data is slightly stale.')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('button', { name: 'Analyze' })).toHaveLength(2);
  });
});

