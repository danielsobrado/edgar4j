import React from 'react';
import * as Switch from '@radix-ui/react-switch';
import { KeyRound } from 'lucide-react';
import { MarketDataProvider, Settings } from '../../api';
import {
  getMarketDataProviderDefinition,
  getProviderSecretHint,
  getProviderStatusLabel,
  MARKET_DATA_PROVIDER_DEFINITIONS,
  MarketDataProviderFormState,
  MarketDataProviderKey,
} from './marketDataSettings';

interface MarketDataSettingsSectionProps {
  settings: Settings | null;
  marketDataProvider: MarketDataProvider;
  marketDataProviders: MarketDataProviderFormState;
  onMarketDataProviderChange: (provider: MarketDataProvider) => void;
  onProviderChange: (key: MarketDataProviderKey, patch: Partial<MarketDataProviderFormState[MarketDataProviderKey]>) => void;
}

export function MarketDataSettingsSection({
  settings,
  marketDataProvider,
  marketDataProviders,
  onMarketDataProviderChange,
  onProviderChange,
}: MarketDataSettingsSectionProps) {
  const selectedProviderDefinition = marketDataProvider !== 'NONE'
    ? getMarketDataProviderDefinition(marketDataProvider)
    : null;

  return (
    <div className="bg-white rounded-lg shadow-sm p-6">
      <h2 className="mb-4">Market Data Providers</h2>
      <p className="text-sm text-gray-600 mb-4">
        Choose the preferred provider for charts and quotes, then keep additional providers enabled as fallbacks.
        Secrets stay server-side after save; leaving an API key blank preserves the stored value.
      </p>

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,16rem)_1fr] gap-4 mb-5">
        <div>
          <label className="block text-sm mb-2">Preferred Provider</label>
          <select
            value={marketDataProvider}
            onChange={(event) => onMarketDataProviderChange(event.target.value as MarketDataProvider)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
          >
            <option value="NONE">Disabled</option>
            {MARKET_DATA_PROVIDER_DEFINITIONS.map((definition) => (
              <option key={definition.provider} value={definition.provider}>
                {definition.label}
              </option>
            ))}
          </select>
        </div>

        <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
          {selectedProviderDefinition ? (
            <>
              <p className="font-medium text-slate-900">{selectedProviderDefinition.label} is selected first</p>
              <p className="mt-1">
                {selectedProviderDefinition.helpText}
                {' '}Any other enabled providers remain available for fallback.
              </p>
            </>
          ) : (
            <>
              <p className="font-medium text-slate-900">Market data is disabled</p>
              <p className="mt-1">
                Filing charts and quote enrichment stay off until you select a provider.
              </p>
            </>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {MARKET_DATA_PROVIDER_DEFINITIONS.map((definition) => {
          const value = marketDataProviders[definition.key];
          const isSelected = marketDataProvider === definition.provider;
          const secretHint = getProviderSecretHint(definition, value);

          return (
            <div
              key={definition.provider}
              className={`rounded-xl border p-4 ${
                isSelected ? 'border-slate-900 bg-slate-50' : 'border-gray-200 bg-white'
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-base font-medium text-slate-900">{definition.label}</h3>
                    {isSelected && (
                      <span className="rounded-full bg-slate-900 px-2 py-0.5 text-xs font-medium text-white">
                        Preferred
                      </span>
                    )}
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                      {getProviderStatusLabel(definition, value)}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-gray-600">{definition.description}</p>
                </div>

                <div className="flex items-center gap-3">
                  <div className="text-right text-xs text-gray-500">
                    {isSelected ? 'Selected provider stays enabled' : 'Available for fallback'}
                  </div>
                  <Switch.Root
                    checked={isSelected ? true : value.enabled}
                    disabled={isSelected}
                    onCheckedChange={(checked) => onProviderChange(definition.key, { enabled: checked })}
                    className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors disabled:opacity-60"
                  >
                    <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
                  </Switch.Root>
                </div>
              </div>

              <div className="mt-4 space-y-4">
                <div>
                  <label className="block text-sm mb-2">Base URL</label>
                  <input
                    type="text"
                    value={value.baseUrl}
                    onChange={(event) => onProviderChange(definition.key, { baseUrl: event.target.value })}
                    placeholder={definition.defaultBaseUrl}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>

                {definition.requiresApiKey ? (
                  <div>
                    <div className="flex items-center justify-between gap-3 mb-2">
                      <label className="block text-sm">{definition.apiKeyLabel}</label>
                      <button
                        type="button"
                        onClick={() => onProviderChange(definition.key, {
                          clearApiKey: !value.clearApiKey,
                          apiKey: '',
                        })}
                        className="text-xs text-slate-600 hover:text-slate-900"
                      >
                        {value.clearApiKey ? 'Undo clear' : 'Clear saved key'}
                      </button>
                    </div>
                    <input
                      type="password"
                      value={value.apiKey}
                      onChange={(event) => onProviderChange(definition.key, {
                        apiKey: event.target.value,
                        clearApiKey: false,
                      })}
                      placeholder={definition.apiKeyPlaceholder}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md"
                    />
                  </div>
                ) : (
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                    No API key is required for this provider.
                  </div>
                )}

                <div className="rounded-lg bg-gray-50 px-3 py-3 text-sm text-gray-600">
                  <div className="flex items-start gap-2">
                    <KeyRound className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" />
                    <div className="space-y-1">
                      {secretHint && <p>{secretHint}</p>}
                      <p>{definition.helpText}</p>
                      {settings?.marketDataProvider === definition.provider && settings.marketDataConfigured && (
                        <p className="text-emerald-700">The currently selected provider is operational.</p>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
