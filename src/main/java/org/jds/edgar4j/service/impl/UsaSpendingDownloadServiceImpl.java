package org.jds.edgar4j.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jds.edgar4j.properties.StorageProperties;
import org.jds.edgar4j.service.UsaSpendingDownloadService;
import org.jds.edgar4j.service.UsaSpendingDownloadService.UsaSpendingCsvPage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsaSpendingDownloadServiceImpl implements UsaSpendingDownloadService {

    private static final String AWARDS_DOWNLOAD_URL = "https://api.usaspending.gov/api/v2/bulk_download/awards/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STATUS_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_DOWNLOAD_WAIT = Duration.ofHours(2);
    private static final long MAX_STATUS_ATTEMPTS = MAX_DOWNLOAD_WAIT.dividedBy(STATUS_POLL_INTERVAL);
    private static final List<String> PRIME_AWARD_TYPES = List.of(
            "A", "B", "C", "D",
            "IDV_A", "IDV_B", "IDV_B_A", "IDV_B_B", "IDV_B_C", "IDV_D", "IDV_E",
            "02", "03", "04", "05", "06", "07", "08", "09", "10", "11"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StorageProperties storageProperties;

    @Override
    public UsaSpendingDownloadResult downloadAwardCsvZip(LocalDate dateFrom, LocalDate dateTo) {
        validateDateRange(dateFrom, dateTo);

        try {
            JsonNode createdDownload = requestAwardDownload(dateFrom, dateTo);
            JsonNode finishedDownload = waitForDownload(createdDownload);
            String fileUrl = requiredText(finishedDownload, "file_url");
            String fileName = sanitizeFileName(requiredText(finishedDownload, "file_name"));
            long totalRows = finishedDownload.path("total_rows").asLong(-1);

            Path outputPath = downloadZipToFile(fileUrl, fileName);
            log.info("Saved USAspending award CSV ZIP to {} ({} bytes, {} rows)", outputPath, Files.size(outputPath), totalRows);
            return new UsaSpendingDownloadResult(outputPath, fileUrl, totalRows);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("USAspending download was interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download USAspending award CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public UsaSpendingCsvPage readCsvPage(Path archivePath, int page, int size, long totalRowsHint) {
        if (archivePath == null || !Files.exists(archivePath)) {
            throw new IllegalArgumentException("USAspending archive was not found");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        long startRow = (long) page * size;
        long endExclusive = startRow + size;
        List<List<String>> pageRows = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry csvEntry = nextCsvEntry(zipInputStream);
            if (csvEntry == null) {
                throw new IllegalStateException("USAspending archive does not contain a CSV file");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream, java.nio.charset.StandardCharsets.UTF_8));
            List<String> headers = stripBom(readCsvRecord(reader));
            long dataRowIndex = 0;
            List<String> record;
            while ((record = readCsvRecord(reader)) != null) {
                if (dataRowIndex >= startRow && dataRowIndex < endExclusive) {
                    pageRows.add(record);
                }
                dataRowIndex++;
            }

            long totalRows = totalRowsHint >= 0 ? totalRowsHint : dataRowIndex;
            return new UsaSpendingCsvPage(csvEntry.getName(), headers, pageRows, totalRows);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read USAspending CSV archive " + archivePath, e);
        }
    }

    private JsonNode requestAwardDownload(LocalDate dateFrom, LocalDate dateTo) throws Exception {
        Map<String, Object> payload = Map.of(
                "filters", Map.of(
                        "date_type", "action_date",
                        "date_range", Map.of(
                                "start_date", dateFrom.toString(),
                                "end_date", dateTo.toString()
                        ),
                        "prime_award_types", PRIME_AWARD_TYPES
                ),
                "columns", List.of(),
                "file_format", "csv"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AWARDS_DOWNLOAD_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "edgar4j USAspending downloader")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        validateResponse(response.statusCode(), response.body(), "request USAspending award download");
        return objectMapper.readTree(response.body());
    }

    private JsonNode waitForDownload(JsonNode createdDownload) throws Exception {
        String statusUrl = createdDownload.path("status_url").asText(null);
        if (statusUrl == null || statusUrl.isBlank()) {
            return createdDownload;
        }

        for (long attempt = 0; attempt < MAX_STATUS_ATTEMPTS; attempt++) {
            JsonNode status = fetchStatus(statusUrl);
            String state = status.path("status").asText("");
            if ("finished".equalsIgnoreCase(state)) {
                return status;
            }
            if ("failed".equalsIgnoreCase(state)) {
                String message = status.path("message").asText("USAspending download generation failed");
                throw new IllegalStateException(message);
            }
            Thread.sleep(STATUS_POLL_INTERVAL.toMillis());
        }

        throw new IllegalStateException("Timed out waiting for USAspending download generation");
    }

    private JsonNode fetchStatus(String statusUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "edgar4j USAspending downloader")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        validateResponse(response.statusCode(), response.body(), "check USAspending download status");
        return objectMapper.readTree(response.body());
    }

    private Path downloadZipToFile(String fileUrl, String fileName) throws Exception {
        Path outputDirectory = Paths.get(storageProperties.getBulkDownloadsPath(), "usaspending");
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve(fileName);
        Path tempPath = Files.createTempFile(outputDirectory, fileName, ".part");

        // No request-level timeout here: BodyHandlers.ofInputStream() only times the
        // headers, and these ZIPs can be hundreds of MB. We stream straight to disk
        // (instead of readAllBytes) so a multi-GB download cannot exhaust the heap.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("Accept", "application/zip, application/octet-stream, */*")
                .header("User-Agent", "edgar4j USAspending downloader")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("USAspending file download failed with HTTP " + response.statusCode());
            }

            long bytesWritten;
            try (InputStream inputStream = response.body()) {
                bytesWritten = Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (bytesWritten == 0) {
                throw new IllegalStateException("USAspending generated an empty ZIP file");
            }

            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            return outputPath;
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private ZipEntry nextCsvEntry(ZipInputStream zipInputStream) throws IOException {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                return entry;
            }
        }
        return null;
    }

    private List<String> readCsvRecord(BufferedReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean sawAnyCharacter = false;

        int value;
        while ((value = reader.read()) != -1) {
            sawAnyCharacter = true;
            char character = (char) value;

            if (inQuotes) {
                if (character == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (character == '"') {
                inQuotes = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (character == '\r') {
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.reset();
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append(character);
            }
        }

        if (!sawAnyCharacter && field.isEmpty()) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    private List<String> stripBom(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }

        String firstHeader = headers.getFirst();
        if (!firstHeader.startsWith("\uFEFF")) {
            return headers;
        }

        List<String> cleanedHeaders = new ArrayList<>(headers);
        cleanedHeaders.set(0, firstHeader.substring(1));
        return cleanedHeaders;
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom and dateTo are required for USAspending downloads");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo must be on or after dateFrom");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (inclusiveDays > 366) {
            throw new IllegalArgumentException("USAspending award downloads may span at most one year");
        }
    }

    private void validateResponse(int statusCode, String body, String action) {
        if (statusCode >= 400) {
            throw new IllegalStateException("Unable to " + action + ": HTTP " + statusCode + " - " + body);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("USAspending response did not include " + fieldName);
        }
        return value;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
