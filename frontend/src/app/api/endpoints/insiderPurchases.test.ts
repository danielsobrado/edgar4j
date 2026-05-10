import { buildInsiderPurchasesQuery } from './insiderPurchases';

describe('buildInsiderPurchasesQuery', () => {
  it('includes supported filters and paging options', () => {
    const query = new URLSearchParams(buildInsiderPurchasesQuery({
      lookbackDays: 14,
      minMarketCap: 1_000_000_000,
      sp500Only: true,
      minTransactionValue: 100_000,
      sortBy: 'transactionValue',
      sortDir: 'asc',
      page: 2,
      size: 25,
    }));

    expect(query.get('lookbackDays')).toBe('14');
    expect(query.get('minMarketCap')).toBe('1000000000');
    expect(query.get('sp500Only')).toBe('true');
    expect(query.get('minTransactionValue')).toBe('100000');
    expect(query.get('sortBy')).toBe('transactionValue');
    expect(query.get('sortDir')).toBe('asc');
    expect(query.get('page')).toBe('2');
    expect(query.get('size')).toBe('25');
  });

  it('applies default paging and omits empty filters', () => {
    const query = new URLSearchParams(buildInsiderPurchasesQuery({
      minMarketCap: 0,
      minTransactionValue: 0,
      sp500Only: false,
    }));

    expect(query.get('page')).toBe('0');
    expect(query.get('size')).toBe('50');
    expect(query.has('minMarketCap')).toBe(false);
    expect(query.has('minTransactionValue')).toBe(false);
    expect(query.has('sp500Only')).toBe(false);
  });
});

