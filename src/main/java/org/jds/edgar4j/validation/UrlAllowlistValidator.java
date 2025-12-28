package org.jds.edgar4j.validation;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jds.edgar4j.properties.XbrlProperties;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UrlAllowlistValidator {

    private final XbrlProperties xbrlProperties;

    public void validateXbrlUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("url must be a valid URI");
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("url must include scheme and host");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);

        if ("http".equals(scheme) && xbrlProperties.isAllowHttpLocalhost() && isLocalhost(host)) {
            return;
        }

        if (!"https".equals(scheme)) {
            throw new IllegalArgumentException("only https URLs are allowed");
        }

        List<String> allowedHosts = Objects.requireNonNullElse(xbrlProperties.getAllowedHosts(), List.<String>of()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("URL access is not configured");
        }

        boolean allowed = allowedHosts.stream().anyMatch(allowedHost ->
                host.equals(allowedHost) || host.endsWith("." + allowedHost));

        if (!allowed) {
            throw new IllegalArgumentException("url host is not allowed");
        }
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }
}
