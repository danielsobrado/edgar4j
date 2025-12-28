package org.jds.edgar4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.repository.Form13FRepository;
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
 * Integration tests for Form13FController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Form 13F Controller Tests")
class Form13FControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form13FRepository repository;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    private Form13F createTestForm13F(String accessionNumber, String cik, String filerName,
            LocalDate reportPeriod, List<Form13FHolding> holdings) {
        return Form13F.builder()
                .accessionNumber(accessionNumber)
                .cik(cik)
                .filerName(filerName)
                .reportPeriod(reportPeriod)
                .filedDate(reportPeriod.plusDays(45))
                .formType("13F-HR")
                .reportType("13F HOLDINGS REPORT")
                .holdings(holdings)
                .holdingsCount(holdings.size())
                .totalValue(holdings.stream()
                        .mapToLong(h -> h.getValue() != null ? h.getValue() : 0L)
                        .sum())
                .build();
    }

    private Form13FHolding createHolding(String issuer, String cusip, Long value, Long shares) {
        return Form13FHolding.builder()
                .nameOfIssuer(issuer)
                .titleOfClass("COM")
                .cusip(cusip)
                .value(value)
                .sharesOrPrincipalAmount(shares)
                .sharesOrPrincipalAmountType("SH")
                .investmentDiscretion("SOLE")
                .votingAuthoritySole(shares)
                .votingAuthorityShared(0L)
                .votingAuthorityNone(0L)
                .build();
    }

    @Nested
    @DisplayName("GET /api/form13f/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form 13F when found")
        void shouldReturnForm13FWhenFound() {
            Form13F saved = repository.save(createTestForm13F(
                    "0001234567-24-000001", "0001234567", "Test Manager",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));

            webTestClient.get()
                    .uri("/api/form13f/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0001234567-24-000001")
                    .jsonPath("$.filerName").isEqualTo("Test Manager");
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() {
            webTestClient.get()
                    .uri("/api/form13f/{id}", "nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form 13F by accession number")
        void shouldReturnByAccessionNumber() {
            repository.save(createTestForm13F(
                    "0001234567-24-000002", "0001234567", "Berkshire Hathaway",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 150000000L, 850000L))));

            webTestClient.get()
                    .uri("/api/form13f/accession/{accessionNumber}", "0001234567-24-000002")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.filerName").isEqualTo("Berkshire Hathaway");
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("should return paginated results by CIK")
        void shouldReturnPaginatedResultsByCik() {
            repository.save(createTestForm13F(
                    "0001234567-24-000003", "0001234567", "Manager A",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F(
                    "0001234567-24-000004", "0001234567", "Manager A",
                    LocalDate.of(2024, 6, 30),
                    List.of(createHolding("APPLE INC", "037833100", 900000L, 4500L))));

            webTestClient.get()
                    .uri("/api/form13f/cik/{cik}?page=0&size=10", "0001234567")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.totalElements").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/filer")
    class SearchByFilerName {

        @Test
        @DisplayName("should search by filer name")
        void shouldSearchByFilerName() {
            repository.save(createTestForm13F(
                    "0001234567-24-000005", "0001234567", "Berkshire Hathaway Inc",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 150000000L, 850000L))));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/form13f/filer")
                            .queryParam("name", "berkshire")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].filerName").isEqualTo("Berkshire Hathaway Inc");
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/recent")
    class GetRecentFilings {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createTestForm13F(
                    "0001234567-24-000006", "0001234567", "Manager A",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F(
                    "0009999999-24-000001", "0009999999", "Manager B",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("MICROSOFT CORP", "594918104", 800000L, 2000L))));

            webTestClient.get()
                    .uri("/api/form13f/recent?limit=5")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/accession/{accessionNumber}/holdings")
    class GetHoldings {

        @Test
        @DisplayName("should return holdings for a filing")
        void shouldReturnHoldings() {
            repository.save(createTestForm13F(
                    "0001234567-24-000007", "0001234567", "Test Manager",
                    LocalDate.of(2024, 9, 30),
                    List.of(
                            createHolding("APPLE INC", "037833100", 1000000L, 5000L),
                            createHolding("MICROSOFT CORP", "594918104", 800000L, 2000L),
                            createHolding("AMAZON COM INC", "023135106", 600000L, 3500L)
                    )));

            webTestClient.get()
                    .uri("/api/form13f/accession/{accessionNumber}/holdings", "0001234567-24-000007")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3);
        }

        @Test
        @DisplayName("should return 404 when filing not found")
        void shouldReturn404WhenFilingNotFound() {
            webTestClient.get()
                    .uri("/api/form13f/accession/{accessionNumber}/holdings", "nonexistent")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/cusip/{cusip}")
    class GetByCusip {

        @Test
        @DisplayName("should find filings containing CUSIP")
        void shouldFindFilingsContainingCusip() {
            repository.save(createTestForm13F(
                    "0001234567-24-000008", "0001234567", "Manager A",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F(
                    "0009999999-24-000002", "0009999999", "Manager B",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("MICROSOFT CORP", "594918104", 800000L, 2000L))));

            webTestClient.get()
                    .uri("/api/form13f/cusip/{cusip}", "037833100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].cik").isEqualTo("0001234567");
        }
    }

    @Nested
    @DisplayName("POST /api/form13f")
    class SaveForm13F {

        @Test
        @DisplayName("should save new Form 13F")
        void shouldSaveNewForm13F() {
            Form13F form13F = createTestForm13F(
                    "0001234567-24-000009", "0001234567", "New Manager",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("TESLA INC", "88160R101", 500000L, 2000L)));

            webTestClient.post()
                    .uri("/api/form13f")
                    .bodyValue(form13F)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.accessionNumber").isEqualTo("0001234567-24-000009");

            assertThat(repository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DELETE /api/form13f/{id}")
    class DeleteForm13F {

        @Test
        @DisplayName("should delete Form 13F")
        void shouldDeleteForm13F() {
            Form13F saved = repository.save(createTestForm13F(
                    "0001234567-24-000010", "0001234567", "To Delete",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));

            webTestClient.delete()
                    .uri("/api/form13f/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(repository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent")
        void shouldReturn404WhenDeletingNonExistent() {
            webTestClient.delete()
                    .uri("/api/form13f/{id}", "nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form13f/cik/{cik}/history")
    class GetPortfolioHistory {

        @Test
        @DisplayName("should return portfolio history for a filer")
        void shouldReturnPortfolioHistory() {
            repository.save(createTestForm13F(
                    "0001234567-24-000011", "0001234567", "Manager A",
                    LocalDate.of(2024, 3, 31),
                    List.of(createHolding("APPLE INC", "037833100", 900000L, 4500L))));
            repository.save(createTestForm13F(
                    "0001234567-24-000012", "0001234567", "Manager A",
                    LocalDate.of(2024, 6, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F(
                    "0001234567-24-000013", "0001234567", "Manager A",
                    LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1100000L, 5500L))));

            webTestClient.get()
                    .uri("/api/form13f/cik/{cik}/history", "0001234567")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3);
        }
    }
}
