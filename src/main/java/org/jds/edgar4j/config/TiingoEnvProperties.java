package org.jds.edgar4j.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TiingoEnvProperties {

    private static final Path TIINGO_ENV_PATH = Path.of("tiingo.env");

    public Optional<String> getApiToken() {
        return readValue("TIINGO_API_TOKEN");
    }

    public Optional<String> getDataDir() {
        return readValue("TIINGO_DATA_DIR");
    }

    public Optional<String> getBaseUrl() {
        Optional<String> configuredBaseUrl = readValue("TIINGO_API_BASE_URL");
        if (configuredBaseUrl.isPresent()) {
            return configuredBaseUrl.map(this::normalizeBaseUrl);
        }

        return readValue("TIINGO_URL").map(this::normalizeBaseUrl);
    }

    public boolean hasApiToken() {
        return getApiToken().isPresent();
    }

    private Optional<String> readValue(String key) {
        String envValue = trimToNull(System.getenv(key));
        if (envValue != null) {
            return Optional.of(envValue);
        }

        return Optional.ofNullable(trimToNull(loadFileValues().get(key)));
    }

    private Map<String, String> loadFileValues() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(TIINGO_ENV_PATH)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(TIINGO_ENV_PATH);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }

                int separator = line.indexOf('=');
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException e) {
            log.warn("Failed to read tiingo.env: {}", e.getMessage());
        }

        return values;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.replaceAll("/+$", "");
        if (normalized.endsWith("/api/test")) {
            normalized = normalized.substring(0, normalized.length() - "/api/test".length());
        } else if (normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - "/api".length());
        }

        return normalized.replaceAll("/+$", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
