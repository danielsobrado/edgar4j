import React, { useState, useEffect } from 'react';
import {
  FileSpreadsheet,
  Building2,
  Calculator,
  AlertTriangle,
  CheckCircle,
  Loader2,
  ChevronDown,
  ChevronUp,
  Search,
} from 'lucide-react';
import { useXbrlAnalysis, useFinancialStatements, useXbrlFacts } from '../../hooks/useXbrl';
import { FinancialStatementView } from './FinancialStatementView';
import { KeyFinancialsCard } from './KeyFinancialsCard';
import { SecFilingMetadata, XbrlFact, FinancialStatements } from '../../api';

interface XbrlAnalysisPanelProps {
  filingUrl: string;
  isXbrl: boolean;
  isInlineXbrl: boolean;
}

type TabType = 'overview' | 'statements' | 'facts';

export function XbrlAnalysisPanel({ filingUrl, isXbrl, isInlineXbrl }: XbrlAnalysisPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [factSearch, setFactSearch] = useState('');

  const { data: analysis, loading: analysisLoading, error: analysisError, analyze } = useXbrlAnalysis();
  const { data: statements, loading: statementsLoading, load: loadStatements } = useFinancialStatements();
  const { facts, loading: factsLoading, loadFacts, searchFacts } = useXbrlFacts();

  // Load analysis when panel is expanded
  useEffect(() => {
    if (expanded && !analysis && !analysisLoading && (isXbrl || isInlineXbrl)) {
      analyze(filingUrl).catch(console.error);
    }
  }, [expanded, analysis, analysisLoading, isXbrl, isInlineXbrl, filingUrl, analyze]);

  // Load statements when tab is selected
  useEffect(() => {
    if (activeTab === 'statements' && !statements && !statementsLoading) {
      loadStatements(filingUrl).catch(console.error);
    }
  }, [activeTab, statements, statementsLoading, filingUrl, loadStatements]);

  // Load facts when tab is selected
  useEffect(() => {
    if (activeTab === 'facts' && facts.length === 0 && !factsLoading) {
      loadFacts(filingUrl).catch(console.error);
    }
  }, [activeTab, facts.length, factsLoading, filingUrl, loadFacts]);

  // Handle fact search
  const handleFactSearch = () => {
    if (factSearch.trim()) {
      searchFacts(filingUrl, factSearch);
    } else {
      loadFacts(filingUrl);
    }
  };

  if (!isXbrl && !isInlineXbrl) {
    return null;
  }

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      {/* Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full px-6 py-4 bg-gradient-to-r from-blue-50 to-indigo-50 flex items-center justify-between hover:from-blue-100 hover:to-indigo-100 transition-colors"
      >
        <div className="flex items-center gap-3">
          <FileSpreadsheet className="w-5 h-5 text-blue-600" />
          <div className="text-left">
            <h3 className="font-semibold text-gray-900">XBRL Analysis</h3>
            <p className="text-sm text-gray-500">
              {isInlineXbrl ? 'Inline XBRL (iXBRL)' : 'XBRL'} structured data available
            </p>
          </div>
        </div>
        {expanded ? (
          <ChevronUp className="w-5 h-5 text-gray-500" />
        ) : (
          <ChevronDown className="w-5 h-5 text-gray-500" />
        )}
      </button>

      {/* Content */}
      {expanded && (
        <div className="border-t border-gray-200">
          {/* Tabs */}
          <div className="flex border-b border-gray-200 bg-gray-50">
            <TabButton
              active={activeTab === 'overview'}
              onClick={() => setActiveTab('overview')}
              icon={<Building2 className="w-4 h-4" />}
              label="Overview"
            />
            <TabButton
              active={activeTab === 'statements'}
              onClick={() => setActiveTab('statements')}
              icon={<Calculator className="w-4 h-4" />}
              label="Statements"
            />
            <TabButton
              active={activeTab === 'facts'}
              onClick={() => setActiveTab('facts')}
              icon={<FileSpreadsheet className="w-4 h-4" />}
              label="Facts"
            />
          </div>

          {/* Tab Content */}
          <div className="p-6">
            {activeTab === 'overview' && (
              <OverviewTab
                analysis={analysis}
                loading={analysisLoading}
                error={analysisError}
              />
            )}
            {activeTab === 'statements' && (
              <StatementsTab
                statements={statements}
                loading={statementsLoading}
              />
            )}
            {activeTab === 'facts' && (
              <FactsTab
                facts={facts}
                loading={factsLoading}
                searchQuery={factSearch}
                onSearchChange={setFactSearch}
                onSearch={handleFactSearch}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// Tab Button Component
function TabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors
        ${active
          ? 'text-blue-600 border-b-2 border-blue-600 bg-white'
          : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
        }`}
    >
      {icon}
      {label}
    </button>
  );
}

// Overview Tab
function OverviewTab({
  analysis,
  loading,
  error,
}: {
  analysis: ReturnType<typeof useXbrlAnalysis>['data'];
  loading: boolean;
  error: string | null;
}) {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
        <span className="ml-2 text-gray-600">Analyzing XBRL data...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 text-red-600 py-4">
        <AlertTriangle className="w-5 h-5" />
        <span>{error}</span>
      </div>
    );
  }

  if (!analysis) {
    return <div className="text-gray-500 py-4">No analysis data available</div>;
  }

  return (
    <div className="space-y-6">
      {/* SEC Metadata */}
      {analysis.secMetadata && (
        <div className="bg-gray-50 rounded-lg p-4">
          <h4 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
            <Building2 className="w-4 h-4" />
            Filing Information
          </h4>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            {analysis.secMetadata.entityName && (
              <div>
                <p className="text-gray-500">Company</p>
                <p className="font-medium">{analysis.secMetadata.entityName}</p>
              </div>
            )}
            {analysis.secMetadata.tradingSymbol && (
              <div>
                <p className="text-gray-500">Ticker</p>
                <p className="font-medium">{analysis.secMetadata.tradingSymbol}</p>
              </div>
            )}
            {analysis.secMetadata.formType && (
              <div>
                <p className="text-gray-500">Form Type</p>
                <p className="font-medium">{analysis.secMetadata.formType}</p>
              </div>
            )}
            {analysis.secMetadata.fiscalYear && (
              <div>
                <p className="text-gray-500">Fiscal Year</p>
                <p className="font-medium">{analysis.secMetadata.fiscalYear}</p>
              </div>
            )}
            {analysis.secMetadata.fiscalPeriod && (
              <div>
                <p className="text-gray-500">Period</p>
                <p className="font-medium">{analysis.secMetadata.fiscalPeriod}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Validation Status */}
      {analysis.calculationValidation && (
        <div className={`rounded-lg p-4 ${
          analysis.calculationValidation.isValid ? 'bg-green-50' : 'bg-yellow-50'
        }`}>
          <div className="flex items-center gap-2 mb-2">
            {analysis.calculationValidation.isValid ? (
              <CheckCircle className="w-5 h-5 text-green-600" />
            ) : (
              <AlertTriangle className="w-5 h-5 text-yellow-600" />
            )}
            <h4 className="font-medium">
              {analysis.calculationValidation.isValid
                ? 'Calculations Valid'
                : 'Calculation Issues Found'}
            </h4>
          </div>
          <p className="text-sm text-gray-600">
            {analysis.calculationValidation.validCalculations} of {analysis.calculationValidation.totalChecks} checks passed
          </p>
        </div>
      )}

      {/* Key Financials */}
      {analysis.keyFinancials && Object.keys(analysis.keyFinancials).length > 0 && (
        <KeyFinancialsCard financials={analysis.keyFinancials} />
      )}

      {/* Summary Stats */}
      {analysis.summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard label="Total Facts" value={analysis.summary.totalFacts} />
          <StatCard label="Contexts" value={analysis.summary.totalContexts} />
          <StatCard label="Units" value={analysis.summary.totalUnits} />
          <StatCard label="Parse Time" value={`${analysis.summary.parseTimeMs}ms`} />
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-gray-50 rounded-lg p-4 text-center">
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      <p className="text-sm text-gray-500">{label}</p>
    </div>
  );
}

// Statements Tab
function StatementsTab({
  statements,
  loading,
}: {
  statements: FinancialStatements | null;
  loading: boolean;
}) {
  const [selectedStatement, setSelectedStatement] = useState<string>('balanceSheet');

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
        <span className="ml-2 text-gray-600">Loading financial statements...</span>
      </div>
    );
  }

  if (!statements) {
    return <div className="text-gray-500 py-4">No statements available</div>;
  }

  const statementOptions = [
    { key: 'balanceSheet', label: 'Balance Sheet', data: statements.balanceSheet },
    { key: 'incomeStatement', label: 'Income Statement', data: statements.incomeStatement },
    { key: 'cashFlowStatement', label: 'Cash Flow', data: statements.cashFlowStatement },
    { key: 'equityStatement', label: 'Equity Statement', data: statements.equityStatement },
  ].filter(opt => opt.data && opt.data.lineItems && opt.data.lineItems.length > 0);

  const currentStatement = statementOptions.find(s => s.key === selectedStatement)?.data;

  return (
    <div className="space-y-4">
      {/* Statement Selector */}
      <div className="flex gap-2 flex-wrap">
        {statementOptions.map(opt => (
          <button
            key={opt.key}
            onClick={() => setSelectedStatement(opt.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors
              ${selectedStatement === opt.key
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Statement View */}
      {currentStatement && (
        <FinancialStatementView statement={currentStatement} />
      )}
    </div>
  );
}

// Facts Tab
function FactsTab({
  facts,
  loading,
  searchQuery,
  onSearchChange,
  onSearch,
}: {
  facts: XbrlFact[];
  loading: boolean;
  searchQuery: string;
  onSearchChange: (value: string) => void;
  onSearch: () => void;
}) {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
        <span className="ml-2 text-gray-600">Loading facts...</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Search */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => onSearchChange(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && onSearch()}
            placeholder="Search facts by concept name..."
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>
        <button
          onClick={onSearch}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          Search
        </button>
      </div>

      {/* Facts Table */}
      <div className="overflow-x-auto border border-gray-200 rounded-lg">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Concept</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Value</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Period</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Unit</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {facts.slice(0, 100).map((fact, index) => (
              <tr key={index} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-sm">
                  <span className="font-mono text-xs text-gray-500">{fact.namespace}:</span>
                  <span className="font-medium">{fact.concept}</span>
                </td>
                <td className="px-4 py-2 text-sm text-right font-mono">
                  {typeof fact.value === 'number'
                    ? fact.value.toLocaleString()
                    : String(fact.value).substring(0, 50)}
                </td>
                <td className="px-4 py-2 text-sm text-gray-600">
                  {fact.periodEnd || '-'}
                </td>
                <td className="px-4 py-2 text-sm text-gray-600">
                  {fact.unitDisplay || '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {facts.length > 100 && (
        <p className="text-sm text-gray-500 text-center">
          Showing first 100 of {facts.length} facts
        </p>
      )}

      {facts.length === 0 && (
        <div className="text-center text-gray-500 py-8">
          No facts found
        </div>
      )}
    </div>
  );
}
