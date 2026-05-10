package org.jds.edgar4j.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.properties.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadBulkDataServiceImplTest {

    @TempDir
    private Path tempDir;

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser responseParser;

    @Mock
    private SubmissionsDataPort submissionsRepository;

    @Mock
    private FillingDataPort fillingRepository;

    @Test
    void downloadBulkSubmissionsArchiveImportsJsonEntries() throws Exception {
        byte[] zipBytes = zip("CIK0000000001.json", "{\"cik\":\"1\"}");
        SecSubmissionResponse response = new SecSubmissionResponse();
        Submissions submissions = Submissions.builder().cik("1").name("Example Inc").build();
        Filling filling = Filling.builder().accessionNumber("0000000001-24-000001").build();

        when(secApiClient.fetchBulkSubmissionsArchive()).thenReturn(zipBytes);
        when(responseParser.parseSubmissionResponse("{\"cik\":\"1\"}")).thenReturn(response);
        when(responseParser.toSubmissions(response)).thenReturn(submissions);
        when(responseParser.toFillings(response)).thenReturn(List.of(filling));
        when(submissionsRepository.findByCik("1")).thenReturn(Optional.empty());
        when(fillingRepository.findByAccessionNumber("0000000001-24-000001")).thenReturn(Optional.empty());

        long imported = service().downloadBulkSubmissionsArchive();

        assertThat(imported).isEqualTo(1);
        verify(submissionsRepository).save(submissions);
        verify(fillingRepository).saveAll(anyList());
        assertThat(tempDir.resolve("submissions.zip")).exists();
    }

    @Test
    void downloadBulkCompanyFactsArchiveStoresArchive() throws Exception {
        byte[] zipBytes = zip("CIK0000000001.json", "{\"facts\":{}}");
        when(secApiClient.fetchBulkCompanyFactsArchive()).thenReturn(zipBytes);

        long imported = service().downloadBulkCompanyFactsArchive();

        assertThat(imported).isEqualTo(1);
        assertThat(tempDir.resolve("companyfacts.zip")).exists();
    }

    private DownloadBulkDataServiceImpl service() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setBulkDownloadsPath(tempDir.toString());
        return new DownloadBulkDataServiceImpl(
                secApiClient,
                responseParser,
                submissionsRepository,
                fillingRepository,
                new Edgar4JProperties(),
                storageProperties);
    }

    private byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(name));
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }
}
