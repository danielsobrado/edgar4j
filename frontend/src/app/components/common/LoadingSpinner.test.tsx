import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingSpinner, LoadingOverlay, LoadingPage } from './LoadingSpinner';

describe('LoadingSpinner', () => {
  it('renders with default props', () => {
    render(<LoadingSpinner />);
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });

  it('renders with small size', () => {
    render(<LoadingSpinner size="sm" />);
    const spinner = document.querySelector('.w-4.h-4');
    expect(spinner).toBeInTheDocument();
  });

  it('renders with medium size (default)', () => {
    render(<LoadingSpinner size="md" />);
    const spinner = document.querySelector('.w-8.h-8');
    expect(spinner).toBeInTheDocument();
  });

  it('renders with large size', () => {
    render(<LoadingSpinner size="lg" />);
    const spinner = document.querySelector('.w-12.h-12');
    expect(spinner).toBeInTheDocument();
  });

  it('renders with custom text', () => {
    render(<LoadingSpinner text="Loading data..." />);
    expect(screen.getByText('Loading data...')).toBeInTheDocument();
  });

  it('does not render text when not provided', () => {
    render(<LoadingSpinner />);
    expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = render(<LoadingSpinner className="custom-class" />);
    expect(container.firstChild).toHaveClass('custom-class');
  });
});

describe('LoadingOverlay', () => {
  it('renders with default text', () => {
    render(<LoadingOverlay />);
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('renders with custom text', () => {
    render(<LoadingOverlay text="Processing..." />);
    expect(screen.getByText('Processing...')).toBeInTheDocument();
  });

  it('has overlay styling', () => {
    const { container } = render(<LoadingOverlay />);
    expect(container.firstChild).toHaveClass('absolute', 'inset-0');
  });
});

describe('LoadingPage', () => {
  it('renders with default text', () => {
    render(<LoadingPage />);
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('renders with custom text', () => {
    render(<LoadingPage text="Fetching data..." />);
    expect(screen.getByText('Fetching data...')).toBeInTheDocument();
  });

  it('has minimum height styling', () => {
    const { container } = render(<LoadingPage />);
    expect(container.firstChild).toHaveClass('min-h-[400px]');
  });
});
