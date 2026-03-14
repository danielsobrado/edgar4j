import { describe, expect, it } from 'vitest';
import { render, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from './Layout';

describe('Layout', () => {
  it('renders the analysis trigger and icon-only utility links in the desktop nav', () => {
    const { container } = render(
      <MemoryRouter>
        <Layout>
          <div>Content</div>
        </Layout>
      </MemoryRouter>,
    );

    const desktopNav = container.querySelector('header nav');
    expect(desktopNav).not.toBeNull();

    const nav = within(desktopNav as HTMLElement);
    expect(nav.getByRole('button', { name: 'Analysis' })).toBeInTheDocument();
    expect(nav.getByTitle('Alerts')).toBeInTheDocument();
    expect(nav.getByTitle('Downloads')).toBeInTheDocument();
    expect(nav.getByTitle('Settings')).toBeInTheDocument();
    expect(nav.queryByRole('link', { name: 'Insider Buys' })).not.toBeInTheDocument();
  });

  it('marks the analysis dropdown active on insider analysis routes', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/insider-purchases']}>
        <Layout>
          <div>Content</div>
        </Layout>
      </MemoryRouter>,
    );

    const desktopNav = container.querySelector('header nav');
    expect(desktopNav).not.toBeNull();

    const nav = within(desktopNav as HTMLElement);
    expect(nav.getByRole('button', { name: 'Analysis' })).toHaveClass('bg-white/10');
  });
});
