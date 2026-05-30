import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Downloads } from './Downloads';

vi.mock('../hooks', () => ({
  useDownloads: vi.fn(),
}));

vi.mock('../store/notificationStore', () => ({
  showError: vi.fn(),
  showSuccess: vi.fn(),
}));

import { useDownloads } from '../hooks';
import { showSuccess } from '../store/notificationStore';
import type { DownloadJob } from '../api';

const mockDownloadBulk = vi.fn();
const mockRefresh = vi.fn();

function pendingJob(overrides: Partial<DownloadJob> = {}): DownloadJob {
  return {
    id: 'job-1',
    type: 'BULK_COMPANY_FACTS',
    description: 'Download Company Facts XBRL Archive',
    status: 'PENDING',
    progress: 0,
    startedAt: '2026-05-30T08:00:00',
    filesDownloaded: 0,
    totalFiles: 0,
    ...overrides,
  };
}

function mockDownloads(overrides: Partial<ReturnType<typeof useDownloads>> = {}) {
  vi.mocked(useDownloads).mockReturnValue({
    jobs: [],
    activeJobs: [],
    summary: null,
    loading: false,
    summaryLoading: false,
    actionLoading: false,
    error: null,
    summaryError: null,
    actionError: null,
    downloadTickers: vi.fn(),
    downloadSubmissions: vi.fn(),
    downloadBulk: mockDownloadBulk,
    cancelJob: vi.fn(),
    refresh: mockRefresh,
    ...overrides,
  } as never);
}

describe('Downloads bulk archive controls', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDownloads();
    mockDownloadBulk.mockResolvedValue(pendingJob({ id: 'job-bulk' }));
    mockRefresh.mockResolvedValue(undefined);
  });

  it('queues company facts as a backend job and refreshes jobs immediately', async () => {
    render(<Downloads />);

    fireEvent.click(screen.getAllByRole('button', { name: /Queue Download/i })[0]);

    await waitFor(() => {
      expect(mockDownloadBulk).toHaveBeenCalledWith('BULK_COMPANY_FACTS');
    });
    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
    expect(showSuccess).toHaveBeenCalledWith(
      'Download Queued',
      expect.stringContaining('job-bulk')
    );
  });

  it('queues all submissions as a backend job', async () => {
    mockDownloadBulk.mockResolvedValue(pendingJob({
      id: 'job-submissions',
      type: 'BULK_SUBMISSIONS',
      description: 'Download Bulk Submissions Archive',
    }));
    render(<Downloads />);

    fireEvent.click(screen.getAllByRole('button', { name: /Queue Download/i })[1]);

    await waitFor(() => {
      expect(mockDownloadBulk).toHaveBeenCalledWith('BULK_SUBMISSIONS');
    });
    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
    expect(showSuccess).toHaveBeenCalledWith(
      'Download Queued',
      expect.stringContaining('job-submissions')
    );
  });

  it('shows loading only on the bulk button being queued', async () => {
    let resolveDownload: (job: DownloadJob) => void = () => {};
    mockDownloadBulk.mockReturnValue(new Promise<DownloadJob>((resolve) => {
      resolveDownload = resolve;
    }));

    render(<Downloads />);

    const [companyFactsButton, submissionsButton] = screen.getAllByRole('button', { name: /Queue Download/i });
    fireEvent.click(companyFactsButton);

    await waitFor(() => {
      expect(companyFactsButton).toBeDisabled();
    });
    expect(submissionsButton).not.toBeDisabled();

    resolveDownload(pendingJob({ id: 'job-bulk' }));
  });

  it('shows completed bulk job source and saved archive path', () => {
    mockDownloads({
      jobs: [
        pendingJob({
          status: 'COMPLETED',
          progress: 100,
          filesDownloaded: 1,
          totalFiles: 1,
          outputPath: 'data/bulk-downloads/companyfacts.zip',
          sourceUrl: 'https://www.sec.gov/Archives/edgar/daily-index/xbrl/companyfacts.zip',
        }),
      ],
    });

    render(<Downloads />);

    expect(screen.getByText('Saved ZIP: data/bulk-downloads/companyfacts.zip')).toBeInTheDocument();
    expect(screen.getByText('Source URL: https://www.sec.gov/Archives/edgar/daily-index/xbrl/companyfacts.zip')).toBeInTheDocument();
    expect(screen.getByText('1 file imported or saved.')).toBeInTheDocument();
  });

  it('shows failed bulk job error and keeps retry available', () => {
    mockDownloads({
      jobs: [
        pendingJob({
          status: 'FAILED',
          error: 'SEC returned HTTP 403',
          completedAt: '2026-05-30T08:05:00',
        }),
      ],
    });

    render(<Downloads />);

    expect(screen.getAllByText(/SEC returned HTTP 403/).length).toBeGreaterThan(0);
    const failedJobCard = screen.getByText('BULK_COMPANY_FACTS').closest('.border');
    expect(failedJobCard).not.toBeNull();
    expect(within(failedJobCard as HTMLElement).getByRole('button', { name: /Retry/i })).toBeInTheDocument();
  });
});
