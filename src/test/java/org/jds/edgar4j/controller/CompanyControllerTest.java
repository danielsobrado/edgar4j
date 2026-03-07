package org.jds.edgar4j.controller;

import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end integration tests for {@link CompanyController} – company_tickers endpoints.
 *
 * <p>Uses {@code WebTestClient} against the full Spring context with embedded MongoDB.
 * There are <em>no mocks</em> anywhere in the request → controller → service →
 * repository → Mongo → back chain.
 *
 * <p>Covered endpoints:
 * <ul>
 *   <li>GET /api/companies/ticker/{ticker}/cik  — getCikByTicker</li>
 *   <li>GET /api/companies/cik/{cik}/ticker     — getTickerByCik</li>
 *   <li>GET /api/companies/ticker/{ticker}/info — raw CompanyTicker document</li>
 *   <li>GET /api/companies/cik/{cik}/info       — raw CompanyTicker document</li>
 *   <li>GET /api/companies/ticker/{ticker}      — full company (company_tickers fallback)</li>
 *   <li>GET /api/companies/cik/{cik}            — full company (company_tickers enrichment)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompanyControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @Autowired
    CompanyTickerRepository companyTickerRepository;

    @Autowired
    SubmissionsRepository submissionsRepository;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static final long   AAPL_CIK_NUM = 320193L;
    private static final String AAPL_CIK_PAD = "0000320193";
    private static final String AAPL_TICKER  = "AAPL";
    private static final String AAPL_TITLE   = "Apple Inc.";

    private static final long   MSFT_CIK_NUM = 789019L;
    private static final String MSFT_CIK_PAD = "0000789019";
    private static final String MSFT_TICKER  = "MSFT";

    private static final String BASE = "/api/companies";

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        companyTickerRepository.deleteAll();
        submissionsRepository.deleteAll();

        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(AAPL_CIK_NUM).ticker(AAPL_TICKER).title(AAPL_TITLE).build());
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(MSFT_CIK_NUM).ticker(MSFT_TICKER).title("Microsoft Corp").build());
    }

    @AfterEach
    void tearDown() {
        companyTickerRepository.deleteAll();
        submissionsRepository.deleteAll();
    }

    // ── GET /ticker/{ticker}/cik ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /ticker/{ticker}/cik — returns zero-padded CIK for known ticker")
    void getCikByTicker_knownTicker() {
        webClient.get().uri(BASE + "/ticker/{t}/cik", AAPL_TICKER)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEqualTo(AAPL_CIK_PAD);
    }

    @Test
    @Order(2)
    @DisplayName("GET /ticker/{ticker}/cik — case-insensitive lookup (lowercase input)")
    void getCikByTicker_caseInsensitive() {
        webClient.get().uri(BASE + "/ticker/{t}/cik", "aapl")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo(AAPL_CIK_PAD);
    }

    @Test
    @Order(3)
    @DisplayName("GET /ticker/{ticker}/cik — 404 for unknown ticker")
    void getCikByTicker_unknownTicker() {
        webClient.get().uri(BASE + "/ticker/{t}/cik", "XXXXXX")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /cik/{cik}/ticker ─────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /cik/{cik}/ticker — returns ticker for zero-padded CIK")
    void getTickerByCik_paddedCik() {
        webClient.get().uri(BASE + "/cik/{cik}/ticker", AAPL_CIK_PAD)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEqualTo(AAPL_TICKER);
    }

    @Test
    @Order(5)
    @DisplayName("GET /cik/{cik}/ticker — works with raw (non-padded) numeric CIK")
    void getTickerByCik_rawNumericCik() {
        webClient.get().uri(BASE + "/cik/{cik}/ticker", String.valueOf(AAPL_CIK_NUM))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo(AAPL_TICKER);
    }

    @Test
    @Order(6)
    @DisplayName("GET /cik/{cik}/ticker — 404 for unknown CIK")
    void getTickerByCik_unknownCik() {
        webClient.get().uri(BASE + "/cik/{cik}/ticker", "0009999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /ticker/{ticker}/info ─────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("GET /ticker/{ticker}/info — returns raw CompanyTicker document")
    void getTickerInfo_byTicker() {
        webClient.get().uri(BASE + "/ticker/{t}/info", AAPL_TICKER)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticker").isEqualTo(AAPL_TICKER)
                .jsonPath("$.data.cikStr").isEqualTo((int) AAPL_CIK_NUM)  // JSON numbers are int by default
                .jsonPath("$.data.title").isEqualTo(AAPL_TITLE);
    }

    @Test
    @Order(8)
    @DisplayName("GET /ticker/{ticker}/info — 404 for unknown ticker")
    void getTickerInfo_unknownTicker() {
        webClient.get().uri(BASE + "/ticker/{t}/info", "XXXXXX")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /cik/{cik}/info ───────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("GET /cik/{cik}/info — returns raw CompanyTicker document")
    void getCikInfo_byCik() {
        webClient.get().uri(BASE + "/cik/{cik}/info", AAPL_CIK_PAD)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticker").isEqualTo(AAPL_TICKER)
                .jsonPath("$.data.title").isEqualTo(AAPL_TITLE);
    }

    @Test
    @Order(10)
    @DisplayName("GET /cik/{cik}/info — 404 for unknown CIK")
    void getCikInfo_unknownCik() {
        webClient.get().uri(BASE + "/cik/{cik}/info", "0009999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /ticker/{ticker} — full company (no submissions) ─────────────────

    @Test
    @Order(11)
    @DisplayName("GET /ticker/{ticker} — returns minimal response from company_tickers when no submission exists")
    void getCompanyByTicker_fallbackToCompanyTickers() {
        // No Submissions document saved — service must fall back to company_tickers data
        webClient.get().uri(BASE + "/ticker/{t}", AAPL_TICKER)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticker").isEqualTo(AAPL_TICKER)
                .jsonPath("$.data.cik").isEqualTo(AAPL_CIK_PAD)
                .jsonPath("$.data.name").isEqualTo(AAPL_TITLE);
    }

    @Test
    @Order(12)
    @DisplayName("GET /ticker/{ticker} — 404 for completely unknown ticker")
    void getCompanyByTicker_unknownTicker() {
        webClient.get().uri(BASE + "/ticker/{t}", "XXXXXX")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /cik/{cik} — full company (no submissions) ───────────────────────

    @Test
    @Order(13)
    @DisplayName("GET /cik/{cik} — returns minimal response from company_tickers when no submission exists")
    void getCompanyByCik_fallbackToCompanyTickers() {
        webClient.get().uri(BASE + "/cik/{cik}", AAPL_CIK_PAD)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.cik").isEqualTo(AAPL_CIK_PAD)
                .jsonPath("$.data.ticker").isEqualTo(AAPL_TICKER)
                .jsonPath("$.data.name").isEqualTo(AAPL_TITLE);
    }

    @Test
    @Order(14)
    @DisplayName("GET /cik/{cik} — also accepts raw (non-padded) numeric CIK")
    void getCompanyByCik_rawNumericCik() {
        webClient.get().uri(BASE + "/cik/{cik}", String.valueOf(AAPL_CIK_NUM))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticker").isEqualTo(AAPL_TICKER);
    }

    @Test
    @Order(15)
    @DisplayName("GET /cik/{cik} — 404 for completely unknown CIK")
    void getCompanyByCik_unknownCik() {
        webClient.get().uri(BASE + "/cik/{cik}", "0009999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── MSFT round-trip ───────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("Round-trip: ticker→CIK and CIK→ticker are consistent for MSFT")
    void roundTrip_msft() {
        // ticker → CIK
        webClient.get().uri(BASE + "/ticker/{t}/cik", MSFT_TICKER)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo(MSFT_CIK_PAD);

        // padded CIK → ticker
        webClient.get().uri(BASE + "/cik/{cik}/ticker", MSFT_CIK_PAD)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo(MSFT_TICKER);

        // raw numeric CIK → ticker
        webClient.get().uri(BASE + "/cik/{cik}/ticker", String.valueOf(MSFT_CIK_NUM))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo(MSFT_TICKER);
    }
}
