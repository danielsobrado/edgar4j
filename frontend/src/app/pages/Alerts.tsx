import React from 'react';
import { Bell, Plus, Trash2, TrendingUp, ArrowUpCircle, ArrowDownCircle, FileText } from 'lucide-react';
import * as Switch from '@radix-ui/react-switch';
import { Link } from 'react-router-dom';
import { FORM_TYPES } from '../api';
import { useAlertsStore } from '../store';
import { showSuccess } from '../store/notificationStore';

type AlertRuleType = 'ticker_filing' | 'volume_spike' | 'insider_buy' | 'insider_sell';

const ALERT_TYPE_META: Record<AlertRuleType, { label: string; description: string; icon: React.ComponentType<{ className?: string }> }> = {
  ticker_filing: {
    label: 'Ticker Filing',
    description: 'Track filings for the tickers you care about, optionally by SEC form type.',
    icon: FileText,
  },
  volume_spike: {
    label: 'Volume Spike',
    description: 'Watch for unusual trading volume relative to recent activity.',
    icon: TrendingUp,
  },
  insider_buy: {
    label: 'Insider Buy',
    description: 'Flag insider purchase activity above a material dollar threshold.',
    icon: ArrowUpCircle,
  },
  insider_sell: {
    label: 'Insider Sell',
    description: 'Flag insider sell activity above a material dollar threshold.',
    icon: ArrowDownCircle,
  },
};

export function Alerts() {
  const { rules, addRule, updateRule, removeRule } = useAlertsStore();

  const [name, setName] = React.useState('');
  const [type, setType] = React.useState<AlertRuleType>('ticker_filing');
  const [tickers, setTickers] = React.useState('');
  const [formTypes, setFormTypes] = React.useState<string[]>([]);
  const [volumePercentThreshold, setVolumePercentThreshold] = React.useState(150);
  const [minTransactionValue, setMinTransactionValue] = React.useState(100000);
  const [delivery, setDelivery] = React.useState<'immediate' | 'daily_digest'>('immediate');

  const resetForm = () => {
    setName('');
    setType('ticker_filing');
    setTickers('');
    setFormTypes([]);
    setVolumePercentThreshold(150);
    setMinTransactionValue(100000);
    setDelivery('immediate');
  };

  const handleCreateRule = () => {
    const normalizedTickers = tickers
      .split(',')
      .map((ticker) => ticker.trim().toUpperCase())
      .filter(Boolean);

    addRule({
      name: name.trim() || ALERT_TYPE_META[type].label,
      enabled: true,
      type,
      tickers: normalizedTickers,
      formTypes: type === 'ticker_filing' ? formTypes : undefined,
      volumePercentThreshold: type === 'volume_spike' ? volumePercentThreshold : undefined,
      minTransactionValue: type === 'insider_buy' || type === 'insider_sell' ? minTransactionValue : undefined,
      delivery,
    });

    showSuccess('Alert saved', 'Alert rule has been added to your local configuration.');
    resetForm();
  };

  const renderRuleSummary = (rule: (typeof rules)[number]) => {
    switch (rule.type) {
      case 'ticker_filing':
        return `${rule.tickers.join(', ') || 'All tickers'}${rule.formTypes?.length ? ` | Forms: ${rule.formTypes.join(', ')}` : ''}`;
      case 'volume_spike':
        return `${rule.tickers.join(', ') || 'All tickers'} | Volume >= ${rule.volumePercentThreshold}% of baseline`;
      case 'insider_buy':
        return `${rule.tickers.join(', ') || 'All tickers'} | Buy value >= $${(rule.minTransactionValue || 0).toLocaleString()}`;
      case 'insider_sell':
        return `${rule.tickers.join(', ') || 'All tickers'} | Sell value >= $${(rule.minTransactionValue || 0).toLocaleString()}`;
      default:
        return '';
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2">
            <Bell className="w-8 h-8" />
            Alerts
          </h1>
          <p className="text-gray-600 mt-2">
            Configure investing-focused alerts for filings, volume spikes, and insider activity.
          </p>
        </div>
        <Link
          to="/settings"
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
        >
          Back to Settings
        </Link>
      </div>

      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        Alert rules are stored locally in this browser for now. Email delivery settings still come from the Settings page.
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Plus className="w-5 h-5" />
          <h2>Create Alert Rule</h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-2">Rule Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Insider buys over $250k"
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            />
          </div>

          <div>
            <label className="block text-sm mb-2">Alert Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as AlertRuleType)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            >
              {Object.entries(ALERT_TYPE_META).map(([value, meta]) => (
                <option key={value} value={value}>
                  {meta.label}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-500 mt-2">{ALERT_TYPE_META[type].description}</p>
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm mb-2">Tickers</label>
            <input
              type="text"
              value={tickers}
              onChange={(e) => setTickers(e.target.value)}
              placeholder="MSFT, NVDA, AAPL"
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            />
            <p className="text-xs text-gray-500 mt-2">Comma-separated list. Leave blank to match all tracked tickers.</p>
          </div>

          {type === 'ticker_filing' && (
            <div className="md:col-span-2">
              <label className="block text-sm mb-2">Form Types</label>
              <div className="flex flex-wrap gap-2">
                {FORM_TYPES.map((form) => {
                  const selected = formTypes.includes(form.value);
                  return (
                    <button
                      key={form.value}
                      type="button"
                      onClick={() =>
                        setFormTypes((current) =>
                          selected ? current.filter((value) => value !== form.value) : [...current, form.value]
                        )
                      }
                      className={`px-3 py-1.5 text-sm rounded-full border ${
                        selected
                          ? 'border-[#1a1f36] bg-[#1a1f36] text-white'
                          : 'border-gray-300 text-gray-700 hover:bg-gray-50'
                      }`}
                    >
                      {form.value}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {type === 'volume_spike' && (
            <div>
              <label className="block text-sm mb-2">Volume Threshold (%)</label>
              <input
                type="number"
                min={100}
                step={10}
                value={volumePercentThreshold}
                onChange={(e) => setVolumePercentThreshold(Number(e.target.value || 150))}
                className="w-full px-3 py-2 border border-gray-300 rounded-md"
              />
            </div>
          )}

          {(type === 'insider_buy' || type === 'insider_sell') && (
            <div>
              <label className="block text-sm mb-2">Minimum Transaction Value (USD)</label>
              <input
                type="number"
                min={1000}
                step={1000}
                value={minTransactionValue}
                onChange={(e) => setMinTransactionValue(Number(e.target.value || 100000))}
                className="w-full px-3 py-2 border border-gray-300 rounded-md"
              />
            </div>
          )}

          <div>
            <label className="block text-sm mb-2">Delivery</label>
            <select
              value={delivery}
              onChange={(e) => setDelivery(e.target.value as 'immediate' | 'daily_digest')}
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            >
              <option value="immediate">Immediate</option>
              <option value="daily_digest">Daily digest</option>
            </select>
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="button"
            onClick={handleCreateRule}
            className="px-5 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47]"
          >
            Save Alert
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <h2>Configured Alerts</h2>
        {rules.length === 0 ? (
          <p className="text-gray-600">No alert rules configured yet.</p>
        ) : (
          <div className="space-y-3">
            {rules.map((rule) => {
              const Icon = ALERT_TYPE_META[rule.type].icon;
              return (
                <div key={rule.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <Icon className="w-4 h-4 text-[#1a1f36]" />
                        <p>{rule.name}</p>
                        <span className="text-xs px-2 py-1 rounded-full bg-gray-100 text-gray-700">
                          {ALERT_TYPE_META[rule.type].label}
                        </span>
                        <span className="text-xs px-2 py-1 rounded-full bg-blue-50 text-blue-700">
                          {rule.delivery === 'immediate' ? 'Immediate' : 'Daily digest'}
                        </span>
                      </div>
                      <p className="text-sm text-gray-600">{renderRuleSummary(rule)}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <Switch.Root
                        checked={rule.enabled}
                        onCheckedChange={(checked) => updateRule(rule.id, { enabled: checked })}
                        className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
                      >
                        <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
                      </Switch.Root>
                      <button
                        type="button"
                        onClick={() => removeRule(rule.id)}
                        className="p-2 text-red-600 hover:bg-red-50 rounded-md"
                        title="Delete alert"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
