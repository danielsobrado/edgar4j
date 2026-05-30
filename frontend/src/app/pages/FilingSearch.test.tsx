import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FilingSearch } from './FilingSearch';

vi.mock('../hooks', () => ({
  useFilings: vi.fn(),
  useFormTypes: vi.fn(),
}));

vi.mock('../hooks/useExport', () => ({
  useExport: vi.fn(),
}));

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    remoteEdgarApi: {
      ...actual.remoteEdgarApi,
      searchFilings: vi.fn(),
    },
  };
});

vi.mock('../store', () => ({
  useSearchStore: vi.fn(),
}));

import { remoteEdgarApi } from '../api';
import { useFilings, useFormTypes } from '../hooks';
import { useExport } from '../hooks/useExport';
import { useSearchStore } from '../store';

const mockSearch = vi.fn();
const mockRefresh = vi.fn();
const mockRefreshExport = vi.fn();
const mockAddSearch = vi.fn();
const mockSearchTickers = vi.fn();
const mockRemoteSearch = vi.fn();

const sampleFilings = {
  content: [
    {
      id: 'f1',
      companyName: 'Acme Corporation',
      ticker: 'ACME',
      cik: '0001234567',
      formType: '10-K',
      filingDate: '2026-05-20',
      accessionNumber: '0001234567-26-000001',
      primaryDocument: 'primary.htm',
      primaryDocDescription: 'Annual report',
      isXBRL: true,
      isInlineXBRL: false,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  size: 25,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

const remoteResult = {
  formType: '4',
  dateFrom: '2026-05-01',
  dateTo: '2026-05-31',
  totalMatches: 10,
  returnedMatches: 10,
  uniqueCompanyCount: 5,
  truncated: false,
  searchedDateCount: 12,
  availableDateCount: 10,
  unavailableDateCount: 2,
  filings: [
    {
      cik: '0001234567',
      companyName: 'Acme Corporation',
      formType: '4',
      filingDate: '2026-05-20',
      accessionNumber: '0001234567-26-000001',
      archivePath: '123/filing',
      filingUrl: 'https://www.sec.gov/primary',
    },
  ],
};

describe('FilingSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockSearch.mockReset();
    mockRemoteSearch.mockReset();
    mockAddSearch.mockReset();

    vi.mocked(useFilings).mockReturnValue({
      filings: sampleFilings.content,
      loading: false,
      error: null,
      totalElements: sampleFilings.totalElements,
      totalPages: sampleFilings.totalPages,
      search: mockSearch,
    } as never);

    vi.mocked(useFormTypes).mockReturnValue({
      formTypes: [
        { code: '10-K', name: 'Annual Report' },
        { code: '4', name: 'Form 4' },
      ],
      loading: false,
      error: null,
    } as never);

    vi.mocked(useExport).mockReturnValue({
      exportToCsv: mockRefreshExport,
      exportToJson: mockRefreshExport,
      loading: false,
      error: null,
    } as never);

    vi.mocked(useSearchStore).mockReturnValue({
      addSearch: mockAddSearch,
    } as never);

    mockSearchTickers.mockResolvedValue([]);
    mockRemoteSearch.mockResolvedValue(remoteResult);
    mockRefresh.mockResolvedValue(undefined);
    vi.mocked(remoteEdgarApi.searchFilings).mockImplementation(mockRemoteSearch);
  });

  it('shows the search shell before submitting', () => {
    render(
      <MemoryRouter initialEntries={['/search']}>
        <Routes>
          <Route path="/search" element={<FilingSearch />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Filing Search' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Company \/ Ticker \/ CIK/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Search' })).toBeInTheDocument();
    expect(screen.queryByText('Local Search Results')).not.toBeInTheDocument();
  });

  it('runs local search and records query history', async () => {
    render(
      <MemoryRouter initialEntries={['/search']}>
        <Routes>
          <Route path="/search" element={<FilingSearch />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByRole('textbox', { name: /Company \/ Ticker \/ CIK/ }), {
      target: { value: 'Acme' },
    });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(mockSearch).toHaveBeenCalledWith(expect.objectContaining({
        companyName: 'Acme',
        formTypes: ['4'],
        page: 0,
      }));
    });
    expect(mockAddSearch).toHaveBeenCalledWith('Acme', 'filing-search');
    expect(screen.getByText(/Local Search Results/)).toBeInTheDocument();
  });

  it('requires a form type for enabled remote search', async () => {
    render(
      <MemoryRouter initialEntries={['/search']}>
        <Routes>
          <Route path="/search" element={<FilingSearch />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByRole('textbox', { name: /Company \/ Ticker \/ CIK/ }), {
      target: { value: 'AAPL' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: /Also search remote SEC filings/ }));

    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Form Type is required for remote SEC search.')).toBeInTheDocument();
    expect(mockRemoteSearch).not.toHaveBeenCalled();
  });

  it('loads remote SEC results when remote search is enabled with a form type', async () => {
    render(
      <MemoryRouter initialEntries={['/search']}>
        <Routes>
          <Route path="/search" element={<FilingSearch />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('checkbox', { name: /Also search remote SEC filings/ }));
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '4' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Company \/ Ticker \/ CIK/ }), {
      target: { value: 'AAPL' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(mockSearch).toHaveBeenCalled();
      expect(mockRemoteSearch).toHaveBeenCalledWith(expect.objectContaining({
        ticker: 'AAPL',
        formType: '4',
      }));
    });

    expect(await screen.findByText('Remote SEC Results (10 filings)')).toBeInTheDocument();
  });

  it('auto-runs a search when autoSearch query flag is present', async () => {
    render(
      <MemoryRouter initialEntries={['/search?autoSearch=1&q=Acme&formType=4']}>
        <Routes>
          <Route path="/search" element={<FilingSearch />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(mockSearch).toHaveBeenCalledWith(expect.objectContaining({
        companyName: 'Acme',
        formTypes: ['4'],
      }));
    });
  });
});

