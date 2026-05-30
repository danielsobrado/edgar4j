import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RemoteEdgar } from './RemoteEdgar';

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    remoteEdgarApi: {
      ...actual.remoteEdgarApi,
      getTickers: vi.fn(),
      getSubmissionByCik: vi.fn(),
      searchFilings: vi.fn(),
    },
    downloadsApi: {
      ...actual.downloadsApi,
      getJobById: vi.fn(),
      downloadSubmissions: vi.fn(),
      downloadRemoteFilings: vi.fn(),
    },
  };
});

import { downloadsApi, remoteEdgarApi } from '../api';

const mockGetTickers = vi.fn();
const mockGetSubmission = vi.fn();
const mockSearchFilings = vi.fn();

describe('RemoteEdgar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetTickers.mockResolvedValue([
      {
        cik: '0001234567',
        ticker: 'ACME',
        name: 'Acme Holdings',
        exchange: 'NASDAQ',
      },
    ]);
    mockGetSubmission.mockResolvedValue({
      cik: '0001234567',
      companyName: 'Acme Holdings',
      recentFilingsCount: 1,
      recentFilings: [
        {
          accessionNumber: '0001234567-26-000001',
          filingDate: '2026-05-20',
          formType: '4',
          primaryDocument: 'primary.txt',
          reportDate: '2026-05-20',
          primaryDocDescription: 'Form 4',
        },
      ],
    });
    mockSearchFilings.mockResolvedValue({
      formType: '4',
      dateFrom: '2026-05-01',
      dateTo: '2026-05-31',
      totalMatches: 1,
      returnedMatches: 1,
      uniqueCompanyCount: 1,
      truncated: false,
      searchedDateCount: 1,
      availableDateCount: 1,
      unavailableDateCount: 0,
      filings: [
        {
          cik: '0001234567',
          companyName: 'Acme Holdings',
          formType: '4',
          filingDate: '2026-05-20',
          accessionNumber: '0001234567-26-000001',
          archivePath: '123',
          filingUrl: 'https://www.sec.gov/primary',
        },
      ],
    });

    vi.mocked(remoteEdgarApi.getTickers).mockImplementation(mockGetTickers);
    vi.mocked(remoteEdgarApi.getSubmissionByCik).mockImplementation(mockGetSubmission);
    vi.mocked(remoteEdgarApi.searchFilings).mockImplementation(mockSearchFilings);
    vi.mocked(downloadsApi.getJobById).mockResolvedValue({
      id: 'job-1',
      status: 'COMPLETED',
      progress: 100,
      type: 'SUBMISSIONS',
      startedAt: '2026-05-20T10:00:00Z',
      filesDownloaded: 1,
      totalFiles: 1,
    });
  });

  it('renders remote explorer sections', () => {
    render(
      <MemoryRouter initialEntries={['/remote-edgar']}>
        <Routes>
          <Route path="/remote-edgar" element={<RemoteEdgar />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Remote EDGAR Explorer' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Remote Tickers' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Remote Filing Search' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Remote Submissions by CIK' })).toBeInTheDocument();
  });

  it('searches remote tickers and renders returned list', async () => {
    render(
      <MemoryRouter initialEntries={['/remote-edgar']}>
        <Routes>
          <Route path="/remote-edgar" element={<RemoteEdgar />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText('Search ticker, name, or CIK'), {
      target: { value: 'ACME' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Search Tickers' }));

    await waitFor(() => expect(mockGetTickers).toHaveBeenCalledWith({
      source: 'all',
      search: 'ACME',
      limit: 50,
    }));

    expect(await screen.findByText('ACME')).toBeInTheDocument();
    expect(screen.getByText('Acme Holdings')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Explore' })).toBeInTheDocument();
  });

  it('enforces a form type before filing search', async () => {
    render(
      <MemoryRouter initialEntries={['/remote-edgar']}>
        <Routes>
          <Route path="/remote-edgar" element={<RemoteEdgar />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText('e.g. 13F, 4, 10-K, SC 13D'), {
      target: { value: '' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Search Filings' }));

    expect(await screen.findByText('Form type is required')).toBeInTheDocument();
    expect(mockSearchFilings).not.toHaveBeenCalled();
  });

  it('searches remote SEC filings and displays results', async () => {
    render(
      <MemoryRouter initialEntries={['/remote-edgar']}>
        <Routes>
          <Route path="/remote-edgar" element={<RemoteEdgar />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText('e.g. 13F, 4, 10-K, SC 13D'), {
      target: { value: '4' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Search Filings' }));

    await waitFor(() => {
      expect(mockSearchFilings).toHaveBeenCalledWith(expect.objectContaining({
        formType: '4',
      }));
    });

    expect(await screen.findByText('Matching filings')).toBeInTheDocument();
    expect(screen.getByText('Acme Holdings')).toBeInTheDocument();
  });

  it('loads a remote submission by CIK', async () => {
    render(
      <MemoryRouter initialEntries={['/remote-edgar']}>
        <Routes>
          <Route path="/remote-edgar" element={<RemoteEdgar />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText('e.g. 0000789019'), {
      target: { value: '0001234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Load Submission' }));

    expect(await screen.findByText('Company')).toBeInTheDocument();
    expect(await screen.findByText('Acme Holdings')).toBeInTheDocument();
    expect(screen.getByText('0001234567')).toBeInTheDocument();
  });
});
