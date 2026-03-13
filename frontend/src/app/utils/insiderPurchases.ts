export function buildForm4SearchUrl({
  ticker,
  cik,
}: {
  ticker?: string | null;
  cik?: string | null;
}): string {
  const params = new URLSearchParams({
    formType: '4',
    autoSearch: '1',
  });

  const normalizedTicker = ticker?.trim().toUpperCase();
  const normalizedCik = cik?.trim();

  if (normalizedTicker) {
    params.set('ticker', normalizedTicker);
  } else if (normalizedCik) {
    params.set('cik', normalizedCik);
  }

  return `/search?${params.toString()}`;
}
