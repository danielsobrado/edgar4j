package org.jds.edgar4j.controller;

import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.repository.Form6KRepository;
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
 * Integration tests for Form6KController REST endpoints.
 * Uses WebTestClient and embedded MongoDB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Form6KControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form6KRepository repository;

    private static final String BASE_URL = "/api/form6k";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form6K createForm6K(String accessionNumber, String cik, String symbol, LocalDate filedDate) {
        return Form6K.builder()
                .accessionNumber(accessionNumber)
                .cik(cik)
                .companyName("NOVA LTD")
                .tradingSymbol(symbol)
                .formType("6-K")
                .filedDate(filedDate)
                .build();
    }

    @Nested
    @DisplayName("GET /api/form6k/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form6K when found by ID")
        void shouldReturnForm6KWhenFound() {
            Form6K saved = repository.save(createForm6K("0001111111-25-000001", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0001111111-25-000001")
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
    @DisplayName("GET /api/form6k/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form6K by accession number")
        void shouldReturnByAccessionNumber() {
            repository.save(createForm6K("0001111111-25-000002", "0001111111", "NOVA", LocalDate.of(2025, 11, 2)));

            webTestClient.get()
                    .uri(BASE_URL + "/accession/0001111111-25-000002")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cik").isEqualTo("0001111111");
        }
    }

    @Nested
    @DisplayName("GET /api/form6k/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("should return paginated Form6K by CIK")
        void shouldReturnPaginatedByCik() {
            repository.save(createForm6K("0001111111-25-000003", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));
            repository.save(createForm6K("0001111111-25-000004", "0001111111", "NOVA", LocalDate.of(2025, 11, 2)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/cik/0001111111")
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
    @DisplayName("GET /api/form6k/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() {
            repository.save(createForm6K("0001111111-25-000005", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));

            webTestClient.get()
                    .uri(BASE_URL + "/symbol/nova")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form6k/date-range")
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
    }

    @Nested
    @DisplayName("GET /api/form6k/recent")
    class GetRecent {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createForm6K("0001111111-25-000006", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));
            repository.save(createForm6K("0001111111-25-000007", "0001111111", "NOVA", LocalDate.of(2025, 11, 2)));

            webTestClient.get()
                    .uri(BASE_URL + "/recent?limit=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(2));
        }
    }
}

