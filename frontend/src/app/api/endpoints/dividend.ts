import { apiClient } from '../client';
import {
  DividendAlerts,
  DividendAlertResolutionRequest,
  DividendComparison,
  DividendEvents,
  DividendFilingEvidence,
  DividendHistory,
  DividendMetricDefinition,
  DividendOverview,
  DividendQuality,
  DividendScreen,
  DividendScreenRequest,
  DividendSyncState,
} from '../types';

interface DividendHistoryOptions {
  metrics?: string[];
  periods?: string;
  years?: number;
}

interface DividendAlertsOptions {
  active?: boolean;
}

interface DividendEventsOptions {
  since?: string;
}

interface DividendCompareOptions {
  metrics?: string[];
}

interface DividendSyncOptions {
  refreshMarketData?: boolean;
}

interface DividendTrackOptions extends DividendSyncOptions {
  syncNow?: boolean;
}

export const dividendApi = {
  getOverview: (tickerOrCik: string): Promise<DividendOverview> => {
    return apiClient.get<DividendOverview>(`/dividend/${encodeURIComponent(tickerOrCik)}`);
  },

  getHistory: (tickerOrCik: string, options: DividendHistoryOptions = {}): Promise<DividendHistory> => {
    return apiClient.get<DividendHistory>(`/dividend/${encodeURIComponent(tickerOrCik)}/history`, {
      params: {
        metrics: options.metrics?.join(','),
        periods: options.periods ?? 'FY',
        years: options.years ?? 15,
      },
    });
  },

  getAlerts: (tickerOrCik: string, options: DividendAlertsOptions = {}): Promise<DividendAlerts> => {
    return apiClient.get<DividendAlerts>(`/dividend/${encodeURIComponent(tickerOrCik)}/alerts`, {
      params: {
        active: options.active ?? false,
      },
    });
  },

  resolveAlert: (
    tickerOrCik: string,
    alertId: string,
    request: DividendAlertResolutionRequest = {},
  ): Promise<DividendAlerts> => {
    return apiClient.post<DividendAlerts>(
      `/dividend/${encodeURIComponent(tickerOrCik)}/alerts/${encodeURIComponent(alertId)}/resolve`,
      request,
    );
  },

  reopenAlert: (
    tickerOrCik: string,
    alertId: string,
    request: DividendAlertResolutionRequest = {},
  ): Promise<DividendAlerts> => {
    return apiClient.delete<DividendAlerts>(
      `/dividend/${encodeURIComponent(tickerOrCik)}/alerts/${encodeURIComponent(alertId)}/resolve`,
      { data: request },
    );
  },

  getEvents: (tickerOrCik: string, options: DividendEventsOptions = {}): Promise<DividendEvents> => {
    return apiClient.get<DividendEvents>(`/dividend/${encodeURIComponent(tickerOrCik)}/events`, {
      params: {
        since: options.since,
      },
    });
  },

  getEvidence: (tickerOrCik: string, accession: string): Promise<DividendFilingEvidence> => {
    return apiClient.get<DividendFilingEvidence>(
      `/dividend/${encodeURIComponent(tickerOrCik)}/evidence/${encodeURIComponent(accession)}`,
    );
  },

  getQuality: (tickerOrCik: string): Promise<DividendQuality> => {
    return apiClient.get<DividendQuality>(`/dividend/${encodeURIComponent(tickerOrCik)}/quality`);
  },

  syncCompany: (tickerOrCik: string, options: DividendSyncOptions = {}): Promise<DividendSyncState> => {
    return apiClient.post<DividendSyncState>(`/dividend/${encodeURIComponent(tickerOrCik)}/sync`, undefined, {
      params: {
        refreshMarketData: options.refreshMarketData ?? true,
      },
    });
  },

  getSyncStatus: (tickerOrCik: string): Promise<DividendSyncState> => {
    return apiClient.get<DividendSyncState>(`/dividend/${encodeURIComponent(tickerOrCik)}/sync`);
  },

  trackCompany: (tickerOrCik: string, options: DividendTrackOptions = {}): Promise<DividendSyncState> => {
    return apiClient.post<DividendSyncState>(`/dividend/${encodeURIComponent(tickerOrCik)}/track`, undefined, {
      params: {
        syncNow: options.syncNow ?? false,
        refreshMarketData: options.refreshMarketData ?? true,
      },
    });
  },

  untrackCompany: (tickerOrCik: string): Promise<DividendSyncState> => {
    return apiClient.delete<DividendSyncState>(`/dividend/${encodeURIComponent(tickerOrCik)}/track`);
  },

  compare: (tickersOrCiks: string[], options: DividendCompareOptions = {}): Promise<DividendComparison> => {
    return apiClient.get<DividendComparison>('/dividend/compare', {
      params: {
        tickers: tickersOrCiks.join(','),
        metrics: options.metrics?.join(','),
      },
    });
  },

  getMetrics: (): Promise<DividendMetricDefinition[]> => {
    return apiClient.get<DividendMetricDefinition[]>('/dividend/metrics');
  },

  screen: (request: DividendScreenRequest): Promise<DividendScreen> => {
    return apiClient.post<DividendScreen>('/dividend/screen', request);
  },
};
