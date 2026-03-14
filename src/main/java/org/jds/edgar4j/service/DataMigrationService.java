package org.jds.edgar4j.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DataMigrationService {

    MigrationReport exportAll(Path targetDir);

    MigrationReport exportCollections(Path targetDir, List<String> collections);

    MigrationReport exportCollection(String name, Path targetDir);

    MigrationReport importAll(Path sourceDir);

    MigrationReport importCollections(Path sourceDir, List<String> collections);

    MigrationReport importCollection(String name, Path sourceDir);

    ValidationReport validate(Path sourceDir);

    record MigrationReport(
            String action,
            boolean success,
            Path location,
            String sourceMode,
            Map<String, CollectionReport> collections,
            List<String> messages,
            Instant completedAt) {
    }

    record CollectionReport(
            String name,
            String file,
            String format,
            long recordCount,
            String checksum,
            Instant processedAt) {
    }

    record ValidationReport(
            boolean valid,
            List<String> errors,
            List<String> warnings,
            Map<String, Long> recordCounts,
            Map<String, String> checksumMismatches) {
    }

    record MigrationManifest(
            String version,
            Instant exportDate,
            String sourceMode,
            String edgar4jVersion,
            Map<String, CollectionManifestEntry> collections) {
    }

    record CollectionManifestEntry(
            String file,
            String format,
            long recordCount,
            String checksum,
            Instant exportedAt) {
    }
}
