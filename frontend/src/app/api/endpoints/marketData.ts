import { apiClient } from '../client';
import { MarketPriceHistory } from '../types';

export const marketDataApi = {
  getPriceHistory: (ticker: string, startDate: string, endDate: string): Promise<MarketPriceHistory> => {
    const params = new URLSearchParams({ startDate, endDate });
    return apiClient.get<MarketPriceHistory>(
      `/market-data/prices/${encodeURIComponent(ticker)}?${params.toString()}`,
      { timeout: 60000 }
    );
  },
};
