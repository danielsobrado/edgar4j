package org.jds.edgar4j.service.provider;

import java.util.Locale;
import java.util.Set;

public final class MarketDataProviders {

    public static final String NONE = "NONE";
    public static final String TIINGO = "TIINGO";
    public static final String YAHOO_FINANCE = "YAHOOFINANCE";
    public static final String FINNHUB = "FINNHUB";
    public static final String ALPHA_VANTAGE = "ALPHAVANTAGE";

    private static final Set<String> PROVIDER_SERVICE_PROVIDERS = Set.of(
            YAHOO_FINANCE,
            FINNHUB,
            ALPHA_VANTAGE);

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            NONE,
            TIINGO,
            YAHOO_FINANCE,
            FINNHUB,
            ALPHA_VANTAGE);

    private MarketDataProviders() {
    }

    public static String normalize(String providerName) {
        String normalized = normalizeOrNull(providerName);
        return normalized != null ? normalized : NONE;
    }

    public static String normalizeOrNull(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return null;
        }

        String normalized = providerName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isSupported(String providerName) {
        return SUPPORTED_PROVIDERS.contains(normalize(providerName));
    }

    public static boolean isProviderServiceProvider(String providerName) {
        String normalized = normalizeOrNull(providerName);
        return normalized != null && PROVIDER_SERVICE_PROVIDERS.contains(normalized);
    }
}
