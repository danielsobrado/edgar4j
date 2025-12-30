import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  const defaultProps = {
    page: 0,
    totalPages: 10,
    totalElements: 100,
    size: 10,
    onPageChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders pagination info text', () => {
    render(<Pagination {...defaultProps} />);
    expect(screen.getByText(/Showing 1 to 10 of 100 results/)).toBeInTheDocument();
  });

  it('calculates correct range for middle page', () => {
    render(<Pagination {...defaultProps} page={4} />);
    expect(screen.getByText(/Showing 41 to 50 of 100 results/)).toBeInTheDocument();
  });

  it('calculates correct range for last page', () => {
    render(<Pagination {...defaultProps} page={9} totalElements={95} />);
    expect(screen.getByText(/Showing 91 to 95 of 95 results/)).toBeInTheDocument();
  });

  it('renders page numbers', () => {
    render(<Pagination {...defaultProps} />);
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('highlights current page', () => {
    render(<Pagination {...defaultProps} page={2} />);
    const currentPageButton = screen.getByText('3');
    expect(currentPageButton).toHaveClass('bg-blue-600', 'text-white');
  });

  it('disables first/prev buttons on first page', () => {
    render(<Pagination {...defaultProps} page={0} />);
    const buttons = document.querySelectorAll('button');
    const firstButton = buttons[0];
    const prevButton = buttons[1];
    expect(firstButton).toBeDisabled();
    expect(prevButton).toBeDisabled();
  });

  it('disables next/last buttons on last page', () => {
    render(<Pagination {...defaultProps} page={9} />);
    const buttons = document.querySelectorAll('button');
    const nextButton = buttons[buttons.length - 2];
    const lastButton = buttons[buttons.length - 1];
    expect(nextButton).toBeDisabled();
    expect(lastButton).toBeDisabled();
  });

  it('calls onPageChange when clicking page number', () => {
    const onPageChange = vi.fn();
    render(<Pagination {...defaultProps} onPageChange={onPageChange} />);

    fireEvent.click(screen.getByText('3'));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('calls onPageChange when clicking next button', () => {
    const onPageChange = vi.fn();
    render(<Pagination {...defaultProps} page={0} onPageChange={onPageChange} />);

    const buttons = document.querySelectorAll('button:not([disabled])');
    const nextButton = Array.from(buttons).find(btn => btn.getAttribute('title') === 'Next page');
    fireEvent.click(nextButton!);
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('calls onPageChange when clicking prev button', () => {
    const onPageChange = vi.fn();
    render(<Pagination {...defaultProps} page={5} onPageChange={onPageChange} />);

    const buttons = document.querySelectorAll('button:not([disabled])');
    const prevButton = Array.from(buttons).find(btn => btn.getAttribute('title') === 'Previous page');
    fireEvent.click(prevButton!);
    expect(onPageChange).toHaveBeenCalledWith(4);
  });

  it('calls onPageChange when clicking first button', () => {
    const onPageChange = vi.fn();
    render(<Pagination {...defaultProps} page={5} onPageChange={onPageChange} />);

    const buttons = document.querySelectorAll('button:not([disabled])');
    const firstButton = Array.from(buttons).find(btn => btn.getAttribute('title') === 'First page');
    fireEvent.click(firstButton!);
    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it('calls onPageChange when clicking last button', () => {
    const onPageChange = vi.fn();
    render(<Pagination {...defaultProps} page={0} onPageChange={onPageChange} />);

    const buttons = document.querySelectorAll('button:not([disabled])');
    const lastButton = Array.from(buttons).find(btn => btn.getAttribute('title') === 'Last page');
    fireEvent.click(lastButton!);
    expect(onPageChange).toHaveBeenCalledWith(9);
  });

  it('renders page size selector when onPageSizeChange is provided', () => {
    const onPageSizeChange = vi.fn();
    render(<Pagination {...defaultProps} onPageSizeChange={onPageSizeChange} />);

    const select = document.querySelector('select');
    expect(select).toBeInTheDocument();
  });

  it('does not render page size selector when onPageSizeChange is not provided', () => {
    render(<Pagination {...defaultProps} />);

    const select = document.querySelector('select');
    expect(select).not.toBeInTheDocument();
  });

  it('calls onPageSizeChange when page size is changed', () => {
    const onPageSizeChange = vi.fn();
    render(<Pagination {...defaultProps} onPageSizeChange={onPageSizeChange} />);

    const select = document.querySelector('select');
    fireEvent.change(select!, { target: { value: '25' } });
    expect(onPageSizeChange).toHaveBeenCalledWith(25);
  });

  it('renders with custom page size options', () => {
    const onPageSizeChange = vi.fn();
    render(
      <Pagination
        {...defaultProps}
        onPageSizeChange={onPageSizeChange}
        pageSizeOptions={[5, 15, 30]}
      />
    );

    expect(screen.getByText('5 per page')).toBeInTheDocument();
    expect(screen.getByText('15 per page')).toBeInTheDocument();
    expect(screen.getByText('30 per page')).toBeInTheDocument();
  });

  it('returns null when totalPages is 1 and no page size change', () => {
    const { container } = render(
      <Pagination {...defaultProps} totalPages={1} totalElements={5} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders when totalPages is 1 but onPageSizeChange is provided', () => {
    const onPageSizeChange = vi.fn();
    render(
      <Pagination
        {...defaultProps}
        totalPages={1}
        totalElements={5}
        onPageSizeChange={onPageSizeChange}
      />
    );
    expect(screen.getByText(/Showing 1 to 5 of 5 results/)).toBeInTheDocument();
  });

  it('shows ellipsis for large page counts', () => {
    render(<Pagination {...defaultProps} page={5} totalPages={20} totalElements={200} />);
    const ellipses = screen.getAllByText('...');
    expect(ellipses.length).toBeGreaterThan(0);
  });
});
