package org.jds.edgar4j.service;

import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadDailyMasterService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Edgar4JProperties edgarProperties;

    public void downloadDailyMaster(String date) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMAT);
        if (localDate.isAfter(LocalDate.now())) {
            log.error("Select the appropriate date.");
            return;
        }

        createDailyIndexesDirectory();

        String fileDate = localDate.format(FILE_DATE_FORMAT);
        String filename = String.format("daily_idx_%s", fileDate);
        String filepath = String.format("%s/%s", edgarProperties.getDailyIndexesPath(), filename);

        List<String> links = generateLinks(localDate);

        boolean success = links.stream()
                .map(link -> Try.of(() -> downloadFile(link, filepath, edgarProperties.getUserAgent())))
                .anyMatch(Try::isSuccess);

        if (!success) {
            log.error("Daily master index is not available for this date.");
        }
    }

    private void createDailyIndexesDirectory() {
        new File(edgarProperties.getDailyIndexesPath()).mkdirs();
    }

    private List<String> generateLinks(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        String formattedDate = String.format("%04d%02d%02d", year, month, day);

        return List.of(
                String.format("https://www.sec.gov/Archives/edgar/daily-index/master.%s.idx", formattedDate),
                String.format("https://www.sec.gov/Archives/edgar/daily-index/%d/QTR%d/master.%s.idx", year, (month - 1) / 3 + 1, formattedDate)
        );
    }

    boolean downloadFile(String url, String filepath, String userAgent) throws IOException,
            InterruptedException {
        log.info("Downloading file from: {}", url);
        ProcessBuilder processBuilder = new ProcessBuilder("curl", "-s", "-A", userAgent, "-o", filepath, url);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Failed to download file from: {}", url);
            return false;
        }

        List<String> lines = Files.readAllLines(Paths.get(filepath));
        boolean isFileValid = lines.stream()
                .noneMatch(line -> line.contains("For security purposes, and to ensure that the public service remains available to users, this government computer system"));

        if (!isFileValid) {
            log.error("Invalid file content. Retrying...");
            return false;
        }

        log.info("Successfully downloaded file from: {}", url);
        return true;
    }
}
