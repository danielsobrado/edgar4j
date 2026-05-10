package org.jds.edgar4j.integration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared date parser used by ownership and filing parsers.
 */
final class ParserDateUtils {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/d"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("MMddyyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy")
    };

    private ParserDateUtils() {
    }

    static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = dateStr.trim();

        // Remove surrounding whitespace and common time suffix.
        if (trimmed.length() > 10) {
            if (trimmed.contains("T")) {
                trimmed = trimmed.substring(0, trimmed.indexOf("T"));
            } else {
                int spaceIdx = trimmed.indexOf(' ');
                if (spaceIdx >= 0) {
                    trimmed = trimmed.substring(0, Math.min(spaceIdx, trimmed.length()));
                }
            }
            if (trimmed.length() > 10 && trimmed.charAt(10) == ' ') {
                trimmed = trimmed.substring(0, 10);
            }
            trimmed = trimmed.trim();
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }
}
