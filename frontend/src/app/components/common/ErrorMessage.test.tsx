import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorMessage, ErrorBanner, ErrorPage } from './ErrorMessage';

describe('ErrorMessage', () => {
  it('renders with message', () => {
    render(<ErrorMessage message="Something went wrong" />);
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
  });

  it('renders with default title', () => {
    render(<ErrorMessage message="Test error" />);
    expect(screen.getByText('Error')).toBeInTheDocument();
  });

  it('renders with custom title', () => {
    render(<ErrorMessage title="Custom Error" message="Test error" />);
    expect(screen.getByText('Custom Error')).toBeInTheDocument();
  });

  it('renders retry button when onRetry is provided', () => {
    const onRetry = vi.fn();
    render(<ErrorMessage message="Test error" onRetry={onRetry} />);

    const retryButton = screen.getByText('Try again');
    expect(retryButton).toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', () => {
    const onRetry = vi.fn();
    render(<ErrorMessage message="Test error" onRetry={onRetry} />);

    fireEvent.click(screen.getByText('Try again'));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('does not render retry button when onRetry is not provided', () => {
    render(<ErrorMessage message="Test error" />);
    expect(screen.queryByText('Try again')).not.toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = render(
      <ErrorMessage message="Test error" className="custom-class" />
    );
    expect(container.firstChild).toHaveClass('custom-class');
  });

  it('has error styling', () => {
    const { container } = render(<ErrorMessage message="Test error" />);
    expect(container.firstChild).toHaveClass('bg-red-50', 'border-red-200');
  });
});

describe('ErrorBanner', () => {
  it('renders with message', () => {
    render(<ErrorBanner message="Server error occurred" />);
    expect(screen.getByText('Server error occurred')).toBeInTheDocument();
  });

  it('renders dismiss button when onDismiss is provided', () => {
    const onDismiss = vi.fn();
    render(<ErrorBanner message="Error" onDismiss={onDismiss} />);

    const dismissButton = document.querySelector('button');
    expect(dismissButton).toBeInTheDocument();
  });

  it('calls onDismiss when dismiss button is clicked', () => {
    const onDismiss = vi.fn();
    render(<ErrorBanner message="Error" onDismiss={onDismiss} />);

    const dismissButton = document.querySelector('button');
    fireEvent.click(dismissButton!);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('does not render dismiss button when onDismiss is not provided', () => {
    render(<ErrorBanner message="Error" />);
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBe(0);
  });

  it('has banner styling', () => {
    const { container } = render(<ErrorBanner message="Error" />);
    expect(container.firstChild).toHaveClass('bg-red-500', 'text-white');
  });
});

describe('ErrorPage', () => {
  it('renders with message', () => {
    render(<ErrorPage message="Page failed to load" />);
    expect(screen.getByText('Page failed to load')).toBeInTheDocument();
  });

  it('renders with default title', () => {
    render(<ErrorPage message="Test error" />);
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
  });

  it('renders with custom title', () => {
    render(<ErrorPage title="404 Not Found" message="Page not found" />);
    expect(screen.getByText('404 Not Found')).toBeInTheDocument();
  });

  it('renders retry button when onRetry is provided', () => {
    const onRetry = vi.fn();
    render(<ErrorPage message="Test error" onRetry={onRetry} />);

    const retryButton = screen.getByText('Try again');
    expect(retryButton).toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', () => {
    const onRetry = vi.fn();
    render(<ErrorPage message="Test error" onRetry={onRetry} />);

    fireEvent.click(screen.getByText('Try again'));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('has minimum height styling', () => {
    const { container } = render(<ErrorPage message="Error" />);
    expect(container.firstChild).toHaveClass('min-h-[400px]');
  });
});
