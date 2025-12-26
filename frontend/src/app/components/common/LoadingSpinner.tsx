import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  text?: string;
}

const sizeClasses = {
  sm: 'w-4 h-4',
  md: 'w-8 h-8',
  lg: 'w-12 h-12',
};

export function LoadingSpinner({ size = 'md', className = '', text }: LoadingSpinnerProps) {
  return (
    <div className={`flex flex-col items-center justify-center gap-2 ${className}`}>
      <Loader2 className={`animate-spin text-blue-600 ${sizeClasses[size]}`} />
      {text && <span className="text-sm text-gray-600">{text}</span>}
    </div>
  );
}

export function LoadingOverlay({ text = 'Loading...' }: { text?: string }) {
  return (
    <div className="absolute inset-0 bg-white/80 flex items-center justify-center z-50">
      <LoadingSpinner size="lg" text={text} />
    </div>
  );
}

export function LoadingPage({ text = 'Loading...' }: { text?: string }) {
  return (
    <div className="min-h-[400px] flex items-center justify-center">
      <LoadingSpinner size="lg" text={text} />
    </div>
  );
}
