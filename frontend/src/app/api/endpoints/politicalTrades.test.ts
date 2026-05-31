import { buildPoliticalTradesQuery, buildPoliticalTradesSyncQuery } from './politicalTrades';

describe('buildPoliticalTradesQuery', () => {
  it('serializes political trade filters and paging options', () => {
    const query = new URLSearchParams(buildPoliticalTradesQuery({
      politician: 'Gottheimer',
      ticker: '$T:US',
      issuer: 'AT&T',
      party: 'Democrat',
      chamber: 'House',
      state: 'NJ',
      assetType: 'stock',
      transactionType: 'SELL',
      owner: 'Spouse',
      tradedDateFrom: '2026-04-01',
      tradedDateTo: '2026-04-30',
      disclosureDateFrom: '2026-05-01',
      disclosureDateTo: '2026-05-31',
      minAmount: 15_000,
      maxAmount: 50_000,
      sortBy: 'disclosureDate',
      sortDir: 'asc',
      page: 2,
      size: 25,
    }));

    expect(query.get('politician')).toBe('Gottheimer');
    expect(query.get('ticker')).toBe('$T:US');
    expect(query.get('issuer')).toBe('AT&T');
    expect(query.get('party')).toBe('Democrat');
    expect(query.get('chamber')).toBe('House');
    expect(query.get('state')).toBe('NJ');
    expect(query.get('assetType')).toBe('stock');
    expect(query.get('transactionType')).toBe('SELL');
    expect(query.get('owner')).toBe('Spouse');
    expect(query.get('tradedDateFrom')).toBe('2026-04-01');
    expect(query.get('tradedDateTo')).toBe('2026-04-30');
    expect(query.get('disclosureDateFrom')).toBe('2026-05-01');
    expect(query.get('disclosureDateTo')).toBe('2026-05-31');
    expect(query.get('minAmount')).toBe('15000');
    expect(query.get('maxAmount')).toBe('50000');
    expect(query.get('sortBy')).toBe('disclosureDate');
    expect(query.get('sortDir')).toBe('asc');
    expect(query.get('page')).toBe('2');
    expect(query.get('size')).toBe('25');
  });

  it('omits paging for exports', () => {
    const query = new URLSearchParams(buildPoliticalTradesQuery({
      assetType: 'ALL',
      page: 3,
      size: 10,
    }, false));

    expect(query.get('assetType')).toBe('ALL');
    expect(query.has('page')).toBe(false);
    expect(query.has('size')).toBe(false);
  });

  it('serializes sync options as query parameters', () => {
    const query = new URLSearchParams(buildPoliticalTradesSyncQuery({
      assetType: 'stock',
      maxPages: 25,
      chunkPages: 5,
      pauseSeconds: 2,
      disclosureDateFrom: '2026-05-29',
      disclosureDateTo: '2026-05-31',
      force: true,
    }));

    expect(query.get('assetType')).toBe('stock');
    expect(query.get('maxPages')).toBe('25');
    expect(query.get('chunkPages')).toBe('5');
    expect(query.get('pauseSeconds')).toBe('2');
    expect(query.get('disclosureDateFrom')).toBe('2026-05-29');
    expect(query.get('disclosureDateTo')).toBe('2026-05-31');
    expect(query.get('force')).toBe('true');
  });
});
