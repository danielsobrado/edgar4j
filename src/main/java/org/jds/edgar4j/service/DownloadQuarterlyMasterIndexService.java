package org.jds.edgar4j.service;

import io.vavr.collection.List;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.MasterIndexEntry;
import org.jds.edgar4j.repository.MasterIndexEntryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true")
public class DownloadQuarterlyMasterIndexService {

    private final WebClient webClient;
    private final MasterIndexEntryRepository masterIndexEntryRepository;

    public DownloadQuarterlyMasterIndexService(WebClient webClient,
            MasterIndexEntryRepository masterIndexEntryRepository) {
        this.webClient = webClient;
        this.masterIndexEntryRepository = masterIndexEntryRepository;
    }

    public void getMasterIndex(int filingYear) {
        downloadMasterIndexes(filingYear);
    }

    private void downloadMasterIndexes(int filingYear) {
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        int currentQuarter = (currentDate.getMonthValue() - 1) / 3 + 1;

        List.rangeClosed(filingYear, currentYear)
                .forEach(year -> {
                    int maxQuarter = year == currentYear ? currentQuarter : 4;
                    List.rangeClosed(1, maxQuarter)
                            .forEach(quarter -> downloadMasterIndex(webClient, year, quarter));
                });
    }

    private void downloadMasterIndex(WebClient webClient, int year, int quarter) {
        log.info("Downloading Master Index for year {} and quarter {}", year, quarter);

        String masterUrl = String.format("https://www.sec.gov/Archives/edgar/full-index/%d/QTR%d/master.gz", year,
                quarter);
        String masterGzPath = String.format("Master Indexes/%dQTR%dmaster.gz", year, quarter);
        String masterPath = String.format("Master Indexes/%dQTR%dmaster", year, quarter);

        webClient.get()
                .uri(masterUrl)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(bytes -> {
                    try {
                        Files.createDirectories(Paths.get("Master Indexes"));
                        Files.write(Paths.get(masterGzPath), bytes);
                        return Mono.just(true);
                    } catch (IOException e) {
                        log.error("Failed to download and save Master Index file", e);
                        return Mono.just(false);
                    }
                })
                .flatMap(saved -> {
                    if (!saved) {
                        return Mono.just(false);
                    }

                    try {
                        unzip(masterGzPath, masterPath);
                        return Mono.just(true);
                    } catch (IOException e) {
                        log.error("Failed to unzip Master Index file", e);
                        return Mono.just(false);
                    }
                })
                .flatMap(unzipped -> {
                    if (!unzipped) {
                        return Mono.just(false);
                    }

                    try {
                        List<MasterIndexEntry> masterIndexEntries = parseMasterIndex(masterPath);
                        processMasterIndexEntries(masterIndexEntries, year, quarter);
                        return Mono.just(true);
                    } catch (IOException e) {
                        log.error("Failed to parse Master Index file", e);
                        return Mono.just(false);
                    }
                })
                .subscribe(success -> {
                    if (success) {
                        log.info("Master Index for year {} and quarter {} downloaded and processed successfully", year,
                                quarter);
                    } else {
                        log.error("Failed to download and process Master Index for year {} and quarter {}", year,
                                quarter);
                    }
                });
    }

    private void unzip(String gzFilePath, String outputPath) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzFilePath));
                FileOutputStream fos = new FileOutputStream(outputPath)) {
            byte[] buffer = new byte[1024];
            int len;

            while ((len = gis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private List<MasterIndexEntry> parseMasterIndex(String masterPath) throws IOException {
        List<String> lines = List.ofAll(Files.readAllLines(Paths.get(masterPath)));
        int headerEnd = lines.indexOf("--------------------------------------------------------") + 1;

        return lines.drop(headerEnd)
                .map(line -> line.split("\\|"))
                .map(parts -> new MasterIndexEntry(parts[0], parts[1], parts[2],
                        LocalDate.parse(parts[3], DateTimeFormatter.BASIC_ISO_DATE), parts[4]))
                .toList();
    }

    private void processMasterIndexEntries(List<MasterIndexEntry> entries, int year, int quarter) {
        entries.forEach(entry -> masterIndexEntryRepository.save(entry));
    }

}
