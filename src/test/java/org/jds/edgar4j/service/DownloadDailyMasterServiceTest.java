package org.jds.edgar4j.service;

import org.jds.edgar4j.integration.SecRateLimiter;
import org.jds.edgar4j.properties.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DownloadDailyMasterServiceTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private SecRateLimiter secRateLimiter;

    @Mock
    private WebClient.Builder webClientBuilder;

    @TempDir
    Path tempDir;

    private DownloadDailyMasterService downloadDailyMasterService;

    private String testDailyMasterContent;

    @BeforeEach
    public void setUp() {
        testDailyMasterContent = "CIK|Company Name|Form Type|Date Filed|Filename\n" +
                "--------------------------------------------------------\n" +
                "1000045|NICHOLAS FINANCIAL INC|10-Q|2019-02-14|edgar/data/1000045/0001193125-19-039489.txt";

        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDailyIndexesPath(tempDir.toString());
        downloadDailyMasterService = spy(new DownloadDailyMasterService(storageProperties, settingsService, secRateLimiter, webClientBuilder));
    }

    @Test
    public void testDownloadDailyMaster() throws Exception {
        LocalDate inputDate = LocalDate.of(2019, 2, 14);
        String inputDateString = inputDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        String userAgent = "edgar4j-test-agent";

        when(settingsService.getUserAgent()).thenReturn(userAgent);
        doReturn(Optional.of(testDailyMasterContent)).when(downloadDailyMasterService)
                .fetchDailyMasterIndex(anyString(), eq(userAgent));

        downloadDailyMasterService.downloadDailyMaster(inputDateString);

        Path expectedFile = tempDir.resolve("daily_idx_20190214.idx");
        Assertions.assertTrue(Files.exists(expectedFile));
        Assertions.assertEquals(testDailyMasterContent, Files.readString(expectedFile));

        verify(settingsService).getUserAgent();
        verify(downloadDailyMasterService, atLeastOnce()).fetchDailyMasterIndex(anyString(), eq(userAgent));
    }
}
