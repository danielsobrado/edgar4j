package org.jds.edgar4j.integration;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecUserAgentPolicy {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> DISALLOWED_DOMAINS = Set.of(
            "example.com",
            "example.org",
            "example.net",
            "noreply.github.com",
            "users.noreply.github.com");
    private static final int MAX_LENGTH = 200;
    private static final String GUIDANCE =
            "SEC user agent must identify your organization or application and a real contact email, "
                    + "for example 'My Company sec-ops@mycompany.com'. Do not use example.com or noreply addresses.";

    private SecUserAgentPolicy() {
    }

    public static String normalize(String userAgent) {
        if (userAgent == null) {
            return null;
        }

        String normalized = WHITESPACE_PATTERN.matcher(userAgent.trim()).replaceAll(" ");
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isValid(String userAgent) {
        String normalized = normalize(userAgent);
        if (normalized == null || normalized.length() > MAX_LENGTH) {
            return false;
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(normalized);
        if (!emailMatcher.find()) {
            return false;
        }

        String email = emailMatcher.group().toLowerCase(Locale.ROOT);
        if (isDisallowedDomain(email)) {
            return false;
        }

        String descriptor = (normalized.substring(0, emailMatcher.start())
                + " "
                + normalized.substring(emailMatcher.end())).trim();
        return descriptor.chars().anyMatch(Character::isLetter);
    }

    public static String normalizeAndValidate(String userAgent) {
        String normalized = normalize(userAgent);
        if (!isValid(normalized)) {
            throw new IllegalArgumentException(GUIDANCE);
        }
        return normalized;
    }

    public static String guidance() {
        return GUIDANCE;
    }

    private static boolean isDisallowedDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return true;
        }

        String domain = email.substring(atIndex + 1);
        return DISALLOWED_DOMAINS.contains(domain);
    }
}
