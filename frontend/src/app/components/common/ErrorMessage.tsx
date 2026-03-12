import React from 'react';
import { AlertCircle, RefreshCw, XCircle } from 'lucide-react';

interface ErrorMessageProps {
  title?: string;
  message: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorMessage({ title = 'Error', message, onRetry, className = '' }: ErrorMessageProps) {
  return (
    <div className={`bg-red-50 border border-red-200 rounded-lg p-4 ${className}`}>
      <div className="flex items-start gap-3">
        <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
        <div className="flex-1">
          <h3 className="font-medium text-red-800">{title}</h3>
          <p className="text-sm text-red-600 mt-1">{message}</p>
          {onRetry && (
            <button
              onClick={onRetry}
              className="mt-3 inline-flex items-center gap-2 text-sm text-red-700 hover:text-red-800 font-medium"
            >
              <RefreshCw className="w-4 h-4" />
              Try again
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export function ErrorBanner({ message, onDismiss }: { message: string; onDismiss?: () => void }) {
  return (
    <div className="bg-red-500 text-white px-4 py-3">
      <div className="flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center gap-2">
          <AlertCircle className="w-5 h-5" />
          <span>{message}</span>
        </div>
        {onDismiss && (
          <button onClick={onDismiss} className="hover:bg-red-600 p-1 rounded">
            <XCircle className="w-5 h-5" />
          </button>
        )}
      </div>
    </div>
  );
}

export function ErrorPage({ title = 'Something went wrong', message, onRetry }: ErrorMessageProps) {
  return (
    <div className="min-h-[400px] flex items-center justify-center">
      <div className="text-center">
        <XCircle className="w-16 h-16 text-red-400 mx-auto mb-4" />
        <h2 className="text-xl font-semibold text-gray-900 mb-2">{title}</h2>
        <p className="text-gray-600 mb-4 max-w-md">{message}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
          >
            <RefreshCw className="w-4 h-4" />
            Try again
          </button>
        )}
      </div>
    </div>
  );
}
