import { apiClient } from '../client';
import { Settings, SettingsRequest, ConnectionStatus } from '../types';

export const settingsApi = {
  getSettings: (): Promise<Settings> => {
    return apiClient.get<Settings>('/settings');
  },

  updateSettings: (request: SettingsRequest): Promise<Settings> => {
    return apiClient.put<Settings>('/settings', request);
  },

  checkMongoDbHealth: (): Promise<ConnectionStatus> => {
    return apiClient.get<ConnectionStatus>('/settings/health/mongodb');
  },

  checkElasticsearchHealth: (): Promise<ConnectionStatus> => {
    return apiClient.get<ConnectionStatus>('/settings/health/elasticsearch');
  },
};
