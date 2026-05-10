import { buildForm4SearchUrl } from './insiderPurchases';

describe('buildForm4SearchUrl', () => {
  it('builds a Form 4 search URL from a ticker', () => {
    expect(buildForm4SearchUrl({ ticker: ' msft ' })).toBe('/search?formType=4&autoSearch=1&ticker=MSFT');
  });

  it('falls back to CIK when the ticker is unavailable', () => {
    expect(buildForm4SearchUrl({ cik: '0000789019' })).toBe('/search?formType=4&autoSearch=1&cik=0000789019');
  });

  it('returns the base Form 4 search URL when no company identifier is provided', () => {
    expect(buildForm4SearchUrl({})).toBe('/search?formType=4&autoSearch=1');
  });
});

