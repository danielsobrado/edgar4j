import { describe, expect, it } from 'vitest';
import { normalizeQuarterPeriodInput } from './form13f';

describe('normalizeQuarterPeriodInput', () => {
  it('passes through yyyy-mm-dd values', () => {
    expect(normalizeQuarterPeriodInput('2024-12-31')).toBe('2024-12-31');
  });

  it('converts yyyy-qn input to quarter-end dates', () => {
    expect(normalizeQuarterPeriodInput('2024-Q1')).toBe('2024-03-31');
    expect(normalizeQuarterPeriodInput('2024-Q4')).toBe('2024-12-31');
  });

  it('accepts lowercase q input', () => {
    expect(normalizeQuarterPeriodInput('2024-q2')).toBe('2024-06-30');
  });

  it('rejects invalid quarter input', () => {
    expect(() => normalizeQuarterPeriodInput('Berkshire')).toThrow(
      'Quarter must use YYYY-QN or YYYY-MM-DD format'
    );
  });
});
