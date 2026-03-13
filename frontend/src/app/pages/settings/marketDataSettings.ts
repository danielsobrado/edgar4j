import {
  MarketDataApiKeySource,
  MarketDataProvider,
  MarketDataProviderRequest,
  MarketDataProvidersRequest,
  Settings,
} from '../../api';

export type SupportedMarketDataProvider = Exclude<MarketDataProvider, 'NONE'>;
export type MarketDataProviderKey = 'tiingo' | 'yahooFinance' | 'finnhub' | 'alphaVantage';

export interface MarketDataProviderDefinition {
  provider: SupportedMarketDataProvider;
  key: MarketDataProviderKey;
  label: string;
  description: string;
  defaultBaseUrl: string;
  defaultEnabled: boolean;
  requiresApiKey: boolean;
  apiKeyLabel?: string;
  apiKeyPlaceholder?: string;
  helpText: string;
}

export interface MarketDataProviderFormValue {
  enabled: boolean;
  baseUrl: string;
  apiKey: string;
  apiKeyConfigured: boolean;
  apiKeySource: MarketDataApiKeySource;
  configured: boolean;
  clearApiKey: boolean;
}

export type MarketDataProviderFormState = Record<MarketDataProviderKey, MarketDataProviderFormValue>;

export interface LegacySelectedMarketDataFields {
  marketDataBaseUrl?: string;
  marketDataApiKey?: string;
  clearMarketDataApiKey?: boolean;
}

const PROVIDER_DEFINITIONS: MarketDataProviderDefinition[] = [
  {
    provider: 'TIINGO',
    key: 'tiingo',
    label: 'Tiingo',
    description: 'Paid historical candles with local cache support for filing charts.',
    defaultBaseUrl: 'https://api.tiingo.com',
    defaultEnabled: false,
    requiresApiKey: true,
    apiKeyLabel: 'Tiingo API key',
    apiKeyPlaceholder: 'Your Tiingo token',
    helpText: 'Best when you want a dedicated historical price source for filing overlays.',
  },
  {
    provider: 'YAHOOFINANCE',
    key: 'yahooFinance',
    label: 'Yahoo Finance',
    description: 'Free quotes and historical prices without an API key.',
    defaultBaseUrl: 'https://query1.finance.yahoo.com/v8/finance/chart',
    defaultEnabled: true,
    requiresApiKey: false,
    helpText: 'Good free default for quotes, market cap, and historical price fallback.',
  },
  {
    provider: 'FINNHUB',
    key: 'finnhub',
    label: 'Finnhub',
    description: 'API-key backed market data with quote and historical endpoints.',
    defaultBaseUrl: 'https://finnhub.io/api/v1',
    defaultEnabled: false,
    requiresApiKey: true,
    apiKeyLabel: 'Finnhub API key',
    apiKeyPlaceholder: 'Your Finnhub token',
    helpText: 'Useful as a paid or rate-limited fallback behind the preferred provider.',
  },
  {
    provider: 'ALPHAVANTAGE',
    key: 'alphaVantage',
    label: 'Alpha Vantage',
    description: 'API-key backed quote and time-series provider.',
    defaultBaseUrl: 'https://www.alphavantage.co/query',
    defaultEnabled: false,
    requiresApiKey: true,
    apiKeyLabel: 'Alpha Vantage API key',
    apiKeyPlaceholder: 'Your Alpha Vantage key',
    helpText: 'Useful as an additional paid/free fallback source when rate limits differ.',
  },
];

const PROVIDER_BY_NAME = Object.fromEntries(
  PROVIDER_DEFINITIONS.map((definition) => [definition.provider, definition]),
) as Record<SupportedMarketDataProvider, MarketDataProviderDefinition>;

export const MARKET_DATA_PROVIDER_DEFINITIONS = PROVIDER_DEFINITIONS;

export function normalizeMarketDataProvider(provider?: string | null): MarketDataProvider {
  if (!provider) {
    return 'NONE';
  }

  const normalized = provider.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  if (normalized === 'TIINGO' || normalized === 'YAHOOFINANCE' || normalized === 'FINNHUB' || normalized === 'ALPHAVANTAGE') {
    return normalized;
  }
  return 'NONE';
}

export function getMarketDataProviderDefinition(provider: SupportedMarketDataProvider): MarketDataProviderDefinition {
  return PROVIDER_BY_NAME[provider];
}

export function createMarketDataProviderFormState(settings?: Settings | null): MarketDataProviderFormState {
  const selectedProvider = normalizeMarketDataProvider(settings?.marketDataProvider);
  const storedProviders = settings?.marketDataProviders;

  return PROVIDER_DEFINITIONS.reduce((state, definition) => {
    const providerSettings = storedProviders?.[definition.key];
    const selected = selectedProvider === definition.provider;

    state[definition.key] = {
      enabled: providerSettings?.enabled ?? (selected || definition.defaultEnabled),
      baseUrl: providerSettings?.baseUrl
        ?? (selected ? settings?.marketDataBaseUrl : undefined)
        ?? definition.defaultBaseUrl,
      apiKey: '',
      apiKeyConfigured: providerSettings?.apiKeyConfigured
        ?? (selected ? settings?.marketDataApiKeyConfigured : undefined)
        ?? false,
      apiKeySource: providerSettings?.apiKeySource
        ?? (selected ? settings?.marketDataApiKeySource : undefined)
        ?? ((providerSettings?.apiKeyConfigured
          ?? (selected ? settings?.marketDataApiKeyConfigured : undefined))
          ? 'UNKNOWN'
          : 'NONE'),
      configured: providerSettings?.configured
        ?? (selected ? settings?.marketDataConfigured : undefined)
        ?? false,
      clearApiKey: false,
    };

    return state;
  }, {} as MarketDataProviderFormState);
}

export function buildMarketDataProvidersRequest(
  formState: MarketDataProviderFormState,
): MarketDataProvidersRequest {
  return {
    tiingo: toProviderRequest(formState.tiingo),
    yahooFinance: toProviderRequest(formState.yahooFinance),
    finnhub: toProviderRequest(formState.finnhub),
    alphaVantage: toProviderRequest(formState.alphaVantage),
  };
}

export function buildLegacySelectedMarketDataFields(
  provider: MarketDataProvider,
  formState: MarketDataProviderFormState,
): LegacySelectedMarketDataFields {
  if (provider === 'NONE') {
    return {
      marketDataBaseUrl: undefined,
      marketDataApiKey: undefined,
      clearMarketDataApiKey: false,
    };
  }

  const definition = getMarketDataProviderDefinition(provider);
  const selectedSettings = formState[definition.key];

  return {
    marketDataBaseUrl: selectedSettings.baseUrl,
    marketDataApiKey: normalizeApiKey(selectedSettings.apiKey),
    clearMarketDataApiKey: selectedSettings.clearApiKey,
  };
}

export function getProviderStatusLabel(
  definition: MarketDataProviderDefinition,
  value: MarketDataProviderFormValue,
): string {
  if (!value.enabled) {
    return 'Disabled';
  }
  if (value.clearApiKey) {
    return definition.requiresApiKey ? 'Key update pending' : 'Pending save';
  }
  if (value.configured) {
    if (definition.requiresApiKey && value.apiKeySource === 'FALLBACK') {
      return 'Ready via fallback';
    }
    return 'Ready';
  }
  if (definition.requiresApiKey && !value.apiKeyConfigured && !value.apiKey) {
    return 'Needs API key';
  }
  return 'Needs save';
}

export function getProviderSecretHint(
  definition: MarketDataProviderDefinition,
  value: MarketDataProviderFormValue,
): string | null {
  if (!definition.requiresApiKey) {
    return 'No API key required.';
  }
  if (value.clearApiKey) {
    if (value.apiKeySource === 'FALLBACK' || value.apiKeySource === 'UNKNOWN') {
      return 'The AppSettings override will be removed when you save, but server-side fallback credentials may still keep this provider operational.';
    }
    return 'The saved API key will be removed when you save settings.';
  }
  if (value.apiKey) {
    return 'A new API key will replace the saved credential when you save.';
  }
  if (value.apiKeySource === 'STORED') {
    return 'A saved API key is already configured. Leave the field blank to keep it unchanged.';
  }
  if (value.apiKeySource === 'FALLBACK') {
    return 'An API key is supplied by server configuration. Clearing AppSettings here will not remove that fallback credential.';
  }
  if (value.apiKeySource === 'UNKNOWN') {
    return 'An API key is already configured. Leave the field blank to keep it unchanged.';
  }
  return 'Enter an API key to enable this provider.';
}

function toProviderRequest(value: MarketDataProviderFormValue): MarketDataProviderRequest {
  return {
    enabled: value.enabled,
    baseUrl: value.baseUrl,
    apiKey: normalizeApiKey(value.apiKey),
    clearApiKey: value.clearApiKey,
  };
}

function normalizeApiKey(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}
