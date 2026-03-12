package org.jds.edgar4j.xbrl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.jds.edgar4j.xbrl.analysis.MultiPeriodAnalyzer;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.parser.StreamingXbrlParser;
import org.jds.edgar4j.xbrl.parser.XbrlPackageHandler;
import org.jds.edgar4j.xbrl.parser.XbrlParser;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.jds.edgar4j.xbrl.statement.StatementReconstructor;
import org.jds.edgar4j.xbrl.taxonomy.TaxonomyResolver;
import org.jds.edgar4j.xbrl.validation.CalculationValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XbrlServiceTest {

    @Mock
    private XbrlParser xbrlParser;

    @Mock
    private XbrlPackageHandler packageHandler;

    @Mock
    private CalculationValidator calculationValidator;

    @Mock
    private TaxonomyResolver taxonomyResolver;

    @Mock
    private StatementReconstructor statementReconstructor;

    @Mock
    private ConceptStandardizer conceptStandardizer;

    @Mock
    private MultiPeriodAnalyzer multiPeriodAnalyzer;

    @Mock
    private SecFilingExtractor secFilingExtractor;

    @Mock
    private StreamingXbrlParser streamingParser;

    @Mock
    private DownloadedResourceStore downloadedResourceStore;

    private HttpServer server;
    private XbrlService xbrlService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sample-xbrl.xml", this::handleSampleXbrlRequest);
        server.start();

        xbrlService = new XbrlService(
                xbrlParser,
                packageHandler,
                calculationValidator,
                taxonomyResolver,
                statementReconstructor,
                conceptStandardizer,
                multiPeriodAnalyzer,
                secFilingExtractor,
                streamingParser,
                WebClient.builder(),
                downloadedResourceStore
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("parseFromUrl fetches and caches content when persistent cache is empty")
    void parseFromUrlFetchesAndCachesOnPersistentCacheMiss() {
        String url = "http://localhost:" + server.getAddress().getPort() + "/sample-xbrl.xml";
        byte[] responseBody = "<xbrl><context id=\"ctx\"/></xbrl>".getBytes(StandardCharsets.UTF_8);
        XbrlInstance expected = XbrlInstance.builder()
                .documentUri(url)
                .parseTime(LocalDateTime.now())
                .build();

        when(downloadedResourceStore.readBytes("xbrl", url)).thenReturn(Optional.empty());
        when(downloadedResourceStore.writeBytes(eq("xbrl"), eq(url), any(byte[].class)))
                .thenReturn(Path.of("build", "cache", "sample-xbrl.xml"));
        when(xbrlParser.parse(any(byte[].class), eq(url), isNull())).thenReturn(expected);

        XbrlInstance actual = xbrlService.parseFromUrl(url).block(Duration.ofSeconds(5));

        assertNotNull(actual);
        assertSame(expected, actual);

        ArgumentCaptor<byte[]> cacheWriteCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> parserInputCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloadedResourceStore).writeBytes(eq("xbrl"), eq(url), cacheWriteCaptor.capture());
        verify(xbrlParser).parse(parserInputCaptor.capture(), eq(url), isNull());
        assertEquals(new String(responseBody, StandardCharsets.UTF_8),
                new String(cacheWriteCaptor.getValue(), StandardCharsets.UTF_8));
        assertEquals(new String(responseBody, StandardCharsets.UTF_8),
                new String(parserInputCaptor.getValue(), StandardCharsets.UTF_8));
    }

    private void handleSampleXbrlRequest(HttpExchange exchange) throws IOException {
        byte[] responseBody = "<xbrl><context id=\"ctx\"/></xbrl>".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}
