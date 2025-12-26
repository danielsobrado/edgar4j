import React from 'react';

interface FormTypeBadgeProps {
  formType: string;
}

const formTypeColors: Record<string, { bg: string; text: string }> = {
  '10-K': { bg: 'bg-blue-100', text: 'text-blue-800' },
  '10-Q': { bg: 'bg-indigo-100', text: 'text-indigo-800' },
  '8-K': { bg: 'bg-orange-100', text: 'text-orange-800' },
  'DEF 14A': { bg: 'bg-purple-100', text: 'text-purple-800' },
  '4': { bg: 'bg-pink-100', text: 'text-pink-800' },
  'S-1': { bg: 'bg-green-100', text: 'text-green-800' },
  '13F': { bg: 'bg-yellow-100', text: 'text-yellow-800' },
  'SC 13D': { bg: 'bg-red-100', text: 'text-red-800' },
  'SC 13G': { bg: 'bg-gray-100', text: 'text-gray-800' }
};

export function FormTypeBadge({ formType }: FormTypeBadgeProps) {
  const colors = formTypeColors[formType] || { bg: 'bg-gray-100', text: 'text-gray-800' };
  
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs ${colors.bg} ${colors.text}`}>
      {formType}
    </span>
  );
}
