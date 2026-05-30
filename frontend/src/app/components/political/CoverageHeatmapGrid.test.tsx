import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { CoverageHeatmapGrid, CoverageSelection } from '../coverage/CoverageHeatmapGrid';

function ControlledGrid({
  year,
  getDay,
}: {
  year: number;
  getDay: (date: string) => { level: number; disabled: boolean; title: string };
}) {
  const [selection, setSelection] = React.useState<CoverageSelection | null>(null);

  return (
    <>
      <CoverageHeatmapGrid
        year={year}
        getDay={getDay}
        levelClass={() => 'bg-emerald-200'}
        selection={selection}
        onSelectionChange={setSelection}
      />
      {selection ? (
        <div data-testid="selection">{selection.start} to {selection.end}</div>
      ) : (
        <div data-testid="selection">none</div>
      )}
    </>
  );
}

describe('CoverageHeatmapGrid', () => {
  it('creates a range when dragging', () => {
    const year = 2026;
    const getDay = (date: string) => ({
      level: 0,
      disabled: false,
      title: date,
    });

    render(
      <ControlledGrid
        year={year}
        getDay={getDay}
      />,
    );

    fireEvent.mouseDown(screen.getByTitle('2026-01-01'));
    fireEvent.mouseEnter(screen.getByTitle('2026-01-03'));

    const selected = screen.getByTitle('2026-01-01');
    const middle = screen.getByTitle('2026-01-02');
    const end = screen.getByTitle('2026-01-03');

    expect(selected.className).toContain('ring-2');
    expect(middle.className).toContain('ring-2');
    expect(end.className).toContain('ring-2');
  });

  it('supports controlled selection flow and ignores disabled start cells', () => {
    const year = 2026;
    const getDay = (date: string) => ({
      level: 0,
      disabled: date === '2026-01-02',
      title: date,
    });

    render(
      <ControlledGrid
        year={year}
        getDay={getDay}
      />,
    );

    fireEvent.mouseDown(screen.getByTitle('2026-01-02'));
    expect(screen.getByTestId('selection')).toHaveTextContent('none');

    fireEvent.mouseDown(screen.getByTitle('2026-01-01'));
    fireEvent.mouseEnter(screen.getByTitle('2026-01-02'));
    expect(screen.getByTestId('selection')).toHaveTextContent('2026-01-01 to 2026-01-01');
  });

  it('stops extending the selection after mouseup', () => {
    const year = 2026;
    const getDay = (date: string) => ({
      level: 0,
      disabled: false,
      title: date,
    });

    render(
      <ControlledGrid
        year={year}
        getDay={getDay}
      />,
    );

    fireEvent.mouseDown(screen.getByTitle('2026-01-01'));
    fireEvent.mouseEnter(screen.getByTitle('2026-01-03'));
    expect(screen.getByTestId('selection')).toHaveTextContent('2026-01-01 to 2026-01-03');

    fireEvent.mouseUp(window);
    fireEvent.mouseEnter(screen.getByTitle('2026-01-05'));
    expect(screen.getByTestId('selection')).toHaveTextContent('2026-01-01 to 2026-01-03');
  });
});
