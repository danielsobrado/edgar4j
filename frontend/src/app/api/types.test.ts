import { describe, it, expect } from 'vitest';
import { FORM_8K_ITEMS, FORM_TYPES } from './types';

describe('API Types', () => {
  describe('FORM_8K_ITEMS', () => {
    it('should have all major item categories', () => {
      // Section 1 - Registrant's Business and Operations
      expect(FORM_8K_ITEMS['1.01']).toBe('Entry into Material Definitive Agreement');
      expect(FORM_8K_ITEMS['1.02']).toBe('Termination of Material Definitive Agreement');
      expect(FORM_8K_ITEMS['1.03']).toBe('Bankruptcy or Receivership');
      expect(FORM_8K_ITEMS['1.05']).toBe('Material Cybersecurity Incidents');

      // Section 2 - Financial Information
      expect(FORM_8K_ITEMS['2.02']).toBe('Results of Operations and Financial Condition');
      expect(FORM_8K_ITEMS['2.06']).toBe('Material Impairments');

      // Section 5 - Corporate Governance
      expect(FORM_8K_ITEMS['5.02']).toBe('Departure/Appointment of Directors or Officers');
      expect(FORM_8K_ITEMS['5.07']).toBe('Submission of Matters to Vote of Security Holders');

      // Section 7 - Regulation FD
      expect(FORM_8K_ITEMS['7.01']).toBe('Regulation FD Disclosure');

      // Section 8 & 9 - Other
      expect(FORM_8K_ITEMS['8.01']).toBe('Other Events');
      expect(FORM_8K_ITEMS['9.01']).toBe('Financial Statements and Exhibits');
    });

    it('should have items for all 8-K sections (1-9)', () => {
      const sections = Object.keys(FORM_8K_ITEMS).map(k => k.split('.')[0]);
      const uniqueSections = [...new Set(sections)];

      expect(uniqueSections).toContain('1');
      expect(uniqueSections).toContain('2');
      expect(uniqueSections).toContain('3');
      expect(uniqueSections).toContain('4');
      expect(uniqueSections).toContain('5');
      expect(uniqueSections).toContain('6');
      expect(uniqueSections).toContain('7');
      expect(uniqueSections).toContain('8');
      expect(uniqueSections).toContain('9');
    });

    it('should have at least 25 item definitions', () => {
      expect(Object.keys(FORM_8K_ITEMS).length).toBeGreaterThanOrEqual(25);
    });
  });

  describe('FORM_TYPES', () => {
    it('should include major SEC form types', () => {
      const formCodes = FORM_TYPES.map(f => f.value);

      expect(formCodes).toContain('10-K');
      expect(formCodes).toContain('10-Q');
      expect(formCodes).toContain('8-K');
      expect(formCodes).toContain('4');
      expect(formCodes).toContain('13F');
      expect(formCodes).toContain('SC 13D');
      expect(formCodes).toContain('SC 13G');
    });

    it('should have labels for all form types', () => {
      FORM_TYPES.forEach(formType => {
        expect(formType.value).toBeDefined();
        expect(formType.label).toBeDefined();
        expect(formType.label.length).toBeGreaterThan(0);
      });
    });
  });
});
