import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SettingsState {
  userAgent: string;
  autoRefresh: boolean;
  refreshInterval: number;
  darkMode: boolean;
  emailNotifications: boolean;
  setUserAgent: (userAgent: string) => void;
  setAutoRefresh: (autoRefresh: boolean) => void;
  setRefreshInterval: (refreshInterval: number) => void;
  setDarkMode: (darkMode: boolean) => void;
  setEmailNotifications: (emailNotifications: boolean) => void;
  updateAll: (settings: Partial<SettingsState>) => void;
}

const DEFAULT_SETTINGS_STATE = {
  userAgent: 'Edgar4j/1.0 (contact@example.com)',
  autoRefresh: true,
  refreshInterval: 300,
  darkMode: false,
  emailNotifications: false,
};

function sanitizePersistedSettings(value: unknown): Partial<SettingsState> {
  if (!value || typeof value !== 'object') {
    return {};
  }

  const persisted = value as Partial<SettingsState>;
  return {
    userAgent: typeof persisted.userAgent === 'string' ? persisted.userAgent : undefined,
    autoRefresh: typeof persisted.autoRefresh === 'boolean' ? persisted.autoRefresh : undefined,
    refreshInterval: typeof persisted.refreshInterval === 'number' && persisted.refreshInterval > 0
      ? persisted.refreshInterval
      : undefined,
    darkMode: typeof persisted.darkMode === 'boolean' ? persisted.darkMode : undefined,
    emailNotifications: typeof persisted.emailNotifications === 'boolean' ? persisted.emailNotifications : undefined,
  };
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      ...DEFAULT_SETTINGS_STATE,

      setUserAgent: (userAgent) => set({ userAgent }),
      setAutoRefresh: (autoRefresh) => set({ autoRefresh }),
      setRefreshInterval: (refreshInterval) => set({ refreshInterval }),
      setDarkMode: (darkMode) => set({ darkMode }),
      setEmailNotifications: (emailNotifications) => set({ emailNotifications }),

      updateAll: (settings) => set(settings),
    }),
    {
      name: 'edgar4j-settings-store',
      merge: (persistedState, currentState) => ({
        ...currentState,
        ...sanitizePersistedSettings((persistedState as { state?: unknown } | null)?.state),
      }),
    }
  )
);
