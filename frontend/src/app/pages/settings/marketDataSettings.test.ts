import { describe, expect, it } from 'vitest';
import {
  buildLegacySelectedMarketDataFields,
  buildMarketDataProvidersRequest,
  createMarketDataProviderFormState,
  getMarketDataProviderDefinition,
  getProviderSecretHint,
  normalizeMarketDataProvider,
} from './marketDataSettings';

describe('marketDataSettings', () => {
  it('normalizes market data provider names from legacy formats', () => {
    expect(normalizeMarketDataProvider('yahoo_finance')).toBe('YAHOOFINANCE');
    expect(normalizeMarketDataProvider('alpha-vantage')).toBe('ALPHAVANTAGE');
    expect(normalizeMarketDataProvider('')).toBe('NONE');
  });

  it('hydrates provider state from nested settings and legacy selected-provider fields', () => {
    const state = createMarketDataProviderFormState({
      userAgent: 'Edgar4j/1.0 (ops@example.com)',
      autoRefresh: true,
      refreshInterval: 60,
      darkMode: false,
      emailNotifications: false,
      smtpPort: 587,
      smtpStartTlsEnabled: true,
      marketDataProvider: 'FINNHUB',
      marketDataBaseUrl: 'https://finnhub.io/api/v1',
      marketDataApiKeyConfigured: true,
      marketDataConfigured: true,
      marketDataProviders: {
        tiingo: {
          enabled: false,
          baseUrl: 'https://api.tiingo.com',
          apiKeyConfigured: true,
          apiKeySource: 'STORED',
          configured: true,
        },
      },
    });

    expect(state.finnhub.enabled).toBe(true);
    expect(state.finnhub.baseUrl).toBe('https://finnhub.io/api/v1');
    expect(state.finnhub.apiKeyConfigured).toBe(true);
    expect(state.finnhub.apiKeySource).toBe('UNKNOWN');
    expect(state.finnhub.configured).toBe(true);
    expect(state.tiingo.apiKeyConfigured).toBe(true);
    expect(state.tiingo.apiKeySource).toBe('STORED');
  });

  it('builds provider request payloads without overwriting saved secrets when fields are blank', () => {
    const request = buildMarketDataProvidersRequest({
      tiingo: {
        enabled: true,
        baseUrl: 'https://api.tiingo.com',
        apiKey: '',
        apiKeyConfigured: true,
        apiKeySource: 'STORED',
        configured: true,
        clearApiKey: false,
      },
      yahooFinance: {
        enabled: true,
        baseUrl: 'https://query1.finance.yahoo.com/v8/finance/chart',
        apiKey: '',
        apiKeyConfigured: false,
        apiKeySource: 'NONE',
        configured: true,
        clearApiKey: false,
      },
      finnhub: {
        enabled: true,
        baseUrl: 'https://finnhub.io/api/v1',
        apiKey: 'finnhub-secret',
        apiKeyConfigured: false,
        apiKeySource: 'NONE',
        configured: false,
        clearApiKey: false,
      },
      alphaVantage: {
        enabled: false,
        baseUrl: 'https://www.alphavantage.co/query',
        apiKey: '',
        apiKeyConfigured: false,
        apiKeySource: 'NONE',
        configured: false,
        clearApiKey: true,
      },
    });

    expect(request.tiingo?.apiKey).toBeUndefined();
    expect(request.finnhub?.apiKey).toBe('finnhub-secret');
    expect(request.alphaVantage?.clearApiKey).toBe(true);
  });

  it('mirrors the selected provider into legacy request fields for backward compatibility', () => {
    const state = createMarketDataProviderFormState();
    state.finnhub.baseUrl = 'https://finnhub.io/api/v1';
    state.finnhub.apiKey = 'new-key';

    const legacyFields = buildLegacySelectedMarketDataFields('FINNHUB', state);

    expect(legacyFields.marketDataBaseUrl).toBe('https://finnhub.io/api/v1');
    expect(legacyFields.marketDataApiKey).toBe('new-key');
    expect(legacyFields.clearMarketDataApiKey).toBe(false);
  });

  it('explains when a provider key comes from fallback server configuration', () => {
    const hint = getProviderSecretHint(
      getMarketDataProviderDefinition('FINNHUB'),
      {
        enabled: true,
        baseUrl: 'https://finnhub.io/api/v1',
        apiKey: '',
        apiKeyConfigured: true,
        apiKeySource: 'FALLBACK',
        configured: true,
        clearApiKey: false,
      },
    );

    expect(hint).toContain('server configuration');
    expect(hint).toContain('will not remove');
  });
});
