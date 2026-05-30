import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FilingDetail } from './FilingDetail';

vi.mock('../hooks', () => ({
  useFiling: vi.fn(),
}));

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    form4Api: {
      ...actual.form4Api,
      getByAccessionNumber: vi.fn(),
      downloadAndParse: vi.fn(),
    },
  };
});

import { form4Api } from '../api';
import { useFiling } from '../hooks';

const parsedForm4 = {
  id: 'pf-1',
  accessionNumber: '0001234567-26-000001',
  cik: '0001234567',
  issuerName: 'Acme Corporation',
  tradingSymbol: 'ACME',
  rptOwnerName: 'Jane Doe',
  officerTitle: 'Chief Executive Officer',
  acquiredDisposedCode: 'A',
  transactions: [
    {
      accessionNumber: '0001234567-26-000001',
      transactionType: 'NON_DERIVATIVE',
      securityTitle: 'Common Stock',
      transactionDate: '2026-05-20',
      acquiredDisposedCode: 'A',
      transactionShares: 10_000,
      transactionPricePerShare: 12.5,
      transactionValue: 125_000,
      sharesOwnedFollowingTransaction: 50_000,
    },
  ],
  transactionValue: 125_000,
  transactionDate: '2026-05-20',
};

const filing = {
  id: 'f-1',
  companyName: 'Acme Corporation',
  ticker: 'ACME',
  cik: '0001234567',
  formType: '4',
  filingDate: '2026-05-21',
  primaryDocument: 'filing.txt',
  primaryDocDescription: '4',
  accessionNumber: '0001234567-26-000001',
  isXBRL: false,
  isInlineXBRL: false,
  contentPreview: 'sample filing content',
  url: 'https://www.sec.gov/ixviewer',
  items: '1,2,3',
};

describe('FilingDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
      configurable: true,
    });
  });

  it('renders loading state when filing is still loading', () => {
    vi.mocked(useFiling).mockReturnValue({
      filing: null,
      loading: true,
      error: null,
    } as never);

    render(
      <MemoryRouter initialEntries={['/filing/123']}>
        <Routes>
          <Route path="/filing/:id" element={<FilingDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Loading filing details...')).toBeInTheDocument();
  });

  it('renders filing not found state', () => {
    vi.mocked(useFiling).mockReturnValue({
      filing: null,
      loading: false,
      error: null,
    } as never);

    render(
      <MemoryRouter initialEntries={['/filing/123']}>
        <Routes>
          <Route path="/filing/:id" element={<FilingDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Filing not found')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Back to Search' })).toBeInTheDocument();
  });

  it('renders an error when loading the filing fails', () => {
    vi.mocked(useFiling).mockReturnValue({
      filing: null,
      loading: false,
      error: 'Unable to fetch filing',
    } as never);

    render(
      <MemoryRouter initialEntries={['/filing/123']}>
        <Routes>
          <Route path="/filing/:id" element={<FilingDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Failed to load filing' })).toBeInTheDocument();
    expect(screen.getByText('Unable to fetch filing')).toBeInTheDocument();
  });

  it('loads and displays parsed Form 4 data, and copies accession number', async () => {
    vi.mocked(useFiling).mockReturnValue({
      filing,
      loading: false,
      error: null,
    } as never);
    vi.mocked(form4Api.getByAccessionNumber).mockResolvedValue(parsedForm4);

    render(
      <MemoryRouter initialEntries={['/filing/123']}>
        <Routes>
          <Route path="/filing/:id" element={<FilingDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Parsed Form 4 Data' })).toBeInTheDocument();
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument();
    expect(screen.getByText('Jane Doe')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Copy Accession Number/ }));

    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('0001234567-26-000001');
    });
  });
});

