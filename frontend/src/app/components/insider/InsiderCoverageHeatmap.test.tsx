import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';

vi.mock('../../api', async () => {
  const actual = await vi.importActual<typeof import('../../api')>('../../api');
  return {
    ...actual,
    insiderActivityApi: {
      ...actual.insiderActivityApi,
      coverage: vi.fn(),
    },
  };
});

import { InsiderCoverageHeatmap } from './InsiderCoverageHeatmap';
import { insiderActivityApi } from '../../api';

describe('InsiderCoverageHeatmap', () => {
  const year = String(new Date().getFullYear());

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads coverage for the selected year and form', async () => {
    vi.mocked(insiderActivityApi.coverage).mockResolvedValue({
      form: '4',
      from: `${year}-01-01`,
      to: `${year}-12-31`,
      totalFilings: 3,
      days: [
        { date: `${year}-01-10`, count: 2 },
        { date: `${year}-01-11`, count: 1 },
      ],
    });

    render(
      <InsiderCoverageHeatmap
        onSelectRange={vi.fn()}
        onDownload={vi.fn()}
        downloading={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(insiderActivityApi.coverage).toHaveBeenCalledWith('4', `${year}-01-01`, `${year}-12-31`);
    });
  });

  it('filters screener and triggers selected sync mode', async () => {
    vi.mocked(insiderActivityApi.coverage).mockResolvedValue({
      form: '4',
      from: `${year}-01-01`,
      to: `${year}-12-31`,
      totalFilings: 3,
      days: [
        { date: `${year}-01-10`, count: 2 },
        { date: `${year}-01-11`, count: 1 },
      ],
    });

    const onSelectRange = vi.fn();
    const onDownload = vi.fn();

    render(
      <InsiderCoverageHeatmap
        onSelectRange={onSelectRange}
        onDownload={onDownload}
        downloading={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(insiderActivityApi.coverage).toHaveBeenCalled();
    });

    fireEvent.change(screen.getByRole('combobox', { name: 'Select sync mode' }), {
      target: { value: 'COMPANY' },
    });
    fireEvent.mouseDown(screen.getByTitle((content) => content.startsWith(`${year}-01-10`)));
    fireEvent.mouseEnter(screen.getByTitle((content) => content.startsWith(`${year}-01-11`)));

    fireEvent.click(screen.getByRole('button', { name: 'Filter screener to selection' }));
    expect(onSelectRange).toHaveBeenCalledWith(`${year}-01-10`, `${year}-01-11`);

    fireEvent.click(screen.getByRole('button', { name: 'Download to DB' }));
    expect(onDownload).toHaveBeenCalledWith('4', `${year}-01-10`, `${year}-01-11`, 'COMPANY');
  });

  it('shows an error message when coverage fails to load', async () => {
    vi.mocked(insiderActivityApi.coverage).mockRejectedValue(new Error('coverage failed'));

    render(
      <InsiderCoverageHeatmap
        onSelectRange={vi.fn()}
        onDownload={vi.fn()}
        downloading={false}
        refreshKey="0"
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('coverage failed')).toBeInTheDocument();
    });
  });
});
