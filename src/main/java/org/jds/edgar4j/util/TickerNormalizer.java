package org.jds.edgar4j.util;

import java.util.Locale;
import java.util.Set;

public final class TickerNormalizer {

    private static final Set<String> INVALID_TICKER_TOKENS = Set.of(
            "N/A",
            "NONE",
            "UNKNOWN");

    private TickerNormalizer() {
    }

    public static String normalize(String ticker) {
        if (ticker == null) {
            return null;
        }

        String normalized = ticker.trim()
                .replace('.', '-')
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || INVALID_TICKER_TOKENS.contains(normalized) || !normalized.matches("[A-Z0-9-]+")) {
            return null;
        }

        return normalized;
    }
}
