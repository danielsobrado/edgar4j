import { describe, expect, it } from 'vitest';
import { normalizeFactsPayload } from './xbrl';

describe('normalizeFactsPayload', () => {
  const sampleFact = {
    concept: 'Assets',
    namespace: 'us-gaap',
    fullNamespace: 'http://fasb.org/us-gaap/2026',
    value: 123,
    rawValue: '123',
    factType: 'MONETARY',
    isNil: false,
    contextRef: 'ctx1',
    contextDescription: 'As of 2025-12-31',
    periodEnd: '2025-12-31',
    unitRef: 'USD',
    unitDisplay: 'USD',
    decimals: 0,
    scale: 0,
  };

  it('returns array payloads unchanged', () => {
    expect(normalizeFactsPayload([sampleFact])).toEqual([sampleFact]);
  });

  it('parses JSON string arrays', () => {
    expect(normalizeFactsPayload(JSON.stringify([sampleFact]))).toEqual([sampleFact]);
  });

  it('unwraps data-wrapped payloads', () => {
    expect(normalizeFactsPayload({ data: [sampleFact] })).toEqual([sampleFact]);
  });

  it('returns an empty array for unsupported payloads', () => {
    expect(normalizeFactsPayload('not json')).toEqual([]);
    expect(normalizeFactsPayload(null)).toEqual([]);
  });
});
