package org.jds.edgar4j.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.properties.StorageProperties;
import org.jds.edgar4j.service.DownloadBulkDataService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadBulkDataServiceImpl implements DownloadBulkDataService {

    private final WebClient webClient;
    private final Edgar4JProperties edgar4JProperties;
    private final StorageProperties storageProperties;

    @Override
    public Path downloadBulkSubmissionsArchive() {
        return downloadArchive(
                edgar4JProperties.getUrls().getBulkSubmissionsFileUrl(),
                "submissions.zip"
        );
    }

    @Override
    public Path downloadBulkCompanyFactsArchive() {
        return downloadArchive(
                edgar4JProperties.getUrls().getBulkCompanyFactsFileUrl(),
                "companyfacts.zip"
        );
    }

    private Path downloadArchive(String url, String fileName) {
        try {
            Path outputDirectory = Paths.get(storageProperties.getBulkDownloadsPath());
            Files.createDirectories(outputDirectory);

            Path outputPath = outputDirectory.resolve(fileName);
            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                log.info("Using existing SEC bulk archive at {}", outputPath);
                return outputPath;
            }

            log.info("Downloading SEC bulk archive from {} to {}", url, outputPath);

            byte[] bytes = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

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
}
