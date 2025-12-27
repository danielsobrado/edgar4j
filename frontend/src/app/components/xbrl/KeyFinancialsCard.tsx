import React from 'react';
import { KeyFinancials } from '../../api';
import { TrendingUp, TrendingDown, DollarSign, BarChart3 } from 'lucide-react';

interface KeyFinancialsCardProps {
  financials: KeyFinancials;
  className?: string;
}

// Human-readable labels for common concepts
const CONCEPT_LABELS: Record<string, string> = {
  Assets: 'Total Assets',
  AssetsCurrent: 'Current Assets',
  Liabilities: 'Total Liabilities',
  LiabilitiesCurrent: 'Current Liabilities',
  StockholdersEquity: 'Shareholders Equity',
  LiabilitiesAndStockholdersEquity: 'Total Liabilities & Equity',
  CashAndCashEquivalentsAtCarryingValue: 'Cash & Equivalents',
  Revenues: 'Revenue',
  RevenueFromContractWithCustomerExcludingAssessedTax: 'Revenue',
  CostOfGoodsAndServicesSold: 'Cost of Revenue',
  GrossProfit: 'Gross Profit',
  OperatingIncomeLoss: 'Operating Income',
  NetIncomeLoss: 'Net Income',
  EarningsPerShareBasic: 'EPS (Basic)',
  EarningsPerShareDiluted: 'EPS (Diluted)',
  NetCashProvidedByUsedInOperatingActivities: 'Operating Cash Flow',
  NetCashProvidedByUsedInInvestingActivities: 'Investing Cash Flow',
  NetCashProvidedByUsedInFinancingActivities: 'Financing Cash Flow',
};

// Categories for grouping
const CATEGORIES = {
  'Income Statement': [
    'Revenues',
    'RevenueFromContractWithCustomerExcludingAssessedTax',
    'CostOfGoodsAndServicesSold',
    'GrossProfit',
    'OperatingIncomeLoss',
    'NetIncomeLoss',
    'EarningsPerShareBasic',
    'EarningsPerShareDiluted',
  ],
  'Balance Sheet': [
    'Assets',
    'AssetsCurrent',
    'CashAndCashEquivalentsAtCarryingValue',
    'Liabilities',
    'LiabilitiesCurrent',
    'StockholdersEquity',
    'LiabilitiesAndStockholdersEquity',
  ],
  'Cash Flow': [
    'NetCashProvidedByUsedInOperatingActivities',
    'NetCashProvidedByUsedInInvestingActivities',
    'NetCashProvidedByUsedInFinancingActivities',
  ],
};

export function KeyFinancialsCard({ financials, className = '' }: KeyFinancialsCardProps) {
  const formatValue = (value: number, concept: string) => {
    // EPS values are small decimals
    if (concept.includes('EarningsPerShare')) {
      return `$${value.toFixed(2)}`;
    }

    const absValue = Math.abs(value);
    const formatted = absValue >= 1_000_000_000
      ? `$${(absValue / 1_000_000_000).toFixed(2)}B`
      : absValue >= 1_000_000
        ? `$${(absValue / 1_000_000).toFixed(2)}M`
        : absValue >= 1_000
          ? `$${(absValue / 1_000).toFixed(2)}K`
          : `$${absValue.toFixed(0)}`;

    return value < 0 ? `(${formatted})` : formatted;
  };

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'Income Statement':
        return <TrendingUp className="w-4 h-4" />;
      case 'Balance Sheet':
        return <DollarSign className="w-4 h-4" />;
      case 'Cash Flow':
        return <BarChart3 className="w-4 h-4" />;
      default:
        return null;
    }
  };

  return (
    <div className={`bg-white rounded-lg shadow-sm ${className}`}>
      <div className="px-6 py-4 border-b border-gray-200">
        <h3 className="text-lg font-semibold text-gray-900">Key Financials</h3>
      </div>

      <div className="p-6 space-y-6">
        {Object.entries(CATEGORIES).map(([category, concepts]) => {
          const categoryData = concepts
            .filter(c => financials[c] !== undefined)
            .map(c => ({
              concept: c,
              label: CONCEPT_LABELS[c] || c,
              value: financials[c],
            }));

          if (categoryData.length === 0) return null;

          return (
            <div key={category}>
              <div className="flex items-center gap-2 mb-3 text-gray-600">
                {getCategoryIcon(category)}
                <h4 className="text-sm font-medium uppercase tracking-wide">{category}</h4>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                {categoryData.map(({ concept, label, value }) => (
                  <div key={concept} className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-500 mb-1 truncate" title={label}>
                      {label}
                    </p>
                    <p className={`text-lg font-semibold ${value < 0 ? 'text-red-600' : 'text-gray-900'}`}>
                      {formatValue(value, concept)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          );
        })}

        {Object.keys(financials).length === 0 && (
          <div className="text-center text-gray-500 py-4">
            No financial data available
          </div>
        )}
      </div>
    </div>
  );
}
