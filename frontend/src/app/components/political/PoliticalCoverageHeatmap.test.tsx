import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';

vi.mock('../../api', async () => {
  const actual = await vi.importActual<typeof import('../../api')>('../../api');
  return {
    ...actual,
    politicalTradesApi: {
      ...actual.politicalTradesApi,
      coverage: vi.fn(),
    },
  };
});

import { PoliticalCoverageHeatmap } from './PoliticalCoverageHeatmap';
import { politicalTradesApi } from '../../api';

describe('PoliticalCoverageHeatmap', () => {
  const year = String(new Date().getFullYear());

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads and displays coverage for the selected year', async () => {
    vi.mocked(politicalTradesApi.coverage).mockResolvedValue({
      from: `${year}-01-01`,
      to: `${year}-12-31`,
      totalTrades: 3,
      days: [
        { date: `${year}-01-10`, count: 2 },
        { date: `${year}-01-11`, count: 1 },
      ],
    });

    render(
      <PoliticalCoverageHeatmap
        onSelectRange={vi.fn()}
        onSync={vi.fn()}
        syncing={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(politicalTradesApi.coverage).toHaveBeenCalledWith(`${year}-01-01`, `${year}-12-31`);
    });
    expect(screen.getByText('Data Coverage')).toBeInTheDocument();
    expect(screen.getByText(/Cached disclosures per day, shaded by volume/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Chunk pages')).toHaveValue(5);
    expect(screen.getByLabelText('Pause sec')).toHaveValue(2);
    expect(screen.getByRole('button', { name: 'Filter to selection' })).toBeInTheDocument();
  });

  it('exposes sync throttle controls', async () => {
    vi.mocked(politicalTradesApi.coverage).mockResolvedValue({
      from: `${year}-01-01`,
      to: `${year}-12-31`,
      totalTrades: 0,
      days: [],
    });

    const onSyncChunkPagesChange = vi.fn();
    const onSyncPauseSecondsChange = vi.fn();

    render(
      <PoliticalCoverageHeatmap
        onSelectRange={vi.fn()}
        onSync={vi.fn()}
        syncing={false}
        refreshKey="0"
        syncChunkPages={7}
        syncPauseSeconds={3}
        onSyncChunkPagesChange={onSyncChunkPagesChange}
        onSyncPauseSecondsChange={onSyncPauseSecondsChange}
      />,
    );

    await waitFor(() => {
      expect(politicalTradesApi.coverage).toHaveBeenCalled();
    });

    fireEvent.change(screen.getByLabelText('Chunk pages'), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText('Pause sec'), { target: { value: '4' } });

    expect(onSyncChunkPagesChange).toHaveBeenCalledWith(10);
    expect(onSyncPauseSecondsChange).toHaveBeenCalledWith(4);
  });

  it('passes selected date range and starts sync when actions are clicked', async () => {
    vi.mocked(politicalTradesApi.coverage).mockResolvedValue({
      from: `${year}-01-01`,
      to: `${year}-12-31`,
      totalTrades: 3,
      days: [
        { date: `${year}-01-10`, count: 2 },
        { date: `${year}-01-11`, count: 1 },
      ],
    });

    const onSelectRange = vi.fn();
    const onSync = vi.fn();

    render(
      <PoliticalCoverageHeatmap
        onSelectRange={onSelectRange}
        onSync={onSync}
        syncing={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(politicalTradesApi.coverage).toHaveBeenCalled();
    });

    fireEvent.mouseDown(screen.getByTitle((content) => content.startsWith(`${year}-01-10`)));
    fireEvent.mouseEnter(screen.getByTitle((content) => content.startsWith(`${year}-01-11`)));


    fireEvent.click(screen.getByRole('button', { name: 'Filter to selection' }));
    expect(onSelectRange).toHaveBeenCalledWith(`${year}-01-10`, `${year}-01-11`);

    fireEvent.click(screen.getByRole('button', { name: 'Sync now' }));
    expect(onSync).toHaveBeenCalledWith(`${year}-01-10`, `${year}-01-11`);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Sync now' })).toBeInTheDocument();
    });
  });

  it('shows an error message when coverage fails to load', async () => {
    vi.mocked(politicalTradesApi.coverage).mockRejectedValue(new Error('coverage failed'));

    render(
      <PoliticalCoverageHeatmap
        onSelectRange={vi.fn()}
        onSync={vi.fn()}
        syncing={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('coverage failed')).toBeInTheDocument();
    });
  });
});
