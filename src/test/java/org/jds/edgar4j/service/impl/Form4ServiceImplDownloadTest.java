package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

import org.jds.edgar4j.integration.Form4Parser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Form4ServiceImplDownloadTest {

    @Mock
    private Form4DataPort form4Repository;

    @Mock
    private Form4Parser form4Parser;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private TickerDataPort tickerRepository;

    @Mock
    private FillingDataPort fillingRepository;

    @Mock
    private DownloadedResourceStore downloadedResourceStore;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<java.io.InputStream> httpResponse;

    private Form4ServiceImpl form4Service;

    @BeforeEach
    void setUp() {
        form4Service = new Form4ServiceImpl(
                form4Repository,
                form4Parser,
                settingsService,
                secApiClient,
                tickerRepository,
                fillingRepository,
                downloadedResourceStore,
                httpClient);
        ReflectionTestUtils.setField(form4Service, "edgarDataArchivesUrl", "https://www.sec.gov/Archives/edgar/data");
    }

    @Test
    @DisplayName("downloadForm4 should decode gzip-compressed SEC responses")
    void downloadForm4ShouldDecodeGzipResponses() throws Exception {
        String xml = "<ownershipDocument><documentType>4</documentType></ownershipDocument>";

        when(settingsService.getUserAgent()).thenReturn("My Company sec-ops@mycompany.com");
        when(downloadedResourceStore.readText(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(downloadedResourceStore.writeText(anyString(), anyString(), anyString(), any())).thenReturn(Path.of("cache.txt"));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(
                Map.of("Content-Encoding", List.of("gzip")),
                (name, value) -> true));
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(gzip(xml)));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));

        String result = form4Service.downloadForm4("0000320193", "0000320193-26-000001", "doc4.xml").get();

        assertEquals(xml, result);
    }

    private byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }
}
