package org.jds.edgar4j.controller;

import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.repository.Form20FRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for Form20FController REST endpoints.
 * Uses WebTestClient and embedded MongoDB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Form20FControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form20FRepository repository;

    private static final String BASE_URL = "/api/form20f";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form20F createForm20F(String accessionNumber, String cik, String symbol, LocalDate filedDate) {
        return Form20F.builder()
                .accessionNumber(accessionNumber)
                .cik(cik)
                .companyName("ACME FOREIGN LTD")
                .tradingSymbol(symbol)
                .formType("20-F")
                .filedDate(filedDate)
                .build();
    }

    @Nested
    @DisplayName("GET /api/form20f/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form20F when found by ID")
        void shouldReturnForm20FWhenFound() {
            Form20F saved = repository.save(createForm20F("0001234567-25-000001", "0001234567", "ACME", LocalDate.of(2026, 3, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0001234567-25-000001")
                    .jsonPath("$.tradingSymbol").isEqualTo("ACME");
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() {
            webTestClient.get()
                    .uri(BASE_URL + "/nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form20f/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form20F by accession number")
        void shouldReturnByAccessionNumber() {
            repository.save(createForm20F("0001234567-25-000002", "0001234567", "ACME", LocalDate.of(2026, 3, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/accession/0001234567-25-000002")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cik").isEqualTo("0001234567");
        }
    }

    @Nested
    @DisplayName("GET /api/form20f/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("should return paginated Form20F by CIK")
        void shouldReturnPaginatedByCik() {
            repository.save(createForm20F("0001234567-25-000003", "0001234567", "ACME", LocalDate.of(2026, 3, 1)));
            repository.save(createForm20F("0001234567-25-000004", "0001234567", "ACME", LocalDate.of(2027, 3, 1)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/cik/0001234567")
                            .queryParam("page", "0")
                            .queryParam("size", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(2));
        }
    }

    @Nested
    @DisplayName("GET /api/form20f/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() {
            repository.save(createForm20F("0001234567-25-000005", "0001234567", "ACME", LocalDate.of(2026, 3, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/symbol/acme")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form20f/date-range")
    class GetByDateRange {

        @Test
        @DisplayName("should return 400 for invalid date format")
        void shouldReturn400ForInvalidDateFormat() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/date-range")
                            .queryParam("startDate", "invalid-date")
                            .queryParam("endDate", "2026-03-20")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/form20f/recent")
    class GetRecent {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createForm20F("0001234567-25-000006", "0001234567", "ACME", LocalDate.of(2026, 3, 1)));
            repository.save(createForm20F("0001234567-25-000007", "0001234567", "ACME", LocalDate.of(2027, 3, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/recent?limit=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(2));
        }
    }
}

