import { apiClient } from '../client';
import { Settings, SettingsRequest, ConnectionStatus, UsaSpendingColumnPreferences } from '../types';

export const settingsApi = {
  getSettings: (): Promise<Settings> => {
    return apiClient.get<Settings>('/settings');
  },

  updateSettings: (request: SettingsRequest): Promise<Settings> => {
    return apiClient.put<Settings>('/settings', request);
  },

  getUsaSpendingColumnPreferences: (): Promise<UsaSpendingColumnPreferences> => {
    return apiClient.get<UsaSpendingColumnPreferences>('/settings/usaspending/columns');
  },

  updateUsaSpendingColumnPreferences: (hiddenColumns: string[]): Promise<UsaSpendingColumnPreferences> => {
    return apiClient.put<UsaSpendingColumnPreferences>('/settings/usaspending/columns', { hiddenColumns });
  },

  checkMongoDbHealth: (): Promise<ConnectionStatus> => {
    return apiClient.get<ConnectionStatus>('/settings/health/mongodb');
  },

  checkElasticsearchHealth: (): Promise<ConnectionStatus> => {
    return apiClient.get<ConnectionStatus>('/settings/health/elasticsearch');
  },
};
