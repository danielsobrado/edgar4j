import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type AlertRuleType = 'ticker_filing' | 'volume_spike' | 'insider_buy' | 'insider_sell';
export type AlertDelivery = 'immediate' | 'daily_digest';

export interface AlertRule {
  id: string;
  name: string;
  enabled: boolean;
  type: AlertRuleType;
  tickers: string[];
  formTypes?: string[];
  volumePercentThreshold?: number;
  minTransactionValue?: number;
  delivery: AlertDelivery;
}

interface AlertsState {
  rules: AlertRule[];
  addRule: (rule: Omit<AlertRule, 'id'>) => void;
  updateRule: (id: string, updates: Partial<AlertRule>) => void;
  removeRule: (id: string) => void;
}

export const useAlertsStore = create<AlertsState>()(
  persist(
    (set) => ({
      rules: [],
      addRule: (rule) =>
        set((state) => ({
          rules: [
            {
              ...rule,
              id: `alert-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            },
            ...state.rules,
          ],
        })),
      updateRule: (id, updates) =>
        set((state) => ({
          rules: state.rules.map((rule) => (rule.id === id ? { ...rule, ...updates } : rule)),
        })),
      removeRule: (id) =>
        set((state) => ({
          rules: state.rules.filter((rule) => rule.id !== id),
        })),
    }),
    {
      name: 'edgar4j-alerts-store',
    }
  )
);
