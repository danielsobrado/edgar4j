package org.jds.edgar4j.integration;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecAccessDiagnostics {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern REFERENCE_ID_PATTERN = Pattern.compile("(?i)reference\\s*id\\s*[:#]?\\s*([^\\r\\n]+)");

    private SecAccessDiagnostics() {
    }

    public static boolean isUndeclaredAutomationBlock(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        String normalized = normalize(responseBody);
        return normalized.contains("your request originates from an undeclared automated tool")
                || normalized.contains("please declare your traffic by updating your user agent")
                || normalized.contains("undeclared automated tool");
    }

    public static boolean isUndeclaredAutomationBlockMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = normalize(message);
        return normalized.contains("undeclared automated tool")
                || normalized.contains("contact opendata@sec.gov")
                || normalized.contains("sec blocked this machine or network");
    }

    public static boolean isUndeclaredAutomationBlock(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (isUndeclaredAutomationBlockMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String extractReferenceId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher = REFERENCE_ID_PATTERN.matcher(toPlainText(responseBody));
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1).trim();
    }

    public static String buildUndeclaredAutomationBlockMessage(String url, String referenceId) {
        StringBuilder message = new StringBuilder(
                "SEC blocked this machine or network as an undeclared automated tool");
        if (url != null && !url.isBlank()) {
            message.append(" while requesting ").append(url);
        }
        message.append(". Verify the egress IP reputation and that User-Agent declares your organization and contact.");
        if (referenceId != null && !referenceId.isBlank()) {
            message.append(" Reference ID: ").append(referenceId).append('.');
        }
        message.append(" See https://www.sec.gov/developer and contact opendata@sec.gov if the block persists.");
        return message.toString();
    }

    private static String normalize(String value) {
        return toPlainText(value).toLowerCase(Locale.ROOT);
    }

    private static String toPlainText(String value) {
        String withoutTags = HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(withoutTags).replaceAll(" ").trim();
    }
}
