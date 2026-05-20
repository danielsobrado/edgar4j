import { buildInsiderActivityQuery } from './insiderActivity';

describe('buildInsiderActivityQuery', () => {
  it('serializes screener filters and paging options', () => {
    const query = new URLSearchParams(buildInsiderActivityQuery({
      preset: 'MULTI_INSIDER_BUYS',
      view: 'AGGREGATE',
      side: 'BUY',
      transactionCodes: ['P', 'A'],
      dateFrom: '2026-01-01',
      dateTo: '2026-01-31',
      symbol: 'AAPL',
      minPrice: 20,
      minShares: 10000,
      minTotalAmount: 1_000_000,
      minInsiderCount: 2,
      insiderTitle: 'Director',
      sortBy: 'totalValue',
      sortDir: 'asc',
      page: 2,
      size: 25,
    }));

    expect(query.get('preset')).toBe('MULTI_INSIDER_BUYS');
    expect(query.get('view')).toBe('AGGREGATE');
    expect(query.get('side')).toBe('BUY');
    expect(query.get('transactionCodes')).toBe('P,A');
    expect(query.get('dateFrom')).toBe('2026-01-01');
    expect(query.get('dateTo')).toBe('2026-01-31');
    expect(query.get('symbol')).toBe('AAPL');
    expect(query.get('minPrice')).toBe('20');
    expect(query.get('minShares')).toBe('10000');
    expect(query.get('minTotalAmount')).toBe('1000000');
    expect(query.get('minInsiderCount')).toBe('2');
    expect(query.get('insiderTitle')).toBe('Director');
    expect(query.get('sortBy')).toBe('totalValue');
    expect(query.get('sortDir')).toBe('asc');
    expect(query.get('page')).toBe('2');
    expect(query.get('size')).toBe('25');
  });

  it('omits paging for exports', () => {
    const query = new URLSearchParams(buildInsiderActivityQuery({
      preset: 'LATEST_SALES',
      page: 3,
      size: 10,
    }, false));

    expect(query.get('preset')).toBe('LATEST_SALES');
    expect(query.has('page')).toBe(false);
    expect(query.has('size')).toBe(false);
  });
});
