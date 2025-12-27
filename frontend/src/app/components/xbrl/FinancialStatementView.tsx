import React from 'react';
import { FinancialStatement, LineItem } from '../../api';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface FinancialStatementViewProps {
  statement: FinancialStatement;
  className?: string;
}

export function FinancialStatementView({ statement, className = '' }: FinancialStatementViewProps) {
  const [expandedSections, setExpandedSections] = React.useState<Set<string>>(new Set());

  // Get unique period columns
  const periods = React.useMemo(() => {
    const periodSet = new Set<string>();
    statement.lineItems.forEach(item => {
      Object.keys(item.valuesByPeriod || {}).forEach(p => periodSet.add(p));
    });
    return Array.from(periodSet).sort().reverse(); // Most recent first
  }, [statement]);

  const formatValue = (value: number | undefined, item: LineItem) => {
    if (value === undefined || value === null) return '-';

    // Format as currency if monetary
    if (item.isMonetary) {
      const absValue = Math.abs(value);
      const formatted = absValue >= 1_000_000_000
        ? `${(absValue / 1_000_000_000).toFixed(1)}B`
        : absValue >= 1_000_000
          ? `${(absValue / 1_000_000).toFixed(1)}M`
          : absValue >= 1_000
            ? `${(absValue / 1_000).toFixed(1)}K`
            : absValue.toFixed(0);
      return value < 0 ? `(${formatted})` : formatted;
    }

    // Format as plain number
    return value.toLocaleString();
  };

  const formatPeriodLabel = (period: string) => {
    try {
      const date = new Date(period);
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return period;
    }
  };

  const toggleSection = (concept: string) => {
    setExpandedSections(prev => {
      const next = new Set(prev);
      if (next.has(concept)) {
        next.delete(concept);
      } else {
        next.add(concept);
      }
      return next;
    });
  };

  return (
    <div className={`bg-white rounded-lg shadow-sm overflow-hidden ${className}`}>
      <div className="px-6 py-4 border-b border-gray-200 bg-gray-50">
        <h3 className="text-lg font-semibold text-gray-900">{statement.title}</h3>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Item
              </th>
              {periods.slice(0, 4).map(period => (
                <th key={period} className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap">
                  {formatPeriodLabel(period)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-100">
            {statement.lineItems.map((item, index) => (
              <tr
                key={`${item.concept}-${index}`}
                className={`
                  ${item.isTotal ? 'bg-gray-100 font-semibold' : ''}
                  ${item.isSubtotal ? 'bg-gray-50 font-medium' : ''}
                  hover:bg-blue-50 transition-colors
                `}
              >
                <td className="px-6 py-2 whitespace-nowrap">
                  <div
                    className="flex items-center gap-1"
                    style={{ paddingLeft: `${item.indentLevel * 16}px` }}
                  >
                    {(item.isTotal || item.isSubtotal) && (
                      <button
                        onClick={() => toggleSection(item.concept)}
                        className="p-0.5 hover:bg-gray-200 rounded"
                      >
                        {expandedSections.has(item.concept) ? (
                          <ChevronDown className="w-3 h-3" />
                        ) : (
                          <ChevronRight className="w-3 h-3" />
                        )}
                      </button>
                    )}
                    <span className={`text-sm ${item.isTotal ? 'text-gray-900' : 'text-gray-700'}`}>
                      {item.label}
                    </span>
                  </div>
                </td>
                {periods.slice(0, 4).map(period => (
                  <td
                    key={period}
                    className={`px-4 py-2 text-right text-sm whitespace-nowrap font-mono
                      ${item.isTotal ? 'border-t-2 border-gray-300' : ''}
                      ${item.isSubtotal ? 'border-t border-gray-200' : ''}
                    `}
                  >
                    {formatValue(item.valuesByPeriod?.[period], item)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {statement.lineItems.length === 0 && (
        <div className="px-6 py-8 text-center text-gray-500">
          No data available for this statement
        </div>
      )}
    </div>
  );
}
