import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FormTypeBadge } from './FormTypeBadge';

describe('FormTypeBadge', () => {
  it('renders the form type text', () => {
    render(<FormTypeBadge formType="10-K" />);
    expect(screen.getByText('10-K')).toBeInTheDocument();
  });

  it('applies correct colors for 10-K', () => {
    render(<FormTypeBadge formType="10-K" />);
    const badge = screen.getByText('10-K');
    expect(badge).toHaveClass('bg-blue-100', 'text-blue-800');
  });

  it('applies correct colors for 10-Q', () => {
    render(<FormTypeBadge formType="10-Q" />);
    const badge = screen.getByText('10-Q');
    expect(badge).toHaveClass('bg-indigo-100', 'text-indigo-800');
  });

  it('applies correct colors for 8-K', () => {
    render(<FormTypeBadge formType="8-K" />);
    const badge = screen.getByText('8-K');
    expect(badge).toHaveClass('bg-orange-100', 'text-orange-800');
  });

  it('applies correct colors for DEF 14A', () => {
    render(<FormTypeBadge formType="DEF 14A" />);
    const badge = screen.getByText('DEF 14A');
    expect(badge).toHaveClass('bg-purple-100', 'text-purple-800');
  });

  it('applies correct colors for Form 4', () => {
    render(<FormTypeBadge formType="4" />);
    const badge = screen.getByText('4');
    expect(badge).toHaveClass('bg-pink-100', 'text-pink-800');
  });

  it('applies correct colors for S-1', () => {
    render(<FormTypeBadge formType="S-1" />);
    const badge = screen.getByText('S-1');
    expect(badge).toHaveClass('bg-green-100', 'text-green-800');
  });

  it('applies correct colors for 13F', () => {
    render(<FormTypeBadge formType="13F" />);
    const badge = screen.getByText('13F');
    expect(badge).toHaveClass('bg-yellow-100', 'text-yellow-800');
  });

  it('applies correct colors for SC 13D', () => {
    render(<FormTypeBadge formType="SC 13D" />);
    const badge = screen.getByText('SC 13D');
    expect(badge).toHaveClass('bg-red-100', 'text-red-800');
  });

  it('applies correct colors for SC 13G', () => {
    render(<FormTypeBadge formType="SC 13G" />);
    const badge = screen.getByText('SC 13G');
    expect(badge).toHaveClass('bg-gray-100', 'text-gray-800');
  });

  it('applies default gray colors for unknown form type', () => {
    render(<FormTypeBadge formType="UNKNOWN" />);
    const badge = screen.getByText('UNKNOWN');
    expect(badge).toHaveClass('bg-gray-100', 'text-gray-800');
  });

  it('has badge styling', () => {
    render(<FormTypeBadge formType="10-K" />);
    const badge = screen.getByText('10-K');
    expect(badge).toHaveClass('inline-flex', 'items-center', 'px-2.5', 'py-0.5', 'rounded', 'text-xs');
  });
});
