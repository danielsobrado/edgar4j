package org.jds.edgar4j.startup;

import java.nio.file.Path;
import java.util.List;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.DataMigrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final Edgar4JProperties properties;
    private final DataMigrationService dataMigrationService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        Edgar4JProperties.Migration migration = properties.getMigration();
        if (migration == null || !StringUtils.hasText(migration.getAction())) {
            return;
        }

        String action = migration.getAction().trim().toLowerCase();
        List<String> collections = migration.getCollections() == null ? List.of() : migration.getCollections();

        try {
            switch (action) {
                case "export" -> executeExport(migration, collections);
                case "import" -> executeImport(migration, collections);
                case "validate" -> executeValidate(migration);
                default -> throw new IllegalArgumentException("Unsupported migration action: " + action);
            }
        } finally {
            applicationContext.close();
        }
    }

    private void executeExport(Edgar4JProperties.Migration migration, List<String> collections) {
        Path targetPath = resolveRequiredPath(migration.getTargetPath(), "target-path");
        DataMigrationService.MigrationReport report = collections.isEmpty()
                ? dataMigrationService.exportAll(targetPath)
                : dataMigrationService.exportCollections(targetPath, collections);
        log.info("Migration export completed: success={}, collections={}, messages={}",
                report.success(), report.collections().keySet(), report.messages());
    }

    private void executeImport(Edgar4JProperties.Migration migration, List<String> collections) {
        Path sourcePath = resolveRequiredPath(migration.getSourcePath(), "source-path");
        DataMigrationService.MigrationReport report = collections.isEmpty()
                ? dataMigrationService.importAll(sourcePath)
                : dataMigrationService.importCollections(sourcePath, collections);
        log.info("Migration import completed: success={}, collections={}, messages={}",
                report.success(), report.collections().keySet(), report.messages());
    }

    private void executeValidate(Edgar4JProperties.Migration migration) {
        Path sourcePath = resolveRequiredPath(migration.getSourcePath(), "source-path");
        DataMigrationService.ValidationReport report = dataMigrationService.validate(sourcePath);
        log.info("Migration validation completed: valid={}, errors={}, warnings={}",
                report.valid(), report.errors(), report.warnings());
    }

    private Path resolveRequiredPath(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("edgar4j.migration." + propertyName + " is required");
        }
        return Path.of(value.trim());
    }
}
