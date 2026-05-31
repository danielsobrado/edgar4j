package org.jds.edgar4j.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jds.edgar4j.properties.StorageProperties;
import org.jds.edgar4j.service.UsaSpendingDownloadService.UsaSpendingCsvPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class UsaSpendingDownloadServiceImplTest {

    @TempDir
    private Path tempDir;

    @Test
    void readCsvPageSkipsHeaderOnlyCsvEntries() throws Exception {
        Path archive = tempDir.resolve("usaspending.zip");
        Files.write(archive, zip(
                entry("All_Contracts_PrimeTransactions.csv", "contract_id,recipient_name\n"),
                entry("All_Assistance_PrimeTransactions.csv", "assistance_id,recipient_name\nA1,Acme Holdings\nA2,Bravo LLC\n")
        ));

        UsaSpendingCsvPage page = service().readCsvPage(archive, 0, 25, 2);

        assertThat(page.fileName()).isEqualTo("All_Assistance_PrimeTransactions.csv");
        assertThat(page.headers()).containsExactly("assistance_id", "recipient_name");
        assertThat(page.rows()).containsExactly(
                java.util.List.of("A1", "Acme Holdings"),
                java.util.List.of("A2", "Bravo LLC")
        );
        assertThat(page.totalRows()).isEqualTo(2);
    }

    private UsaSpendingDownloadServiceImpl service() {
        return new UsaSpendingDownloadServiceImpl(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                new StorageProperties());
    }

    private static ZipEntryContent entry(String name, String content) {
        return new ZipEntryContent(name, content);
    }

    private static byte[] zip(ZipEntryContent... entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (ZipEntryContent entry : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.name()));
                zipOutputStream.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private record ZipEntryContent(String name, String content) {
    }
}
