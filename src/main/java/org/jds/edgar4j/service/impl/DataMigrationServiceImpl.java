package org.jds.edgar4j.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jds.edgar4j.config.ResourceModeInfo;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.DividendSyncState;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.model.SearchHistory;
import org.jds.edgar4j.model.Sp500Constituent;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.port.CompanyDataPort;
import org.jds.edgar4j.port.CompanyMarketDataDataPort;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.port.DividendSyncStateDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.Form13DGDataPort;
import org.jds.edgar4j.port.Form13FDataPort;
import org.jds.edgar4j.port.Form20FDataPort;
import org.jds.edgar4j.port.Form3DataPort;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.port.Form5DataPort;
import org.jds.edgar4j.port.Form6KDataPort;
import org.jds.edgar4j.port.Form8KDataPort;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.port.InsiderDataPort;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.port.SearchHistoryDataPort;
import org.jds.edgar4j.port.Sp500ConstituentDataPort;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.port.TransactionTypeDataPort;
import org.jds.edgar4j.service.DataMigrationService;
import org.jds.edgar4j.storage.file.FileFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService {

    private static final int PAGE_SIZE = 500;
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String EXPORT_VERSION = "1.0";
    private static final Set<String> REQUIRED_COLLECTIONS = Set.of("form4", "companies", "tickers");

    private final ObjectMapper objectMapper;
    private final ResourceModeInfo resourceModeInfo;
    private final AppSettingsDataPort appSettingsDataPort;
    private final CompanyDataPort companyDataPort;
    private final CompanyMarketDataDataPort companyMarketDataDataPort;
    private final CompanyTickerDataPort companyTickerDataPort;
    private final DownloadJobDataPort downloadJobDataPort;
    private final DividendSyncStateDataPort dividendSyncStateDataPort;
    private final FillingDataPort fillingDataPort;
    private final Form3DataPort form3DataPort;
    private final Form4DataPort form4DataPort;
    private final Form5DataPort form5DataPort;
    private final Form6KDataPort form6KDataPort;
    private final Form8KDataPort form8KDataPort;
    private final Form13DGDataPort form13DGDataPort;
    private final Form13FDataPort form13FDataPort;
    private final Form20FDataPort form20FDataPort;
    private final SearchHistoryDataPort searchHistoryDataPort;
    private final Sp500ConstituentDataPort sp500ConstituentDataPort;
    private final SubmissionsDataPort submissionsDataPort;
    private final TickerDataPort tickerDataPort;
    private final InsiderCompanyDataPort insiderCompanyDataPort;
    private final InsiderCompanyRelationshipDataPort insiderCompanyRelationshipDataPort;
    private final InsiderDataPort insiderDataPort;
    private final InsiderTransactionDataPort insiderTransactionDataPort;
    private final TransactionTypeDataPort transactionTypeDataPort;

    private final Map<String, CollectionHandler<?>> collectionHandlers = new LinkedHashMap<>();
    private ObjectMapper migrationObjectMapper;

    @PostConstruct
    void initializeCollectionHandlers() {
        migrationObjectMapper = objectMapper.copy()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

        migrationObjectMapper.addMixIn(Insider.class, InsiderMixin.class);
        migrationObjectMapper.addMixIn(org.jds.edgar4j.model.insider.Company.class, InsiderCompanyMixin.class);
        migrationObjectMapper.addMixIn(TransactionType.class, TransactionTypeMixin.class);

        registerJsonl("app_settings", FileFormat.JSON, AppSettings.class, appSettingsDataPort);
        registerJsonl("companies", FileFormat.JSONL, Company.class, companyDataPort);
        registerJsonl("company_market_data", FileFormat.JSONL, CompanyMarketData.class, companyMarketDataDataPort);
        registerJsonl("company_tickers", FileFormat.JSON, CompanyTicker.class, companyTickerDataPort);
        registerJsonl("download_jobs", FileFormat.JSONL, DownloadJob.class, downloadJobDataPort);
        registerJsonl("dividend_sync_states", FileFormat.JSONL, DividendSyncState.class, dividendSyncStateDataPort);
        registerJsonl("fillings", FileFormat.JSONL, Filling.class, fillingDataPort);
        registerJsonl("form3", FileFormat.JSONL, Form3.class, form3DataPort);
        registerJsonl("form4", FileFormat.JSONL, Form4.class, form4DataPort);
        registerJsonl("form5", FileFormat.JSONL, Form5.class, form5DataPort);
        registerJsonl("form6k", FileFormat.JSONL, Form6K.class, form6KDataPort);
        registerJsonl("form8k", FileFormat.JSONL, Form8K.class, form8KDataPort);
        registerJsonl("form13dg", FileFormat.JSONL, Form13DG.class, form13DGDataPort);
        registerJsonl("form13f", FileFormat.JSONL, Form13F.class, form13FDataPort);
        registerJsonl("form20f", FileFormat.JSONL, Form20F.class, form20FDataPort);
        registerJsonl("search_history", FileFormat.JSONL, SearchHistory.class, searchHistoryDataPort);
        registerJsonl("sp500_constituents", FileFormat.JSONL, Sp500Constituent.class, sp500ConstituentDataPort);
        registerJsonl("submissions", FileFormat.JSON, Submissions.class, submissionsDataPort);
        registerJsonl("tickers", FileFormat.JSON, Ticker.class, tickerDataPort);
        registerJsonl("insider_companies", FileFormat.JSONL, org.jds.edgar4j.model.insider.Company.class, insiderCompanyDataPort);
        registerJsonl("insider_company_relationships", FileFormat.JSONL, InsiderCompanyRelationship.class, insiderCompanyRelationshipDataPort);
        registerJsonl("insiders", FileFormat.JSONL, Insider.class, insiderDataPort);
        registerJsonl("insider_transactions", FileFormat.JSONL, InsiderTransaction.class, insiderTransactionDataPort);
        registerCsv("transaction_types", TransactionType.class, transactionTypeDataPort);
    }

    @Override
    public MigrationReport exportAll(Path targetDir) {
        return exportSelected(targetDir, collectionHandlers.keySet());
    }

    @Override
    public MigrationReport exportCollections(Path targetDir, List<String> collections) {
        return exportSelected(targetDir, collections);
    }

    @Override
    public MigrationReport exportCollection(String name, Path targetDir) {
        return exportSelected(targetDir, List.of(name));
    }

    @Override
    public MigrationReport importAll(Path sourceDir) {
        return importSelected(sourceDir, collectionHandlers.keySet());
    }

    @Override
    public MigrationReport importCollections(Path sourceDir, List<String> collections) {
        return importSelected(sourceDir, collections);
    }

    @Override
    public MigrationReport importCollection(String name, Path sourceDir) {
        return importSelected(sourceDir, List.of(name));
    }

    @Override
    public ValidationReport validate(Path sourceDir) {
        Path manifestPath = normalize(sourceDir).resolve(MANIFEST_FILE);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Long> recordCounts = new LinkedHashMap<>();
        Map<String, String> checksumMismatches = new LinkedHashMap<>();

        if (!Files.exists(manifestPath)) {
            return new ValidationReport(false, List.of("Missing manifest.json"), warnings, recordCounts, checksumMismatches);
        }

        try {
            MigrationManifest manifest = migrationObjectMapper.readValue(manifestPath.toFile(), MigrationManifest.class);

            for (String requiredCollection : REQUIRED_COLLECTIONS) {
                if (!manifest.collections().containsKey(requiredCollection)) {
                    errors.add("Missing required collection in manifest: " + requiredCollection);
                }
            }

            for (Map.Entry<String, CollectionManifestEntry> entry : manifest.collections().entrySet()) {
                String collectionName = entry.getKey();
                CollectionManifestEntry manifestEntry = entry.getValue();
                Path filePath = normalize(sourceDir).resolve(manifestEntry.file());
                if (!Files.exists(filePath)) {
                    errors.add("Missing exported file for " + collectionName + ": " + manifestEntry.file());
                    continue;
                }

                long countedRecords = countRecords(filePath, manifestEntry.format());
                recordCounts.put(collectionName, countedRecords);
                if (countedRecords != manifestEntry.recordCount()) {
                    errors.add("Record count mismatch for " + collectionName + ": expected "
                            + manifestEntry.recordCount() + " but found " + countedRecords);
                }

                String checksum = checksum(filePath);
                if (!Objects.equals(checksum, manifestEntry.checksum())) {
                    checksumMismatches.put(collectionName, checksum);
                }

                if (!collectionHandlers.containsKey(collectionName)) {
                    warnings.add("Collection is present in manifest but not supported by this runtime: " + collectionName);
                }
            }

            boolean valid = errors.isEmpty() && checksumMismatches.isEmpty();
            return new ValidationReport(valid, List.copyOf(errors), List.copyOf(warnings), recordCounts, checksumMismatches);
        } catch (IOException e) {
            return new ValidationReport(
                    false,
                    List.of("Failed to parse manifest.json: " + e.getMessage()),
                    warnings,
                    recordCounts,
                    checksumMismatches);
        }
    }

    private MigrationReport exportSelected(Path targetDir, Iterable<String> requestedCollectionNames) {
        Path exportRoot = normalize(targetDir);
        List<String> messages = new ArrayList<>();
        Map<String, CollectionReport> reports = new LinkedHashMap<>();
        Instant completedAt = Instant.now();

        try {
            Files.createDirectories(exportRoot.resolve("collections"));
            Files.createDirectories(exportRoot.resolve("tables"));

            Map<String, CollectionManifestEntry> manifestEntries = new LinkedHashMap<>();
            for (CollectionHandler<?> handler : resolveRequestedHandlers(requestedCollectionNames)) {
                CollectionReport report = exportCollection(handler, exportRoot);
                reports.put(handler.name, report);
                manifestEntries.put(handler.name, new CollectionManifestEntry(
                        report.file(),
                        report.format(),
                        report.recordCount(),
                        report.checksum(),
                        report.processedAt()));
            }

            MigrationManifest manifest = new MigrationManifest(
                    EXPORT_VERSION,
                    Instant.now(),
                    resourceModeInfo.mode(),
                    resolveVersion(),
                    manifestEntries);

            migrationObjectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(exportRoot.resolve(MANIFEST_FILE).toFile(), manifest);
            messages.add("Exported " + reports.size() + " collection(s)");
            completedAt = Instant.now();
            return new MigrationReport("export", true, exportRoot, resourceModeInfo.mode(), reports, messages, completedAt);
        } catch (Exception e) {
            log.error("Data export failed", e);
            messages.add("Export failed: " + e.getMessage());
            return new MigrationReport("export", false, exportRoot, resourceModeInfo.mode(), reports, messages, completedAt);
        }
    }

    private MigrationReport importSelected(Path sourceDir, Iterable<String> requestedCollectionNames) {
        Path importRoot = normalize(sourceDir);
        List<String> messages = new ArrayList<>();
        Map<String, CollectionReport> reports = new LinkedHashMap<>();
        Instant completedAt = Instant.now();

        try {
            ValidationReport validationReport = validate(importRoot);
            if (!validationReport.valid()) {
                messages.addAll(validationReport.errors());
                if (!validationReport.checksumMismatches().isEmpty()) {
                    messages.add("Checksum mismatches: " + validationReport.checksumMismatches().keySet());
                }
                return new MigrationReport("import", false, importRoot, resourceModeInfo.mode(), reports, messages, completedAt);
            }

            MigrationManifest manifest = migrationObjectMapper.readValue(importRoot.resolve(MANIFEST_FILE).toFile(), MigrationManifest.class);
            for (CollectionHandler<?> handler : resolveRequestedHandlers(requestedCollectionNames)) {
                CollectionManifestEntry manifestEntry = manifest.collections().get(handler.name);
                if (manifestEntry == null) {
                    throw new IllegalArgumentException("Collection '" + handler.name + "' is not present in manifest");
                }

                CollectionReport report = importCollection(handler, importRoot, manifestEntry);
                reports.put(handler.name, report);
            }

            messages.add("Imported " + reports.size() + " collection(s)");
            completedAt = Instant.now();
            return new MigrationReport("import", true, importRoot, resourceModeInfo.mode(), reports, messages, completedAt);
        } catch (Exception e) {
            log.error("Data import failed", e);
            messages.add("Import failed: " + e.getMessage());
            return new MigrationReport("import", false, importRoot, resourceModeInfo.mode(), reports, messages, completedAt);
        }
    }

    private CollectionReport exportCollection(CollectionHandler<?> handler, Path exportRoot) throws IOException {
        Path outputFile = handler.resolveOutputPath(exportRoot);
        Files.createDirectories(outputFile.getParent());
        long recordCount = handler.export(outputFile, migrationObjectMapper);
        return new CollectionReport(
                handler.name,
                exportRoot.relativize(outputFile).toString().replace('\\', '/'),
                handler.format.name().toLowerCase(),
                recordCount,
                checksum(outputFile),
                Instant.now());
    }

    private CollectionReport importCollection(
            CollectionHandler<?> handler,
            Path importRoot,
            CollectionManifestEntry manifestEntry) throws IOException {
        Path inputFile = importRoot.resolve(manifestEntry.file());
        long recordCount = handler.importRecords(inputFile, migrationObjectMapper);
        return new CollectionReport(
                handler.name,
                manifestEntry.file(),
                manifestEntry.format(),
                recordCount,
                checksum(inputFile),
                Instant.now());
    }

    private List<CollectionHandler<?>> resolveRequestedHandlers(Iterable<String> requestedCollectionNames) {
        LinkedHashSet<String> requestedNames = new LinkedHashSet<>();
        for (String requestedCollectionName : requestedCollectionNames) {
            if (requestedCollectionName != null && !requestedCollectionName.isBlank()) {
                requestedNames.add(requestedCollectionName.trim());
            }
        }

        if (requestedNames.isEmpty()) {
            requestedNames.addAll(collectionHandlers.keySet());
        }

        List<CollectionHandler<?>> handlers = new ArrayList<>();
        for (String requestedName : requestedNames) {
            CollectionHandler<?> handler = collectionHandlers.get(requestedName);
            if (handler == null) {
                throw new IllegalArgumentException("Unsupported collection '" + requestedName + "'");
            }
            handlers.add(handler);
        }
        return handlers;
    }

    private <T> void registerJsonl(
            String name,
            FileFormat format,
            Class<T> type,
            ListPagingAndSortingRepository<T, ?> pagingRepository) {
        @SuppressWarnings("unchecked")
        ListCrudRepository<T, ?> crudRepository = (ListCrudRepository<T, ?>) pagingRepository;

        collectionHandlers.put(name, new CollectionHandler<T>(
                name,
                format,
                type,
            pagingRepository,
                crudRepository,
                null));
    }

    private void registerCsv(
            String name,
            Class<TransactionType> type,
            TransactionTypeDataPort repository) {
        collectionHandlers.put(name, new CollectionHandler<>(
                name,
                FileFormat.CSV,
                type,
                repository,
                repository,
                new TransactionTypeCsvCodec()));
    }

    private String resolveVersion() {
        String implementationVersion = DataMigrationServiceImpl.class.getPackage().getImplementationVersion();
        return implementationVersion != null ? implementationVersion : "0.0.1-SNAPSHOT";
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private long countRecords(Path filePath, String format) throws IOException {
        if ("csv".equalsIgnoreCase(format)) {
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                long lines = reader.lines().filter(line -> !line.isBlank()).count();
                return Math.max(0, lines - 1);
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return reader.lines().filter(line -> !line.isBlank()).count();
        }
    }

    private String checksum(Path filePath) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    messageDigest.update(buffer, 0, read);
                }
            }
            return "sha256:" + HexFormat.of().formatHex(messageDigest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to calculate checksum for " + filePath, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @JsonIgnoreProperties({"insiderTransactions", "companyRelationships", "hibernateLazyInitializer", "handler"})
    private abstract static class InsiderMixin {
    }

    @JsonIgnoreProperties({"insiderTransactions", "insiderRelationships", "hibernateLazyInitializer", "handler"})
    private abstract static class InsiderCompanyMixin {
    }

    @JsonIgnoreProperties({"insiderTransactions", "hibernateLazyInitializer", "handler"})
    private abstract static class TransactionTypeMixin {
    }

    private static final class CollectionHandler<T> {

        private final String name;
        private final FileFormat format;
        private final Class<T> type;
        private final ListPagingAndSortingRepository<T, ?> pagingRepository;
        private final ListCrudRepository<T, ?> crudRepository;
        private final CsvCodec<T> csvCodec;

        private CollectionHandler(
                String name,
                FileFormat format,
                Class<T> type,
                ListPagingAndSortingRepository<T, ?> pagingRepository,
                ListCrudRepository<T, ?> crudRepository,
                CsvCodec<T> csvCodec) {
            this.name = name;
            this.format = format;
            this.type = type;
            this.pagingRepository = pagingRepository;
            this.crudRepository = crudRepository;
            this.csvCodec = csvCodec;
        }

        private Path resolveOutputPath(Path exportRoot) {
            String baseFolder = format == FileFormat.CSV ? "tables" : "collections";
            return exportRoot.resolve(baseFolder).resolve(name + format.extension());
        }

        private long export(Path outputFile, ObjectMapper objectMapper) throws IOException {
            if (format == FileFormat.CSV) {
                return exportCsv(outputFile);
            }
            return exportJson(outputFile, objectMapper);
        }

        private long importRecords(Path inputFile, ObjectMapper objectMapper) throws IOException {
            Path backupFile = Files.createTempFile("edgar4j-" + name + "-backup-", format.extension());
            export(backupFile, objectMapper);

            try {
                return replaceRecords(inputFile, objectMapper);
            } catch (IOException | RuntimeException importFailure) {
                try {
                    replaceRecords(backupFile, objectMapper);
                } catch (IOException | RuntimeException restoreFailure) {
                    importFailure.addSuppressed(restoreFailure);
                }
                throw importFailure;
            } finally {
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException ignored) {
                    // Best effort cleanup for the temporary backup file.
                }
            }
        }

        private long replaceRecords(Path inputFile, ObjectMapper objectMapper) throws IOException {
            crudRepository.deleteAll();
            if (format == FileFormat.CSV) {
                return importCsv(inputFile);
            }
            return importJson(inputFile, objectMapper);
        }

        private long exportJson(Path outputFile, ObjectMapper objectMapper) throws IOException {
            long totalRecords = 0;
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                int pageNumber = 0;
                Page<T> page;
                do {
                    page = pagingRepository.findAll(PageRequest.of(pageNumber, PAGE_SIZE));
                    for (T record : page.getContent()) {
                        writer.write(objectMapper.writeValueAsString(record));
                        writer.newLine();
                        totalRecords++;
                    }
                    pageNumber++;
                } while (page.hasNext());
            }
            return totalRecords;
        }

        private long importJson(Path inputFile, ObjectMapper objectMapper) throws IOException {
            List<T> batch = new ArrayList<>(PAGE_SIZE);
            long totalRecords = 0;
            try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    batch.add(objectMapper.readValue(line, type));
                    totalRecords++;
                    if (batch.size() >= PAGE_SIZE) {
                        crudRepository.saveAll(batch);
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                crudRepository.saveAll(batch);
            }
            return totalRecords;
        }

        private long exportCsv(Path outputFile) throws IOException {
            requireCsvCodec();
            List<T> records = pagingRepository.findAll(PageRequest.of(0, Integer.MAX_VALUE)).getContent();
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                writer.write(String.join(",", csvCodec.headers()));
                writer.newLine();
                for (T record : records) {
                    writer.write(csvCodec.toCsv(record));
                    writer.newLine();
                }
            }
            return records.size();
        }

        private long importCsv(Path inputFile) throws IOException {
            requireCsvCodec();

            List<T> batch = new ArrayList<>(PAGE_SIZE);
            long totalRecords = 0;
            try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
                String header = reader.readLine();
                if (header == null) {
                    return 0;
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    batch.add(csvCodec.fromCsv(line));
                    totalRecords++;
                    if (batch.size() >= PAGE_SIZE) {
                        crudRepository.saveAll(batch);
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                crudRepository.saveAll(batch);
            }
            return totalRecords;
        }

        private void requireCsvCodec() {
            if (csvCodec == null) {
                throw new IllegalStateException(
                        "CSV codec is required for collection '" + name + "' but was not registered");
            }
        }
    }

    private interface CsvCodec<T> {
        List<String> headers();
        String toCsv(T record);
        T fromCsv(String line);
    }

    private static final class TransactionTypeCsvCodec implements CsvCodec<TransactionType> {

        @Override
        public List<String> headers() {
            return List.of("transactionCode", "transactionName", "transactionDescription", "transactionCategory", "isActive", "sortOrder");
        }

        @Override
        public String toCsv(TransactionType record) {
            return String.join(",",
                    escapeCsv(record.getTransactionCode()),
                    escapeCsv(record.getTransactionName()),
                    escapeCsv(record.getTransactionDescription()),
                    escapeCsv(record.getTransactionCategory() == null ? null : record.getTransactionCategory().name()),
                    escapeCsv(record.getIsActive() == null ? null : record.getIsActive().toString()),
                    escapeCsv(record.getSortOrder() == null ? null : record.getSortOrder().toString()));
        }

        @Override
        public TransactionType fromCsv(String line) {
            List<String> values = parseCsvLine(line);
            return TransactionType.builder()
                    .transactionCode(value(values, 0))
                    .transactionName(value(values, 1))
                    .transactionDescription(value(values, 2))
                    .transactionCategory(value(values, 3) == null ? null : TransactionType.TransactionCategory.valueOf(value(values, 3)))
                    .isActive(value(values, 4) == null ? Boolean.TRUE : Boolean.valueOf(value(values, 4)))
                    .sortOrder(value(values, 5) == null ? null : Integer.valueOf(value(values, 5)))
                    .build();
        }

        private static String escapeCsv(String value) {
            if (value == null) {
                return "";
            }
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }

        private static String value(List<String> values, int index) {
            return index < values.size() && !values.get(index).isEmpty() ? values.get(index) : null;
        }

        private static List<String> parseCsvLine(String line) {
            List<String> values = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;

            for (int i = 0; i < line.length(); i++) {
                char currentChar = line.charAt(i);
                if (quoted) {
                    if (currentChar == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else if (currentChar == '"') {
                        quoted = false;
                    } else {
                        current.append(currentChar);
                    }
                } else if (currentChar == ',') {
                    values.add(current.toString());
                    current.setLength(0);
                } else if (currentChar == '"') {
                    quoted = true;
                } else {
                    current.append(currentChar);
                }
            }

            values.add(current.toString());
            return values;
        }
    }
}
