import { useState, useEffect, useCallback } from 'react';
import { settingsApi, Settings, SettingsRequest } from '../api';

export function useSettings() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSettings = useCallback(async () => {
    setLoading(true);
    try {
      const data = await settingsApi.getSettings();
      setSettings(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load settings');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSettings();
  }, [fetchSettings]);

  const updateSettings = useCallback(async (request: SettingsRequest) => {
    setSaving(true);
    setError(null);
    try {
      const data = await settingsApi.updateSettings(request);
      setSettings(data);
      return data;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to save settings';
      setError(message);
      throw err;
    } finally {
      setSaving(false);
    }
  }, []);

  const checkConnections = useCallback(async () => {
    try {
      const [mongoDb, elasticsearch] = await Promise.all([
        settingsApi.checkMongoDbHealth(),
        settingsApi.checkElasticsearchHealth(),
      ]);

      setSettings(prev => prev ? {
        ...prev,
        mongoDbStatus: mongoDb,
        elasticsearchStatus: elasticsearch,
      } : null);
    } catch (err) {
      console.error('Failed to check connections:', err);
    }
  }, []);

  return {
    settings,
    loading,
    saving,
    error,
    updateSettings,
    checkConnections,
    refresh: fetchSettings,
  };
}
