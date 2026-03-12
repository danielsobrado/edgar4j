package org.jds.edgar4j.xbrl.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms iXBRL formatted values to their native types.
 * Handles the various iXBRL transformation formats (ixt namespace).
 */
@Slf4j
@Component
public class ValueTransformer {

    // Common number patterns
    private static final Pattern NUMERIC_PATTERN = Pattern.compile(
            "^\\s*([+-])?\\s*\\$?\\s*([\\d,. ]+)\\s*%?\\s*$"
    );

    private static final Pattern SCIENTIFIC_PATTERN = Pattern.compile(
            "^\\s*([+-])?\\s*([\\d.]+)\\s*[eE]\\s*([+-]?\\d+)\\s*$"
    );

    // iXBRL format handlers
    private static final Map<String, FormatHandler> FORMAT_HANDLERS = new HashMap<>();

    static {
        // Numeric formats
        FORMAT_HANDLERS.put("ixt:num-dot-decimal", ValueTransformer::transformDotDecimal);
        FORMAT_HANDLERS.put("ixt:num-comma-decimal", ValueTransformer::transformCommaDecimal);
        FORMAT_HANDLERS.put("ixt:num-unit-decimal", ValueTransformer::transformUnitDecimal);
        FORMAT_HANDLERS.put("ixt:numdotdecimal", ValueTransformer::transformDotDecimal);
        FORMAT_HANDLERS.put("ixt:numcommadecimal", ValueTransformer::transformCommaDecimal);

        // Zero-dash formats
        FORMAT_HANDLERS.put("ixt:num-dot-decimal-apos", ValueTransformer::transformDotDecimal);
        FORMAT_HANDLERS.put("ixt:zerodash", (v) -> BigDecimal.ZERO);
        FORMAT_HANDLERS.put("ixt:zero-dash", (v) -> BigDecimal.ZERO);

        // Negative formats
        FORMAT_HANDLERS.put("ixt:num-dot-decimal-negative", ValueTransformer::transformNegative);
        FORMAT_HANDLERS.put("ixt:num-comma-decimal-negative", ValueTransformer::transformNegativeComma);

        // SEC-specific formats
        FORMAT_HANDLERS.put("ixt-sec:numwordsen", ValueTransformer::transformWordNumber);
        FORMAT_HANDLERS.put("ixt-sec:durwordsen", ValueTransformer::transformDuration);
        FORMAT_HANDLERS.put("ixt-sec:datequarterend", ValueTransformer::transformQuarterEnd);
    }

    /**
     * Transform a raw string value to a BigDecimal using the specified format.
     */
    public BigDecimal transformToNumber(String value, String format) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Trim whitespace
        value = value.trim();

        // Check for nil/empty indicators
        if (value.equals("-") || value.equals("—") || value.equals("–")
                || value.equalsIgnoreCase("nil") || value.equalsIgnoreCase("n/a")) {
            return null;
        }

        // Try format-specific handler first
        if (format != null && !format.isEmpty()) {
            FormatHandler handler = FORMAT_HANDLERS.get(format.toLowerCase());
            if (handler != null) {
                try {
                    return handler.transform(value);
                } catch (Exception e) {
                    log.trace("Format handler failed for {}: {}", format, e.getMessage());
                }
            }
        }

        // Fall back to generic parsing
        return parseGenericNumber(value);
    }

    /**
     * Parse a number using generic heuristics.
     */
    public BigDecimal parseGenericNumber(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Remove common formatting
        String cleaned = value
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("¥", "")
                .replace("%", "")
                .replace(" ", "")
                .replace("\u00A0", "")  // Non-breaking space
                .trim();

        // Handle parentheses for negative
        boolean isNegative = false;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
            isNegative = true;
        }

        // Check for explicit negative sign
        if (cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
            isNegative = true;
        } else if (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
            isNegative = true;
        }

        // Determine decimal separator
        int lastDot = cleaned.lastIndexOf('.');
        int lastComma = cleaned.lastIndexOf(',');

        if (lastDot > lastComma) {
            // Dot is decimal separator, commas are thousands
            cleaned = cleaned.replace(",", "");
        } else if (lastComma > lastDot) {
            // Comma is decimal separator, dots are thousands
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else {
            // No decimal separator, remove all separators
            cleaned = cleaned.replace(",", "").replace(".", "");
        }

        try {
            BigDecimal result = new BigDecimal(cleaned);
            return isNegative ? result.negate() : result;
        } catch (NumberFormatException e) {
            log.trace("Failed to parse number: {}", value);
            return null;
        }
    }

    /**
     * Transform a date string to LocalDate.
     */
    public LocalDate transformToDate(String value, String format) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        value = value.trim();

        // Try common date formats
        String[] formats = {
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "dd/MM/yyyy",
                "MMMM d, yyyy",
                "MMM d, yyyy",
                "d MMMM yyyy",
                "yyyy"
        };

        for (String pattern : formats) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        // Try to extract date from text
        Pattern datePattern = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
        Matcher matcher = datePattern.matcher(value);
        if (matcher.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                );
            } catch (Exception e) {
                // Continue to return null
            }
        }

        return null;
    }

    /**
     * Transform a boolean string value.
     */
    public Boolean transformToBoolean(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String lower = value.trim().toLowerCase();
        if (lower.equals("true") || lower.equals("yes") || lower.equals("1")
                || lower.equals("y") || lower.equals("t")) {
            return true;
        }
        if (lower.equals("false") || lower.equals("no") || lower.equals("0")
                || lower.equals("n") || lower.equals("f")) {
            return false;
        }

        return null;
    }

    // Format-specific handlers

    private static BigDecimal transformDotDecimal(String value) {
        // Format: 1,234,567.89 (dot is decimal, comma is thousands)
        String cleaned = value.replace(",", "").replace(" ", "");
        return new BigDecimal(cleaned);
    }

    private static BigDecimal transformCommaDecimal(String value) {
        // Format: 1.234.567,89 (comma is decimal, dot is thousands)
        String cleaned = value.replace(".", "").replace(",", ".").replace(" ", "");
        return new BigDecimal(cleaned);
    }

    private static BigDecimal transformUnitDecimal(String value) {
        // Format with spaces as thousands separator
        String cleaned = value.replace(" ", "").replace("\u00A0", "");
        return new BigDecimal(cleaned);
    }

    private static BigDecimal transformNegative(String value) {
        BigDecimal result = transformDotDecimal(value.replace("(", "").replace(")", ""));
        return result.negate();
    }

    private static BigDecimal transformNegativeComma(String value) {
        BigDecimal result = transformCommaDecimal(value.replace("(", "").replace(")", ""));
        return result.negate();
    }

    private static BigDecimal transformWordNumber(String value) {
        // Convert word numbers like "one hundred million" to numeric
        Map<String, Long> wordValues = Map.ofEntries(
                Map.entry("zero", 0L),
                Map.entry("one", 1L),
                Map.entry("two", 2L),
                Map.entry("three", 3L),
                Map.entry("four", 4L),
                Map.entry("five", 5L),
                Map.entry("six", 6L),
                Map.entry("seven", 7L),
                Map.entry("eight", 8L),
                Map.entry("nine", 9L),
                Map.entry("ten", 10L),
                Map.entry("eleven", 11L),
                Map.entry("twelve", 12L),
                Map.entry("hundred", 100L),
                Map.entry("thousand", 1000L),
                Map.entry("million", 1000000L),
                Map.entry("billion", 1000000000L),
                Map.entry("trillion", 1000000000000L)
        );

        String[] words = value.toLowerCase().split("\\s+");
        long result = 0;
        long current = 0;

        for (String word : words) {
            Long val = wordValues.get(word);
            if (val != null) {
                if (val >= 100) {
                    current = current == 0 ? val : current * val;
                    if (val >= 1000) {
                        result += current;
                        current = 0;
                    }
                } else {
                    current += val;
                }
            }
        }

        result += current;
        return BigDecimal.valueOf(result);
    }

    private static BigDecimal transformDuration(String value) {
        // Convert duration words to days
        // "two years" -> 730
        return transformWordNumber(value);
    }

    private static BigDecimal transformQuarterEnd(String value) {
        // Q1 2024 -> returns month number or similar
        return null;  // Would need date context
    }

    @FunctionalInterface
    interface FormatHandler {
        BigDecimal transform(String value);
    }
}
