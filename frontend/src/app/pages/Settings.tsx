import React, { useEffect } from 'react';
import { Settings as SettingsIcon, CheckCircle, XCircle, Sun, Moon, RefreshCw, Database } from 'lucide-react';
import * as Switch from '@radix-ui/react-switch';
import { Link } from 'react-router-dom';
import { useSettings } from '../hooks';
import { LoadingSpinner, LoadingPage } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useSettingsStore } from '../store';
import { MarketDataProvider } from '../api';

export function Settings() {
  const {
    settings,
    loading,
    error,
    saving,
    updateSettings,
    checkConnections,
    refresh,
  } = useSettings();

  const { darkMode, setDarkMode } = useSettingsStore();

  // Local form state
  const [userAgent, setUserAgent] = React.useState('edgar4j/1.0 (contact@example.com)');
  const [autoRefresh, setAutoRefresh] = React.useState(true);
  const [autoRefreshInterval, setAutoRefreshInterval] = React.useState(60);
  const [notifications, setNotifications] = React.useState(true);
  const [notificationEmailTo, setNotificationEmailTo] = React.useState('');
  const [notificationEmailFrom, setNotificationEmailFrom] = React.useState('');
  const [smtpHost, setSmtpHost] = React.useState('');
  const [smtpPort, setSmtpPort] = React.useState(587);
  const [smtpUsername, setSmtpUsername] = React.useState('');
  const [smtpPassword, setSmtpPassword] = React.useState('');
  const [smtpStartTlsEnabled, setSmtpStartTlsEnabled] = React.useState(true);
  const [marketDataProvider, setMarketDataProvider] = React.useState<MarketDataProvider>('NONE');
  const [marketDataBaseUrl, setMarketDataBaseUrl] = React.useState('https://api.tiingo.com');
  const [marketDataApiKey, setMarketDataApiKey] = React.useState('');
  const [connectionStatus, setConnectionStatus] = React.useState<'unknown' | 'testing' | 'connected' | 'failed'>('unknown');
  const [saved, setSaved] = React.useState(false);

  // Load settings on mount
  useEffect(() => {
    void checkConnections().catch(() => {
      setConnectionStatus('failed');
    });
  }, [checkConnections]);

  // Update local state when settings are loaded
  useEffect(() => {
    if (settings) {
      setUserAgent(settings.userAgent || 'edgar4j/1.0 (contact@example.com)');
      setAutoRefresh(settings.autoRefresh ?? true);
      setAutoRefreshInterval(settings.refreshInterval ?? 60);
      setNotifications(settings.emailNotifications ?? true);
      setNotificationEmailTo(settings.notificationEmailTo || '');
      setNotificationEmailFrom(settings.notificationEmailFrom || '');
      setSmtpHost(settings.smtpHost || '');
      setSmtpPort(settings.smtpPort || 587);
      setSmtpUsername(settings.smtpUsername || '');
      setSmtpPassword(settings.smtpPassword || '');
      setSmtpStartTlsEnabled(settings.smtpStartTlsEnabled ?? true);
      setMarketDataProvider(settings.marketDataProvider || 'NONE');
      setMarketDataBaseUrl(settings.marketDataBaseUrl || 'https://api.tiingo.com');
      setMarketDataApiKey(settings.marketDataApiKey || '');
      setDarkMode(settings.darkMode ?? false);
      setConnectionStatus(settings.mongoDbStatus?.connected ? 'connected' : 'unknown');
    }
  }, [settings, setDarkMode]);

  const handleSave = async () => {
    await updateSettings({
      userAgent,
      autoRefresh,
      refreshInterval: autoRefreshInterval,
      emailNotifications: notifications,
      notificationEmailTo,
      notificationEmailFrom,
      smtpHost,
      smtpPort,
      smtpUsername,
      smtpPassword,
      smtpStartTlsEnabled,
      marketDataProvider,
      marketDataBaseUrl,
      marketDataApiKey,
      darkMode,
    });
    setSaved(true);
    window.setTimeout(() => setSaved(false), 3000);
  };

  const handleTestConnection = async () => {
    setConnectionStatus('testing');
    try {
      const result = await checkConnections();
      setConnectionStatus(result.mongoDb.connected ? 'connected' : 'failed');
    } catch {
      setConnectionStatus('failed');
    }
  };

  if (loading && !settings) {
    return <LoadingPage text="Loading settings..." />;
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="flex items-center gap-2">
        <SettingsIcon className="w-8 h-8" />
        Settings
      </h1>

      {/* Error Message */}
      {error && (
        <ErrorMessage
          message={error}
          onRetry={refresh}
        />
      )}

      {/* User Agent Configuration */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">User-Agent Configuration</h2>
        <p className="text-gray-600 mb-4 text-sm">
          The SEC requires all automated requests to include a User-Agent header that identifies the requester.
          This helps them monitor traffic and contact you if necessary.
        </p>
        <div>
          <label className="block text-sm mb-2">Default User-Agent String</label>
          <input
            type="text"
            value={userAgent}
            onChange={(e) => setUserAgent(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p className="text-xs text-gray-500 mt-2">
            Format: ApplicationName/Version (contact email)
          </p>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Market Data For Filing Charts</h2>
        <p className="text-sm text-gray-600 mb-4">
          Configure a market data provider so Form 4 filing pages can draw insider transactions on top of the stock price.
          The first chart view triggers an on-demand download for the required symbol and caches the daily candles locally.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-2">Provider</label>
            <select
              value={marketDataProvider}
              onChange={(e) => setMarketDataProvider(e.target.value as MarketDataProvider)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            >
              <option value="NONE">Disabled</option>
              <option value="TIINGO">Tiingo</option>
            </select>
          </div>
          <div>
            <label className="block text-sm mb-2">Base URL</label>
            <input
              type="text"
              value={marketDataBaseUrl}
              onChange={(e) => setMarketDataBaseUrl(e.target.value)}
              placeholder="https://api.tiingo.com"
              className="w-full px-3 py-2 border border-gray-300 rounded-md"
            />
          </div>
        </div>

        {marketDataProvider === 'TIINGO' && (
          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm mb-2">Tiingo API Key</label>
              <input
                type="password"
                value={marketDataApiKey}
                onChange={(e) => setMarketDataApiKey(e.target.value)}
                placeholder="Your Tiingo token"
                className="w-full px-3 py-2 border border-gray-300 rounded-md"
              />
              {settings?.marketDataConfigured && !settings.marketDataApiKey && (
                <p className="mt-2 text-xs text-gray-500">
                  Using `tiingo.env` from the repo root as the active Tiingo credential source.
                </p>
              )}
            </div>
            <div className="rounded-lg bg-gray-50 p-4 text-sm text-gray-600">
              <p className="font-medium text-gray-900 mb-1">Used by Form 4 detail pages</p>
              <p>
                When a filing chart needs a symbol that is not cached yet, the backend downloads the requested daily range,
                stores it under `data/market-data/tiingo/edgar4j/prices_1d`, and then renders the chart from that local cache.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* API Endpoints */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">API Endpoints</h2>
        <div className="space-y-3">
          <div>
            <p className="text-sm text-gray-600 mb-1">SEC EDGAR API</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">
              {settings?.apiEndpoints?.baseSecUrl || 'https://www.sec.gov'}
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-600 mb-1">Company Tickers</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">
              {settings?.apiEndpoints?.companyTickersUrl || 'https://www.sec.gov/files/company_tickers.json'}
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-600 mb-1">Bulk Data</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">
              {settings?.apiEndpoints?.edgarArchivesUrl || 'https://www.sec.gov/Archives/edgar/data'}
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-600 mb-1">Submissions API</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">
              {settings?.apiEndpoints?.submissionsUrl || 'https://data.sec.gov/submissions'}
            </p>
          </div>
        </div>
      </div>

      {/* Database Connection */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4 flex items-center gap-2">
          <Database className="w-5 h-5" />
          Database Connection
        </h2>
        <div className="flex items-center justify-between mb-4">
          <div>
            <p>Connection Status</p>
            <p className="text-sm text-gray-600">
              {settings?.mongoDbStatus?.message || 'MongoDB localhost:27017'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            {connectionStatus === 'testing' && (
              <>
                <LoadingSpinner size="sm" />
                <span className="text-gray-600">Testing...</span>
              </>
            )}
            {connectionStatus === 'connected' && (
              <>
                <CheckCircle className="w-5 h-5 text-green-500" />
                <span className="text-green-600">Connected</span>
              </>
            )}
            {connectionStatus === 'failed' && (
              <>
                <XCircle className="w-5 h-5 text-red-500" />
                <span className="text-red-600">Failed</span>
              </>
            )}
            {connectionStatus === 'unknown' && (
              <button
                onClick={handleTestConnection}
                className="px-3 py-1 text-sm border border-gray-300 rounded-md hover:bg-gray-50 flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" />
                Test Connection
              </button>
            )}
          </div>
        </div>

        {(settings?.mongoDbStatus || settings?.elasticsearchStatus) && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
            <div>
              <p className="text-sm text-gray-600">MongoDB</p>
              <p className="font-mono text-sm">
                {settings?.mongoDbStatus?.connected ? 'Connected' : 'Disconnected'} {settings?.mongoDbStatus ? `(${settings.mongoDbStatus.latencyMs} ms)` : ''}
              </p>
            </div>
            <div>
              <p className="text-sm text-gray-600">Elasticsearch</p>
              <p className="font-mono text-sm">
                {settings?.elasticsearchStatus?.connected ? 'Connected' : 'Disconnected'} {settings?.elasticsearchStatus ? `(${settings.elasticsearchStatus.latencyMs} ms)` : ''}
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Application Settings */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Application Settings</h2>

        <div className="space-y-4">
          <div className="flex items-center justify-between py-3 border-b border-gray-100">
            <div>
              <p>Dark Mode</p>
              <p className="text-sm text-gray-600">Toggle dark/light theme</p>
            </div>
            <div className="flex items-center gap-3">
              {darkMode ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
              <Switch.Root
                checked={darkMode}
                onCheckedChange={setDarkMode}
                className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
              >
                <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
              </Switch.Root>
            </div>
          </div>

          <div className="flex items-center justify-between py-3 border-b border-gray-100">
            <div>
              <p>Auto Refresh</p>
              <p className="text-sm text-gray-600">Automatically refresh filing data</p>
            </div>
            <div className="flex items-center gap-3">
              {autoRefresh && (
                <select
                  value={autoRefreshInterval}
                  onChange={(e) => setAutoRefreshInterval(Number(e.target.value))}
                  className="px-2 py-1 border border-gray-300 rounded text-sm"
                >
                  <option value={15}>Every 15 min</option>
                  <option value={30}>Every 30 min</option>
                  <option value={60}>Every hour</option>
                  <option value={120}>Every 2 hours</option>
                </select>
              )}
              <Switch.Root
                checked={autoRefresh}
                onCheckedChange={setAutoRefresh}
                className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
              >
                <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
              </Switch.Root>
            </div>
          </div>

          <div className="flex items-center justify-between py-3">
            <div>
              <p>Email Notifications</p>
              <p className="text-sm text-gray-600">Receive alerts for new filings</p>
            </div>
            <Switch.Root
              checked={notifications}
              onCheckedChange={setNotifications}
              className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
            >
              <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
            </Switch.Root>
          </div>

          {notifications && (
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-4">
              <div>
                <p className="text-sm">Email Delivery Configuration</p>
                <p className="text-sm text-gray-600">
                  Configure the SMTP server and sender/recipient addresses required to deliver alerts.
                </p>
                <div className="mt-3">
                  <Link
                    to="/alerts"
                    className="inline-flex items-center px-3 py-2 text-sm border border-gray-300 text-gray-700 rounded-md hover:bg-white"
                  >
                    Configure Alert Rules
                  </Link>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm mb-2">Recipient Email</label>
                  <input
                    type="email"
                    value={notificationEmailTo}
                    onChange={(e) => setNotificationEmailTo(e.target.value)}
                    placeholder="alerts@example.com"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm mb-2">Sender Email</label>
                  <input
                    type="email"
                    value={notificationEmailFrom}
                    onChange={(e) => setNotificationEmailFrom(e.target.value)}
                    placeholder="noreply@example.com"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm mb-2">SMTP Host</label>
                  <input
                    type="text"
                    value={smtpHost}
                    onChange={(e) => setSmtpHost(e.target.value)}
                    placeholder="smtp.example.com"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm mb-2">SMTP Port</label>
                  <input
                    type="number"
                    min={1}
                    value={smtpPort}
                    onChange={(e) => setSmtpPort(Number(e.target.value || 587))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm mb-2">SMTP Username</label>
                  <input
                    type="text"
                    value={smtpUsername}
                    onChange={(e) => setSmtpUsername(e.target.value)}
                    placeholder="smtp-user"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm mb-2">SMTP Password</label>
                  <input
                    type="password"
                    value={smtpPassword}
                    onChange={(e) => setSmtpPassword(e.target.value)}
                    placeholder="smtp-password"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
              </div>

              <div className="flex items-center justify-between py-1">
                <div>
                  <p>StartTLS</p>
                  <p className="text-sm text-gray-600">Enable STARTTLS for the SMTP connection</p>
                </div>
                <Switch.Root
                  checked={smtpStartTlsEnabled}
                  onCheckedChange={setSmtpStartTlsEnabled}
                  className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
                >
                  <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
                </Switch.Root>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Save Button */}
      <div className="flex items-center gap-3">
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors disabled:opacity-50 flex items-center gap-2"
        >
          {saving ? (
            <>
              <LoadingSpinner size="sm" />
              Saving...
            </>
          ) : (
            'Save Settings'
          )}
        </button>
        {saved && (
          <div className="flex items-center gap-2 text-green-600">
            <CheckCircle className="w-5 h-5" />
            <span>Settings saved successfully!</span>
          </div>
        )}
      </div>
    </div>
  );
}
