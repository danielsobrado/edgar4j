import React from 'react';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [10, 25, 50, 100],
}: PaginationProps) {
  const startItem = page * size + 1;
  const endItem = Math.min((page + 1) * size, totalElements);

  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const showPages = 5;
    const halfShow = Math.floor(showPages / 2);

    let startPage = Math.max(0, page - halfShow);
    let endPage = Math.min(totalPages - 1, page + halfShow);

    if (page - halfShow < 0) {
      endPage = Math.min(totalPages - 1, showPages - 1);
    }
    if (page + halfShow >= totalPages) {
      startPage = Math.max(0, totalPages - showPages);
    }

    if (startPage > 0) {
      pages.push(0);
      if (startPage > 1) pages.push('...');
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }

    if (endPage < totalPages - 1) {
      if (endPage < totalPages - 2) pages.push('...');
      pages.push(totalPages - 1);
    }

    return pages;
  };

  if (totalPages <= 1 && !onPageSizeChange) {
    return null;
  }

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-4 py-4">
      <div className="flex items-center gap-4">
        <span className="text-sm text-gray-600">
          Showing {startItem} to {endItem} of {totalElements.toLocaleString()} results
        </span>
        {onPageSizeChange && (
          <select
            value={size}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="border border-gray-300 rounded-md px-2 py-1 text-sm"
          >
            {pageSizeOptions.map((option) => (
              <option key={option} value={option}>
                {option} per page
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="flex items-center gap-1">
        <button
          onClick={() => onPageChange(0)}
          disabled={page === 0}
          className="p-2 rounded-md hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          title="First page"
        >
          <ChevronsLeft className="w-4 h-4" />
        </button>
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          className="p-2 rounded-md hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          title="Previous page"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {getPageNumbers().map((pageNum, index) => (
          <React.Fragment key={index}>
            {pageNum === '...' ? (
              <span className="px-2 text-gray-500">...</span>
            ) : (
              <button
                onClick={() => onPageChange(pageNum as number)}
                className={`px-3 py-1 rounded-md text-sm ${
                  page === pageNum
                    ? 'bg-blue-600 text-white'
                    : 'hover:bg-gray-100 text-gray-700'
                }`}
              >
                {(pageNum as number) + 1}
              </button>
            )}
          </React.Fragment>
        ))}

        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="p-2 rounded-md hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          title="Next page"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
        <button
          onClick={() => onPageChange(totalPages - 1)}
          disabled={page >= totalPages - 1}
          className="p-2 rounded-md hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          title="Last page"
        >
          <ChevronsRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
