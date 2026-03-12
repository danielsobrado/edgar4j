import type { MarketPriceHistory } from '../api';

export type MarketCloseLookup = {
  byDate: Map<string, number>;
  dates: string[];
};

export type ResolvedMarketClose = {
  close: number;
  sourceDate: string;
};

export function buildMarketCloseLookup(
  history: Pick<MarketPriceHistory, 'prices'> | null | undefined,
): MarketCloseLookup | null {
  if (!history || history.prices.length === 0) {
    return null;
  }

  const byDate = new Map<string, number>();
  history.prices.forEach((price) => {
    byDate.set(price.date, price.close);
  });

  return {
    byDate,
    dates: Array.from(byDate.keys()).sort(),
  };
}

export function mergeMarketCloseLookups(
  existing: MarketCloseLookup | null,
  incoming: MarketCloseLookup | null,
): MarketCloseLookup | null {
  if (!existing) {
    return incoming;
  }
  if (!incoming) {
    return existing;
  }

  const byDate = new Map(existing.byDate);
  incoming.byDate.forEach((close, date) => {
    byDate.set(date, close);
  });

  return {
    byDate,
    dates: Array.from(byDate.keys()).sort(),
  };
}

export function resolveEstimatedClose(
  date: string,
  marketCloseLookup: MarketCloseLookup | null,
): ResolvedMarketClose | null {
  if (!marketCloseLookup) {
    return null;
  }

  const exactClose = marketCloseLookup.byDate.get(date);
  if (exactClose != null) {
    return { close: exactClose, sourceDate: date };
  }

  for (let index = marketCloseLookup.dates.length - 1; index >= 0; index -= 1) {
    const marketDate = marketCloseLookup.dates[index];
    if (marketDate <= date) {
      const fallbackClose = marketCloseLookup.byDate.get(marketDate);
      if (fallbackClose != null) {
        return { close: fallbackClose, sourceDate: marketDate };
      }
    }
  }

  return null;
}
