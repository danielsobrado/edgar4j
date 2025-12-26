import React from 'react';
import { Settings as SettingsIcon, CheckCircle, XCircle, Sun, Moon } from 'lucide-react';
import * as Switch from '@radix-ui/react-switch';

export function Settings() {
  const [userAgent, setUserAgent] = React.useState('edgar4j/1.0 (contact@example.com)');
  const [darkMode, setDarkMode] = React.useState(false);
  const [autoRefresh, setAutoRefresh] = React.useState(true);
  const [notifications, setNotifications] = React.useState(true);
  const [saved, setSaved] = React.useState(false);
  
  const handleSave = () => {
    console.log('Saving settings...');
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };
  
  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="flex items-center gap-2">
        <SettingsIcon className="w-8 h-8" />
        Settings
      </h1>
      
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
      
      {/* API Endpoints */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">API Endpoints</h2>
        <div className="space-y-3">
          <div>
            <p className="text-sm text-gray-600 mb-1">SEC EDGAR API</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">https://data.sec.gov/</p>
          </div>
          <div>
            <p className="text-sm text-gray-600 mb-1">Company Tickers</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">https://www.sec.gov/files/company_tickers.json</p>
          </div>
          <div>
            <p className="text-sm text-gray-600 mb-1">Bulk Data</p>
            <p className="font-mono text-sm bg-gray-50 p-2 rounded">https://www.sec.gov/Archives/edgar/daily-index/</p>
          </div>
        </div>
      </div>
      
      {/* Elasticsearch Connection */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="mb-4">Elasticsearch Connection</h2>
        <div className="flex items-center justify-between mb-4">
          <div>
            <p>Connection Status</p>
            <p className="text-sm text-gray-600">localhost:9200</p>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle className="w-5 h-5 text-green-500" />
            <span className="text-green-600">Connected</span>
          </div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4 bg-gray-50 rounded-lg">
          <div>
            <p className="text-sm text-gray-600">Index</p>
            <p className="font-mono text-sm">edgar_filings</p>
          </div>
          <div>
            <p className="text-sm text-gray-600">Documents</p>
            <p className="font-mono text-sm">45,782</p>
          </div>
          <div>
            <p className="text-sm text-gray-600">Size</p>
            <p className="font-mono text-sm">2.4 GB</p>
          </div>
        </div>
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
              <p className="text-sm text-gray-600">Automatically refresh filing data every hour</p>
            </div>
            <Switch.Root
              checked={autoRefresh}
              onCheckedChange={setAutoRefresh}
              className="w-11 h-6 bg-gray-200 rounded-full relative data-[state=checked]:bg-blue-500 transition-colors"
            >
              <Switch.Thumb className="block w-5 h-5 bg-white rounded-full transition-transform translate-x-0.5 data-[state=checked]:translate-x-[22px]" />
            </Switch.Root>
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
        </div>
      </div>
      
      {/* Save Button */}
      <div className="flex items-center gap-3">
        <button
          onClick={handleSave}
          className="px-6 py-2 bg-[#1a1f36] text-white rounded-md hover:bg-[#252b47] transition-colors"
        >
          Save Settings
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
