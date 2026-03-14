package org.jds.edgar4j.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class LowModeTestSupport {

    private LowModeTestSupport() {
    }

    public static void registerLowModeProperties(DynamicPropertyRegistry registry, String scope) {
        Path dataPath = createDataPath(scope);
        String h2Url = "jdbc:h2:file:" + dataPath.resolve("batch")
                .resolve("edgar4j")
                .toAbsolutePath()
                .toString()
                .replace("\\", "/") + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";

        registry.add("edgar4j.storage.file.base-path", () -> dataPath.toAbsolutePath().toString());
        registry.add("spring.datasource.url", () -> h2Url);
    }

    private static Path createDataPath(String scope) {
        try {
            Path path = Path.of("target", "contract-tests", scope, UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(path);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create test data path", exception);
        }
    }
}