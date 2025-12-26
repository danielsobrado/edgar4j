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

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      userAgent: 'Edgar4j/1.0 (contact@example.com)',
      autoRefresh: true,
      refreshInterval: 300,
      darkMode: false,
      emailNotifications: false,

      setUserAgent: (userAgent) => set({ userAgent }),
      setAutoRefresh: (autoRefresh) => set({ autoRefresh }),
      setRefreshInterval: (refreshInterval) => set({ refreshInterval }),
      setDarkMode: (darkMode) => set({ darkMode }),
      setEmailNotifications: (emailNotifications) => set({ emailNotifications }),

      updateAll: (settings) => set(settings),
    }),
    {
      name: 'edgar4j-settings-store',
    }
  )
);
