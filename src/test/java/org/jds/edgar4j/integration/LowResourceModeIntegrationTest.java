package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.config.ResourceModeInfo;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.port.Form5DataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test-low", "resource-low"})
class LowResourceModeIntegrationTest {

    private static final Path DATA_PATH = createDataPath();
    private static final String H2_URL = "jdbc:h2:file:" + DATA_PATH.resolve("batch")
            .resolve("edgar4j")
            .toAbsolutePath()
            .toString()
            .replace("\\", "/") + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";

    @LocalServerPort
    private int port;

    @Autowired
    private ResourceModeInfo resourceModeInfo;

    @Autowired
    private AppSettingsDataPort appSettingsDataPort;

    @Autowired
    private CompanyTickerDataPort companyTickerDataPort;

    @Autowired
    private Form5DataPort form5DataPort;

    @Autowired
    private FillingDataPort fillingDataPort;

    @Autowired
    private Form4DataPort form4DataPort;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerLowModeProperties(DynamicPropertyRegistry registry) {
        registry.add("edgar4j.storage.file.base-path", () -> DATA_PATH.toAbsolutePath().toString());
        registry.add("spring.datasource.url", () -> H2_URL);
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        appSettingsDataPort.deleteAll();
        companyTickerDataPort.deleteAll();
        fillingDataPort.deleteAll();
        form4DataPort.deleteAll();
        form5DataPort.deleteAll();
    }

    @Test
    @DisplayName("test-low activates the low resource mode profile")
    void activatesLowResourceMode() {
        assertThat(resourceModeInfo.mode()).isEqualTo("low");
        assertThat(resourceModeInfo.description()).contains("File-backed");

        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.resourceMode.status").isEqualTo("UP")
                .jsonPath("$.components.resourceMode.details.resourceMode").isEqualTo("low")
                .jsonPath("$.components.resourceMode.details.storage").isEqualTo("file")
                .jsonPath("$.components.resourceMode.details.dataPath").isEqualTo(DATA_PATH.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("settings endpoints persist to file storage and report Mongo disabled")
    void settingsEndpointsPersistToFileStorage() throws IOException {
        webTestClient.put()
                .uri("/api/settings")
                .bodyValue(Map.ofEntries(
                        Map.entry("userAgent", "Edgar4j/1.0 (sec-ops@mycompany.com)"),
                        Map.entry("autoRefresh", true),
                        Map.entry("refreshInterval", 30),
                        Map.entry("darkMode", false),
                        Map.entry("emailNotifications", false),
                        Map.entry("smtpPort", 587),
                        Map.entry("smtpStartTlsEnabled", true),
                        Map.entry("marketDataProvider", "NONE"),
                        Map.entry("realtimeSyncEnabled", true),
                        Map.entry("realtimeSyncForms", "4"),
                        Map.entry("realtimeSyncLookbackHours", 1),
                        Map.entry("realtimeSyncMaxPages", 10),
                        Map.entry("realtimeSyncPageSize", 100)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.userAgent").isEqualTo("Edgar4j/1.0 (sec-ops@mycompany.com)")
                .jsonPath("$.data.marketDataProvider").isEqualTo("NONE");

        webTestClient.get()
                .uri("/api/settings/health/mongodb")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.connected").isEqualTo(false)
                .jsonPath("$.data.message").isEqualTo("MongoDB is disabled in low resource mode");

        Path settingsFile = DATA_PATH.resolve("collections").resolve("app_settings.json");
        assertThat(settingsFile).exists();
        assertThat(Files.readString(settingsFile)).contains("sec-ops@mycompany.com");
    }

    @Test
    @DisplayName("company endpoints read from the file-backed company ticker store")
    void companyEndpointsUseFileBackedStorage() throws IOException {
        companyTickerDataPort.save(CompanyTicker.builder()
                .cikStr(320193L)
                .ticker("AAPL")
                .title("Apple Inc.")
                .build());

        webTestClient.get()
                .uri("/api/companies/ticker/AAPL/cik")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEqualTo("0000320193");

        webTestClient.get()
                .uri("/api/companies/cik/0000320193/ticker")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEqualTo("AAPL");

        Path companyTickerFile = DATA_PATH.resolve("collections").resolve("company_tickers.json");
        assertThat(companyTickerFile).exists();
        assertThat(Files.readString(companyTickerFile)).contains("Apple Inc.");
    }

    @Test
    @DisplayName("form5 endpoints persist and return file-backed filings")
    void form5EndpointsUseFileBackedStorage() throws IOException {
        webTestClient.post()
                .uri("/api/form5")
                .bodyValue(Map.ofEntries(
                        Map.entry("accessionNumber", "0000777777-26-000001"),
                        Map.entry("cik", "0000777777"),
                        Map.entry("issuerName", "NOVA LTD"),
                        Map.entry("tradingSymbol", "NOVA"),
                        Map.entry("documentType", "5"),
                        Map.entry("filedDate", "2026-03-14"),
                        Map.entry("rptOwnerCik", "0001111111"),
                        Map.entry("rptOwnerName", "Jane Doe"),
                        Map.entry("officerTitle", "CEO"),
                        Map.entry("isDirector", true),
                        Map.entry("isOfficer", true),
                        Map.entry("isTenPercentOwner", false),
                        Map.entry("isOther", false),
                        Map.entry("ownerType", "Officer")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessionNumber").isEqualTo("0000777777-26-000001")
                .jsonPath("$.tradingSymbol").isEqualTo("NOVA");

        webTestClient.get()
                .uri("/api/form5/recent?limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].accessionNumber").isEqualTo("0000777777-26-000001");

        Path form5File = DATA_PATH.resolve("collections").resolve("form5.jsonl");
        assertThat(form5File).exists();
        assertThat(Files.readString(form5File)).contains("0000777777-26-000001");
    }

    @Test
    @DisplayName("filing endpoints serve file-backed filing data")
    void filingEndpointsUseFileBackedStorage() throws IOException {
        Filling filing = TestFixtures.createTestFilling(
                "0001234567-26-000101",
                "0001234567",
                "8-K",
                LocalDate.of(2026, 3, 10));
        filing.setCompany("Acme Corp");
        fillingDataPort.save(filing);

        webTestClient.get()
                .uri("/api/filings/accession/0001234567-26-000101")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.accessionNumber").isEqualTo("0001234567-26-000101")
                .jsonPath("$.data.cik").isEqualTo("0001234567")
                .jsonPath("$.data.formType").isEqualTo("8-K");

        webTestClient.get()
                .uri("/api/filings/recent?limit=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].accessionNumber").isEqualTo("0001234567-26-000101");

        Path filingsFile = DATA_PATH.resolve("collections").resolve("fillings.jsonl");
        assertThat(filingsFile).exists();
        assertThat(Files.readString(filingsFile)).contains("0001234567-26-000101");
    }

    @Test
    @DisplayName("form4 endpoints read from file-backed insider filings")
    void form4EndpointsUseFileBackedStorage() throws IOException {
        form4DataPort.save(TestFixtures.createTestForm4(
                "0000666666-26-000001",
                "NOVA",
                LocalDate.of(2026, 3, 12)));

        webTestClient.get()
                .uri("/api/form4/accession/0000666666-26-000001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessionNumber").isEqualTo("0000666666-26-000001")
                .jsonPath("$.tradingSymbol").isEqualTo("NOVA");

        webTestClient.get()
                .uri("/api/form4/symbol/NOVA?page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].accessionNumber").isEqualTo("0000666666-26-000001")
                .jsonPath("$.content[0].tradingSymbol").isEqualTo("NOVA");

        Path form4File = DATA_PATH.resolve("collections").resolve("form4.jsonl");
        assertThat(form4File).exists();
        assertThat(Files.readString(form4File)).contains("0000666666-26-000001");
    }

    private static Path createDataPath() {
        try {
            Path path = Path.of("target", "test-low-mode", UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create low-mode test data path", e);
        }
    }
}
