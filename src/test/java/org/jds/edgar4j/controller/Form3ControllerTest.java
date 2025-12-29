package org.jds.edgar4j.controller;

import static org.hamcrest.Matchers.hasSize;

import java.util.Calendar;
import java.util.Date;

import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.repository.Form3Repository;
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
 * Integration tests for Form3Controller REST endpoints.
 * Uses WebTestClient and embedded MongoDB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Form3ControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form3Repository repository;

    private static final String BASE_URL = "/api/form3";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form3 createForm3(String accessionNumber, String cik, String symbol, Date filedDate) {
        return Form3.builder()
                .accessionNumber(accessionNumber)
                .cik(cik)
                .issuerName("ACME CORP")
                .tradingSymbol(symbol)
                .documentType("3")
                .filedDate(filedDate)
                .build();
    }

    @Nested
    @DisplayName("GET /api/form3/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form3 when found by ID")
        void shouldReturnForm3WhenFound() {
            Form3 saved = repository.save(createForm3("0000555555-25-000001", "0000555555", "ACME", new Date()));

            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0000555555-25-000001")
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
    @DisplayName("GET /api/form3/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form3 by accession number")
        void shouldReturnByAccessionNumber() {
            repository.save(createForm3("0000555555-25-000002", "0000555555", "ACME", new Date()));

            webTestClient.get()
                    .uri(BASE_URL + "/accession/0000555555-25-000002")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cik").isEqualTo("0000555555");
        }
    }

    @Nested
    @DisplayName("GET /api/form3/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("should return paginated Form3 by CIK")
        void shouldReturnPaginatedByCik() {
            repository.save(createForm3("0000555555-25-000003", "0000555555", "ACME", new Date()));
            repository.save(createForm3("0000555555-25-000004", "0000555555", "ACME", new Date()));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/cik/0000555555")
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
    @DisplayName("GET /api/form3/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() {
            repository.save(createForm3("0000555555-25-000005", "0000555555", "ACME", new Date()));

            webTestClient.get()
                    .uri(BASE_URL + "/symbol/acme")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form3/date-range")
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
            Calendar cal = Calendar.getInstance();
            cal.set(2025, Calendar.NOVEMBER, 5);
            Date nov5 = cal.getTime();

            repository.save(createForm3("0000555555-25-000006", "0000555555", "ACME", nov5));

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
    @DisplayName("GET /api/form3/recent")
    class GetRecent {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createForm3("0000555555-25-000007", "0000555555", "ACME", new Date()));
            repository.save(createForm3("0000555555-25-000008", "0000555555", "ACME", new Date()));

            webTestClient.get()
                    .uri(BASE_URL + "/recent?limit=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(2));
        }
    }
}

