import { cleanup, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';

import { App } from './App';

vi.mock('./components/Layout', () => ({
  Layout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('./components/common/AppErrorBoundary', () => ({
  AppErrorBoundary: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('./pages/Dashboard', () => ({ Dashboard: () => <div>Dashboard route</div> }));
vi.mock('./pages/FilingSearch', () => ({ FilingSearch: () => <div>FilingSearch route</div> }));
vi.mock('./pages/FilingDetail', () => ({ FilingDetail: () => <div>FilingDetail route</div> }));
vi.mock('./pages/Companies', () => ({ Companies: () => <div>Companies route</div> }));
vi.mock('./pages/CompanyFundamentals', () => ({ CompanyFundamentals: () => <div>CompanyFundamentals route</div> }));
vi.mock('./pages/InsiderActivity', () => ({ InsiderActivityPage: () => <div>InsiderActivity route</div> }));
vi.mock('./pages/PoliticalTrades', () => ({ PoliticalTradesPage: () => <div>PoliticalTrades route</div> }));
vi.mock('./pages/DividendViabilityDashboard', () => ({ DividendViabilityDashboard: () => <div>DividendViability route</div> }));
vi.mock('./pages/Form13F', () => ({ Form13FPage: () => <div>Form13F route</div> }));
vi.mock('./pages/Form13DG', () => ({ Form13DGPage: () => <div>Form13DG route</div> }));
vi.mock('./pages/Form8K', () => ({ Form8KPage: () => <div>Form8K route</div> }));
vi.mock('./pages/Form3', () => ({ Form3Page: () => <div>Form3 route</div> }));
vi.mock('./pages/Form4', () => ({ Form4Page: () => <div>Form4 route</div> }));
vi.mock('./pages/Form5', () => ({ Form5Page: () => <div>Form5 route</div> }));
vi.mock('./pages/Form6K', () => ({ Form6KPage: () => <div>Form6K route</div> }));
vi.mock('./pages/Form20F', () => ({ Form20FPage: () => <div>Form20F route</div> }));
vi.mock('./pages/RemoteEdgar', () => ({ RemoteEdgar: () => <div>RemoteEdgar route</div> }));
vi.mock('./pages/Alerts', () => ({ Alerts: () => <div>Alerts route</div> }));
vi.mock('./pages/Downloads', () => ({ Downloads: () => <div>Downloads route</div> }));
vi.mock('./pages/UsaSpendingDownloads', () => ({ UsaSpendingDownloads: () => <div>UsaSpendingDownloads route</div> }));
vi.mock('./pages/Settings', () => ({ Settings: () => <div>Settings route</div> }));

const renderAppAt = (path: string) => {
  cleanup();
  window.history.pushState({}, '', path);
  return render(<App />);
};

describe('App', () => {
  it('renders route for dashboard', () => {
    renderAppAt('/');
    expect(screen.getByText('Dashboard route')).toBeInTheDocument();
  });

  it('renders filing routes', () => {
    renderAppAt('/search');
    expect(screen.getByText('FilingSearch route')).toBeInTheDocument();

    renderAppAt('/filing/0000320193-24-000001');
    expect(screen.getByText('FilingDetail route')).toBeInTheDocument();
  });

  it('renders analysis and form routes', () => {
    renderAppAt('/analysis/dividend-viability');
    expect(screen.getByText('DividendViability route')).toBeInTheDocument();

    renderAppAt('/companies/0000320193/fundamentals');
    expect(screen.getByText('CompanyFundamentals route')).toBeInTheDocument();

    renderAppAt('/form8k');
    expect(screen.getByText('Form8K route')).toBeInTheDocument();
  });

  it('renders remaining navigation routes', () => {
    renderAppAt('/insider-activity');
    expect(screen.getByText('InsiderActivity route')).toBeInTheDocument();

    renderAppAt('/political-trades');
    expect(screen.getByText('PoliticalTrades route')).toBeInTheDocument();

    renderAppAt('/alerts');
    expect(screen.getByText('Alerts route')).toBeInTheDocument();

    renderAppAt('/usaspending-downloads');
    expect(screen.getByText('UsaSpendingDownloads route')).toBeInTheDocument();
  });
});
