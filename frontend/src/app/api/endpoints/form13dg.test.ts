import { beforeEach, describe, expect, it, vi } from 'vitest';
import { form13dgApi } from './form13dg';
import { apiClient } from '../client';
import type { Form13DG } from '../types';

vi.mock('../client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

const mockedGet = vi.mocked(apiClient.get);

describe('form13dgApi', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('uses backend issuer and filer routes', async () => {
    mockedGet.mockResolvedValue({ content: [] });

    await form13dgApi.getByIssuerCik('0000320193', 1, 25);
    await form13dgApi.searchByIssuerName('Apple Inc.', 2, 10);
    await form13dgApi.getByFilerCik('0001067983', 3, 50);
    await form13dgApi.searchByFilerName('Berkshire Hathaway', 0, 20);

    expect(mockedGet).toHaveBeenNthCalledWith(1, '/form13dg/issuer/cik/0000320193?page=1&size=25');
    expect(mockedGet).toHaveBeenNthCalledWith(2, '/form13dg/issuer?name=Apple+Inc.&page=2&size=10');
    expect(mockedGet).toHaveBeenNthCalledWith(3, '/form13dg/filer/cik/0001067983?page=3&size=50');
    expect(mockedGet).toHaveBeenNthCalledWith(4, '/form13dg/filer?name=Berkshire+Hathaway&page=0&size=20');
  });

  it('uses backend ownership analytics routes', async () => {
    mockedGet.mockResolvedValue([]);

    await form13dgApi.getAboveThreshold(10, 1, 25);
    await form13dgApi.getBeneficialOwners('037833100', 5);
    await form13dgApi.getOwnershipHistory('0001067983', '0000320193');
    await form13dgApi.getFilerPortfolio('0001067983');
    await form13dgApi.getOwnershipSnapshot('037833100');

    expect(mockedGet).toHaveBeenNthCalledWith(1, '/form13dg/ownership/min?minPercent=10&page=1&size=25');
    expect(mockedGet).toHaveBeenNthCalledWith(2, '/form13dg/cusip/037833100/top-owners?limit=5');
    expect(mockedGet).toHaveBeenNthCalledWith(3, '/form13dg/filer/0001067983/issuer/0000320193/history');
    expect(mockedGet).toHaveBeenNthCalledWith(4, '/form13dg/filer/cik/0001067983/portfolio');
    expect(mockedGet).toHaveBeenNthCalledWith(5, '/form13dg/cusip/037833100/ownership');
  });

  it('adapts activist list responses to the paginated shape used by search UI', async () => {
    const filings = [
      { accessionNumber: '1' },
      { accessionNumber: '2' },
      { accessionNumber: '3' },
    ] as Form13DG[];
    mockedGet.mockResolvedValue(filings);

    const page = await form13dgApi.getActivistFilings(1, 2);

    expect(mockedGet).toHaveBeenCalledWith('/form13dg/activist?limit=4');
    expect(page.content).toEqual([{ accessionNumber: '3' }]);
    expect(page.page).toBe(1);
    expect(page.size).toBe(2);
    expect(page.totalElements).toBe(3);
    expect(page.totalPages).toBe(2);
    expect(page.first).toBe(false);
    expect(page.last).toBe(true);
    expect(page.hasPrevious).toBe(true);
    expect(page.hasNext).toBe(false);
  });
});
