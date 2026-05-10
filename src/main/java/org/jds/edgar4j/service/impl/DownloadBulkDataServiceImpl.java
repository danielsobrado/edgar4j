package org.jds.edgar4j.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.properties.StorageProperties;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadBulkDataServiceImpl implements DownloadBulkDataService {

    private final SecApiClient secApiClient;
    private final SecResponseParser responseParser;
    private final SubmissionsDataPort submissionsRepository;
    private final FillingDataPort fillingRepository;
    private final Edgar4JProperties edgar4JProperties;
    private final StorageProperties storageProperties;

    @Override
    public long downloadBulkSubmissionsArchive() {
        Path archive = downloadArchive(
                edgar4JProperties.getUrls().getBulkSubmissionsFileUrl(),
                "submissions.zip",
                secApiClient.fetchBulkSubmissionsArchive()
        );
        return importSubmissionsArchive(archive);
    }

    @Override
    public long downloadBulkCompanyFactsArchive() {
        downloadArchive(
                edgar4JProperties.getUrls().getBulkCompanyFactsFileUrl(),
                "companyfacts.zip",
                secApiClient.fetchBulkCompanyFactsArchive()
        );
        return 1;
    }

    private Path downloadArchive(String url, String fileName, byte[] bytes) {
        try {
            Path outputDirectory = Paths.get(storageProperties.getBulkDownloadsPath());
            Files.createDirectories(outputDirectory);

            Path outputPath = outputDirectory.resolve(fileName);
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Downloaded archive was empty: " + url);
            }

            Files.write(outputPath, bytes);
            log.info("Saved SEC bulk archive to {} ({} bytes)", outputPath, bytes.length);
            return outputPath;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download SEC bulk archive from " + url, e);
        }
    }

    private long importSubmissionsArchive(Path archive) {
        long importedSubmissions = 0;
        long skippedEntries = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!isSubmissionJsonEntry(entry)) {
                    skippedEntries++;
                    continue;
                }

                String json = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                importSubmissionJson(json, entry.getName());
                importedSubmissions++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import SEC bulk submissions archive " + archive, e);
        }

        log.info("Imported {} SEC submission documents from {} (skipped {} entries)",
                importedSubmissions, archive, skippedEntries);
        return importedSubmissions;
    }

    private boolean isSubmissionJsonEntry(ZipEntry entry) {
        return !entry.isDirectory() && entry.getName().toLowerCase().endsWith(".json");
    }

    private void importSubmissionJson(String json, String sourceName) {
        SecSubmissionResponse response = responseParser.parseSubmissionResponse(json);
        Submissions submissions = responseParser.toSubmissions(response);
        String cik = submissions.getCik();

        Submissions existingSubmissions = cik == null ? null : submissionsRepository.findByCik(cik).orElse(null);
        if (existingSubmissions != null) {
            submissions.setId(existingSubmissions.getId());
        }

        submissionsRepository.save(submissions);

        List<Filling> fillings = responseParser.toFillings(response);
        Map<String, Filling> uniqueFillingsByAccession = new LinkedHashMap<>();
        for (Filling filling : fillings) {
            String accessionNumber = filling.getAccessionNumber();
            if (accessionNumber == null || accessionNumber.isBlank()) {
                continue;
            }
            uniqueFillingsByAccession.putIfAbsent(accessionNumber, filling);
            Filling existingFilling = fillingRepository.findByAccessionNumber(accessionNumber).orElse(null);
            if (existingFilling != null) {
                uniqueFillingsByAccession.get(accessionNumber).setId(existingFilling.getId());
            }
        }

        fillingRepository.saveAll(List.copyOf(uniqueFillingsByAccession.values()));
        log.debug("Imported bulk submission {} for CIK {} with {} filings",
                sourceName, cik, uniqueFillingsByAccession.size());
    }
}
