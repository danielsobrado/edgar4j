package org.jds.edgar4j.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.integration.SecRateLimiter;
import org.jds.edgar4j.properties.StorageProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadDailyMasterService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String SEC_BLOCK_MESSAGE =
            "For security purposes, and to ensure that the public service remains available to users, this government computer system";

    private final StorageProperties storageProperties;
    private final SettingsService settingsService;
    private final SecRateLimiter secRateLimiter;
    private final WebClient.Builder webClientBuilder;

    public void downloadDailyMaster(String date) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMAT);
        if (localDate.isAfter(LocalDate.now())) {
            log.error("Select the appropriate date.");
            return;
        }

        String fileDate = localDate.format(FILE_DATE_FORMAT);
        String filename = String.format("daily_idx_%s.idx", fileDate);
        Path outputPath = resolveOutputPath(filename);

        List<String> links = generateLinks(localDate);

        String userAgent = settingsService.getUserAgent();
        boolean downloaded = links.stream()
                .map(url -> fetchDailyMasterIndex(url, userAgent))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .map(content -> writeFile(outputPath, content))
                .orElse(false);

        if (!downloaded) {
            log.error("Daily master index is not available for this date.");
        }
    }

    Optional<String> fetchDailyMasterIndex(String url, String userAgent) {
        try {
            secRateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        WebClient client = webClientBuilder.build();
        return client.get()
                .uri(url)
                .accept(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.bodyToMono(String.class)
                        : Mono.empty())
                .timeout(java.time.Duration.ofSeconds(30))
                .onErrorResume(e -> Mono.empty())
                .map(String::trim)
                .filter(body -> !body.isBlank())
                .filter(body -> !body.contains(SEC_BLOCK_MESSAGE))
                .blockOptional();
    }

    private Path resolveOutputPath(String filename) {
        Path directory = Paths.get(storageProperties.getDailyIndexesPath());
        return directory.resolve(filename).normalize();
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

    private boolean writeFile(Path outputPath, String content) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content);
            log.info("Saved daily master index to {}", outputPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to write daily master index to {}", outputPath, e);
            return false;
        }
    }
}
