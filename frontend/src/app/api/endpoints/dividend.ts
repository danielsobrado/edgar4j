import { apiClient } from '../client';
import {
  DividendAlerts,
  DividendComparison,
  DividendEvents,
  DividendFilingEvidence,
  DividendHistory,
  DividendMetricDefinition,
  DividendOverview,
  DividendScreen,
  DividendScreenRequest,
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
