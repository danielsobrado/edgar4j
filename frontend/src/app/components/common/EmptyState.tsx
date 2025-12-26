import React from 'react';
import { FileQuestion, Search, Building2, Download } from 'lucide-react';

type EmptyStateType = 'search' | 'filings' | 'companies' | 'downloads' | 'generic';

interface EmptyStateProps {
  type?: EmptyStateType;
  title?: string;
  message?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

const defaultContent: Record<EmptyStateType, { icon: React.ReactNode; title: string; message: string }> = {
  search: {
    icon: <Search className="w-12 h-12 text-gray-400" />,
    title: 'No results found',
    message: 'Try adjusting your search criteria or filters.',
  },
  filings: {
    icon: <FileQuestion className="w-12 h-12 text-gray-400" />,
    title: 'No filings found',
    message: 'There are no filings matching your criteria.',
  },
  companies: {
    icon: <Building2 className="w-12 h-12 text-gray-400" />,
    title: 'No companies found',
    message: 'Try downloading company data from the Downloads page.',
  },
  downloads: {
    icon: <Download className="w-12 h-12 text-gray-400" />,
    title: 'No download jobs',
    message: 'Start a download to see job status here.',
  },
  generic: {
    icon: <FileQuestion className="w-12 h-12 text-gray-400" />,
    title: 'No data available',
    message: 'There is no data to display at this time.',
  },
};

export function EmptyState({ type = 'generic', title, message, action }: EmptyStateProps) {
  const content = defaultContent[type];

  return (
    <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
      {content.icon}
      <h3 className="mt-4 text-lg font-medium text-gray-900">{title || content.title}</h3>
      <p className="mt-2 text-sm text-gray-500 max-w-md">{message || content.message}</p>
      {action && (
        <button
          onClick={action.onClick}
          className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
