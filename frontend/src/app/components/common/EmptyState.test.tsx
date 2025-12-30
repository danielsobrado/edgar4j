import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EmptyState } from './EmptyState';

describe('EmptyState', () => {
  it('renders with default generic type', () => {
    render(<EmptyState />);
    expect(screen.getByText('No data available')).toBeInTheDocument();
    expect(screen.getByText('There is no data to display at this time.')).toBeInTheDocument();
  });

  it('renders search empty state', () => {
    render(<EmptyState type="search" />);
    expect(screen.getByText('No results found')).toBeInTheDocument();
    expect(screen.getByText('Try adjusting your search criteria or filters.')).toBeInTheDocument();
  });

  it('renders filings empty state', () => {
    render(<EmptyState type="filings" />);
    expect(screen.getByText('No filings found')).toBeInTheDocument();
    expect(screen.getByText('There are no filings matching your criteria.')).toBeInTheDocument();
  });

  it('renders companies empty state', () => {
    render(<EmptyState type="companies" />);
    expect(screen.getByText('No companies found')).toBeInTheDocument();
    expect(screen.getByText('Try downloading company data from the Downloads page.')).toBeInTheDocument();
  });

  it('renders downloads empty state', () => {
    render(<EmptyState type="downloads" />);
    expect(screen.getByText('No download jobs')).toBeInTheDocument();
    expect(screen.getByText('Start a download to see job status here.')).toBeInTheDocument();
  });

  it('renders with custom title', () => {
    render(<EmptyState title="Custom Title" />);
    expect(screen.getByText('Custom Title')).toBeInTheDocument();
  });

  it('renders with custom message', () => {
    render(<EmptyState message="Custom message here" />);
    expect(screen.getByText('Custom message here')).toBeInTheDocument();
  });

  it('renders with custom title and message overriding type defaults', () => {
    render(
      <EmptyState
        type="search"
        title="No Search Results"
        message="Please try a different query"
      />
    );
    expect(screen.getByText('No Search Results')).toBeInTheDocument();
    expect(screen.getByText('Please try a different query')).toBeInTheDocument();
  });

  it('renders action button when action is provided', () => {
    const onClick = vi.fn();
    render(
      <EmptyState
        action={{ label: 'Add Item', onClick }}
      />
    );

    const button = screen.getByText('Add Item');
    expect(button).toBeInTheDocument();
  });

  it('calls action onClick when button is clicked', () => {
    const onClick = vi.fn();
    render(
      <EmptyState
        action={{ label: 'Refresh', onClick }}
      />
    );

    fireEvent.click(screen.getByText('Refresh'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not render action button when action is not provided', () => {
    render(<EmptyState />);
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBe(0);
  });

  it('has centered layout styling', () => {
    const { container } = render(<EmptyState />);
    expect(container.firstChild).toHaveClass('flex', 'flex-col', 'items-center', 'justify-center', 'text-center');
  });
});
