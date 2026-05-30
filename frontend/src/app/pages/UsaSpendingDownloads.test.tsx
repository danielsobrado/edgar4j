import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { UsaSpendingDownloads } from './UsaSpendingDownloads';

vi.mock('../hooks', () => ({
  useDownloadJob: vi.fn(),
}));

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    downloadsApi: {
      ...actual.downloadsApi,
      downloadUsaSpendingAwards: vi.fn(),
      getJobs: vi.fn(),
      getUsaSpendingCsvPage: vi.fn(),
    },
  };
});

vi.mock('../store/notificationStore', () => ({
  showSuccess: vi.fn(),
  showError: vi.fn(),
}));

import { downloadsApi, UsoSpendingCoverage } from '../api';
import { useDownloadJob } from '../hooks';

const mockGetJobs = vi.fn();
const mockStartDownload = vi.fn();
const mockGetPage = vi.fn();
const mockRefresh = vi.fn();

const completedJob = {
  id: 'job-1',
  type: 'USA_SPENDING_AWARDS',
  description: 'USAspending awards',
  status: 'COMPLETED',
  progress: 100,
  startedAt: '2026-05-20T10:00:00Z',
  completedAt: '2026-05-20T10:01:00Z',
  totalFiles: 0,
  filesDownloaded: 0,
  estimatedSize: '0 MB',
};

const csvPage = {
  jobId: 'job-1',
  fileName: 'awards-2026-05-01.csv',
  headers: ['candidate', 'amount'],
  rows: [['Acme Holdings', '1000']],
  rowMatches: [[
    {
      cik: '0001234567',
      ticker: 'ACME',
      companyName: 'Acme Holdings',
      confidence: 99,
      sourceField: 'name',
      sourceValue: 'Acme Holdings',
      matchMethod: 'exact',
      marketCap: 25_000_000_000,
    },
  ]],
  page: 0,
  size: 25,
  totalRows: 1,
  totalPages: 1,
};

describe('UsaSpendingDownloads', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useDownloadJob).mockImplementation((jobId) => {
      return {
        job: jobId === 'job-1' ? completedJob : null,
        loading: false,
        error: null,
        refresh: mockRefresh,
      } as never;
    });
    mockGetJobs.mockResolvedValue([]);
    mockStartDownload.mockResolvedValue({ id: 'job-1' });
    mockGetPage.mockResolvedValue(csvPage);
    (downloadsApi.getJobs as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockGetJobs);
    (downloadsApi.downloadUsaSpendingAwards as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockStartDownload);
    (downloadsApi.getUsaSpendingCsvPage as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockGetPage);
  });

  it('renders the default heading and award controls', () => {
    render(<UsaSpendingDownloads />);

    expect(screen.getByRole('heading', { name: 'USAspending CSV Downloads' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument();
  });

  it('starts a download and loads preview rows when the job completes', async () => {
    render(<UsaSpendingDownloads />);

    const startDateInput = screen.getByLabelText('Start Date');
    const endDateInput = screen.getByLabelText('End Date');
    fireEvent.change(startDateInput, { target: { value: '2026-05-01' } });
    fireEvent.change(endDateInput, { target: { value: '2026-05-31' } });

    fireEvent.click(screen.getByRole('button', { name: 'Download CSV' }));

    await waitFor(() => {
      expect(mockStartDownload).toHaveBeenCalledWith({
        dateFrom: '2026-05-01',
        dateTo: '2026-05-31',
      });
    });

    await waitFor(() => {
      expect(mockGetPage).toHaveBeenCalledWith('job-1', 0, 25);
      expect(screen.getByText('CSV Preview')).toBeInTheDocument();
    });

    expect(await screen.findByText('Parsed from awards-2026-05-01.csv')).toBeInTheDocument();
    const acmeRows = await screen.findAllByText('Acme Holdings');
    expect(acmeRows.length).toBeGreaterThanOrEqual(2);
  });

  it('filters rows using candidate search', async () => {
    render(<UsaSpendingDownloads />);

    const startDateInput = screen.getByLabelText('Start Date');
    const endDateInput = screen.getByLabelText('End Date');
    fireEvent.change(startDateInput, { target: { value: '2026-05-01' } });
    fireEvent.change(endDateInput, { target: { value: '2026-05-31' } });
    fireEvent.click(screen.getByRole('button', { name: 'Download CSV' }));

    await waitFor(() => {
      expect(screen.getByText('CSV Preview')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('Company or awardee'), {
      target: { value: 'Nope' },
    });

    expect(await screen.findByText('No rows returned for the active filters.')).toBeInTheDocument();
    expect(screen.queryAllByText('Acme Holdings')).toHaveLength(0);

    fireEvent.change(screen.getByPlaceholderText('Company or awardee'), {
      target: { value: '' },
    });
    const restoredRows = await screen.findAllByText('Acme Holdings');
    expect(restoredRows.length).toBeGreaterThanOrEqual(2);
  });
});
