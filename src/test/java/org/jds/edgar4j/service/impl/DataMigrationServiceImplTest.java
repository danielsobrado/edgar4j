package org.jds.edgar4j.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.port.CompanyDataPort;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.port.TransactionTypeDataPort;
import org.jds.edgar4j.service.DataMigrationService;
import org.jds.edgar4j.storage.file.FileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test-low", "resource-low"})
class DataMigrationServiceImplTest {

    private static final Path DATA_PATH = createDirectory("migration-data");
    private static final Path EXPORT_PATH = createDirectory("migration-export");
    private static final String H2_URL = "jdbc:h2:file:" + DATA_PATH.resolve("batch")
            .resolve("edgar4j")
            .toAbsolutePath()
            .toString()
            .replace("\\", "/") + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private Form4DataPort form4DataPort;

    @Autowired
    private CompanyDataPort companyDataPort;

    @Autowired
    private TickerDataPort tickerDataPort;

    @Autowired
    private TransactionTypeDataPort transactionTypeDataPort;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("edgar4j.storage.file.base-path", () -> DATA_PATH.toAbsolutePath().toString());
        registry.add("spring.datasource.url", () -> H2_URL);
    }

    @BeforeEach
    void setUp() throws IOException {
        form4DataPort.deleteAll();
        companyDataPort.deleteAll();
        tickerDataPort.deleteAll();
        transactionTypeDataPort.deleteAll();
        deleteExportDirectoryContents();
    }

    @Test
    @DisplayName("exportAll, validate, and importAll round-trip the supported collections")
    void exportValidateAndImportRoundTrip() throws IOException {
        form4DataPort.save(TestFixtures.createTestForm4("0001234567-24-000001", "AAPL", LocalDate.of(2024, 1, 15)));
        companyDataPort.save(TestFixtures.createTestCompany("0000320193", "AAPL"));
        tickerDataPort.save(TestFixtures.createTestTicker("AAPL", "0000320193"));
        transactionTypeDataPort.save(TransactionType.builder()
                .transactionCode("P")
                .transactionName("Purchase")
                .transactionDescription("Open market purchase")
                .transactionCategory(TransactionType.TransactionCategory.PURCHASE)
                .isActive(true)
                .sortOrder(1)
                .build());

        DataMigrationService.MigrationReport exportReport = dataMigrationService.exportAll(EXPORT_PATH);
        DataMigrationService.ValidationReport validationReport = dataMigrationService.validate(EXPORT_PATH);

        form4DataPort.deleteAll();
        companyDataPort.deleteAll();
        tickerDataPort.deleteAll();
        transactionTypeDataPort.deleteAll();

        DataMigrationService.MigrationReport importReport = dataMigrationService.importAll(EXPORT_PATH);

        assertThat(exportReport.success()).isTrue();
        assertThat(validationReport.valid()).isTrue();
        assertThat(importReport.success()).isTrue();
        assertThat(EXPORT_PATH.resolve("manifest.json")).exists();
        assertThat(EXPORT_PATH.resolve("collections").resolve("form4.jsonl")).exists();
        assertThat(EXPORT_PATH.resolve("tables").resolve("transaction_types.csv")).exists();
        assertThat(form4DataPort.findByAccessionNumber("0001234567-24-000001")).isPresent();
        assertThat(companyDataPort.count()).isEqualTo(1);
        assertThat(tickerDataPort.findByCode("AAPL")).isPresent();
        assertThat(transactionTypeDataPort.findByTransactionCode("P")).isPresent();
    }

    @Test
    @DisplayName("importCollection restores the previous collection when import parsing fails")
    void importCollectionRestoresBackupWhenImportFails() throws IOException {
        form4DataPort.save(TestFixtures.createTestForm4("0001234567-24-ORIGINAL", "AAPL", LocalDate.of(2024, 1, 15)));
        prepareCorruptForm4Import();

        DataMigrationService.MigrationReport importReport = dataMigrationService.importCollection("form4", EXPORT_PATH);

        assertThat(importReport.success()).isFalse();
        assertThat(form4DataPort.count()).isEqualTo(1);
        assertThat(form4DataPort.findByAccessionNumber("0001234567-24-ORIGINAL")).isPresent();
        assertThat(form4DataPort.findByAccessionNumber("0001234567-24-NEW")).isEmpty();
    }

    private void deleteExportDirectoryContents() throws IOException {
        if (!Files.exists(EXPORT_PATH)) {
            Files.createDirectories(EXPORT_PATH);
            return;
        }

        try (var paths = Files.walk(EXPORT_PATH)) {
            paths.filter(path -> !EXPORT_PATH.equals(path))
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Path createDirectory(String prefix) {
        try {
            Path path = Path.of("target", prefix, UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void prepareCorruptForm4Import() throws IOException {
        Path collectionsDir = EXPORT_PATH.resolve("collections");
        Files.createDirectories(collectionsDir);

        Path form4File = collectionsDir.resolve("form4.jsonl");
        Path companiesFile = collectionsDir.resolve("companies.jsonl");
        Path tickersFile = collectionsDir.resolve("tickers.json");

        String validForm4 = objectMapper.writeValueAsString(
                TestFixtures.createTestForm4("0001234567-24-NEW", "MSFT", LocalDate.of(2024, 2, 1)));
        Files.writeString(form4File, validForm4 + System.lineSeparator() + "{\"invalid\":", StandardCharsets.UTF_8);
        Files.writeString(companiesFile, "", StandardCharsets.UTF_8);
        Files.writeString(tickersFile, "", StandardCharsets.UTF_8);

        Instant exportedAt = Instant.now();
        Map<String, DataMigrationService.CollectionManifestEntry> collections = new LinkedHashMap<>();
        collections.put("form4", manifestEntry(form4File, FileFormat.JSONL, 2L, exportedAt));
        collections.put("companies", manifestEntry(companiesFile, FileFormat.JSONL, 0L, exportedAt));
        collections.put("tickers", manifestEntry(tickersFile, FileFormat.JSON, 0L, exportedAt));

        DataMigrationService.MigrationManifest manifest = new DataMigrationService.MigrationManifest(
                "1.0",
                exportedAt,
                "low",
                "test",
                collections);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(EXPORT_PATH.resolve("manifest.json").toFile(), manifest);
    }

    private DataMigrationService.CollectionManifestEntry manifestEntry(
            Path file,
            FileFormat format,
            long recordCount,
            Instant exportedAt) {
        return new DataMigrationService.CollectionManifestEntry(
                EXPORT_PATH.relativize(file).toString().replace('\\', '/'),
                format.name().toLowerCase(),
                recordCount,
                checksum(file),
                exportedAt);
    }

    private String checksum(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
