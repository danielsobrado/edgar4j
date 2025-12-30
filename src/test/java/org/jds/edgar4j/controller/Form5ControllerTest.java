package org.jds.edgar4j.controller;

import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.repository.Form5Repository;
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
 * Integration tests for Form5Controller REST endpoints.
 * Uses WebTestClient and embedded MongoDB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Form5ControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form5Repository repository;

    private static final String BASE_URL = "/api/form5";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form5 createForm5(String accessionNumber, String cik, String symbol, LocalDate filedDate) {
        return Form5.builder()
                .accessionNumber(accessionNumber)
                .cik(cik)
                .issuerName("NOVA LTD")
                .tradingSymbol(symbol)
                .documentType("5")
                .filedDate(filedDate)
                .build();
    }

    @Nested
    @DisplayName("GET /api/form5/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form5 when found by ID")
        void shouldReturnForm5WhenFound() {
            Form5 saved = repository.save(createForm5("0000777777-25-000001", "0000777777", "NOVA", LocalDate.now()));

            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0000777777-25-000001")
                    .jsonPath("$.tradingSymbol").isEqualTo("NOVA");
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
    @DisplayName("GET /api/form5/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form5 by accession number")
        void shouldReturnByAccessionNumber() {
            repository.save(createForm5("0000777777-25-000002", "0000777777", "NOVA", LocalDate.now()));

            webTestClient.get()
                    .uri(BASE_URL + "/accession/0000777777-25-000002")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cik").isEqualTo("0000777777");
        }
    }

    @Nested
    @DisplayName("GET /api/form5/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("should return paginated Form5 by CIK")
        void shouldReturnPaginatedByCik() {
            repository.save(createForm5("0000777777-25-000003", "0000777777", "NOVA", LocalDate.now()));
            repository.save(createForm5("0000777777-25-000004", "0000777777", "NOVA", LocalDate.now()));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/cik/0000777777")
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
    @DisplayName("GET /api/form5/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() {
            repository.save(createForm5("0000777777-25-000005", "0000777777", "NOVA", LocalDate.now()));

            webTestClient.get()
                    .uri(BASE_URL + "/symbol/nova")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form5/date-range")
    class GetByDateRange {

        @Test
        @DisplayName("should return 400 for invalid date format")
        void shouldReturn400ForInvalidDateFormat() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/date-range")
                            .queryParam("startDate", "invalid-date")
                            .queryParam("endDate", "2025-11-20")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("should return filings within date range")
        void shouldReturnByDateRange() {
            LocalDate nov5 = LocalDate.of(2025, 11, 5);

            repository.save(createForm5("0000777777-25-000006", "0000777777", "NOVA", nov5));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/date-range")
                            .queryParam("startDate", "2025-11-01")
                            .queryParam("endDate", "2025-11-10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form5/recent")
    class GetRecent {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createForm5("0000777777-25-000007", "0000777777", "NOVA", LocalDate.now()));
            repository.save(createForm5("0000777777-25-000008", "0000777777", "NOVA", LocalDate.now()));

            webTestClient.get()
                    .uri(BASE_URL + "/recent?limit=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(2));
        }
    }
}
