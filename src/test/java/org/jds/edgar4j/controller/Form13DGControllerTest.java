package org.jds.edgar4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.repository.Form13DGRepository;
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
 * Integration tests for Form13DGController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Form 13D/G Controller Tests")
class Form13DGControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form13DGRepository repository;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        repository.deleteAll();
    }

    private Form13DG createTest13D(String accessionNumber, String issuerCik, String issuerName,
                                    String filingPersonCik, String filingPersonName,
                                    String cusip, Double percentOfClass, LocalDate eventDate) {
        return Form13DG.builder()
                .accessionNumber(accessionNumber)
                .formType("SCHEDULE 13D")
                .scheduleType("13D")
                .issuerCik(issuerCik)
                .issuerName(issuerName)
                .filingPersonCik(filingPersonCik)
                .filingPersonName(filingPersonName)
                .cusip(cusip)
                .securityTitle("Common Stock")
                .percentOfClass(percentOfClass)
                .sharesBeneficiallyOwned(1000000L)
                .votingPowerSole(1000000L)
                .votingPowerShared(0L)
                .dispositivePowerSole(1000000L)
                .dispositivePowerShared(0L)
                .eventDate(eventDate)
                .filedDate(eventDate.plusDays(5))
                .amendmentType("INITIAL")
                .filingPersonAddress(Form13DG.Address.builder()
                        .street1("123 Main St")
                        .city("New York")
                        .stateOrCountry("NY")
                        .zipCode("10001")
                        .build())
                .citizenshipOrOrganization("Delaware")
                .reportingPersonTypes(List.of("PN"))
                .purposeOfTransaction("To influence management and board composition")
                .build();
    }

    private Form13DG createTest13G(String accessionNumber, String issuerCik, String issuerName,
                                    String filingPersonCik, String filingPersonName,
                                    String cusip, Double percentOfClass, LocalDate eventDate) {
        return Form13DG.builder()
                .accessionNumber(accessionNumber)
                .formType("SCHEDULE 13G")
                .scheduleType("13G")
                .issuerCik(issuerCik)
                .issuerName(issuerName)
                .filingPersonCik(filingPersonCik)
                .filingPersonName(filingPersonName)
                .cusip(cusip)
                .securityTitle("Common Stock")
                .percentOfClass(percentOfClass)
                .sharesBeneficiallyOwned(5000000L)
                .votingPowerSole(0L)
                .votingPowerShared(0L)
                .dispositivePowerSole(5000000L)
                .dispositivePowerShared(0L)
                .eventDate(eventDate)
                .filedDate(eventDate.plusDays(45))
                .amendmentType("INITIAL")
                .filingPersonAddress(Form13DG.Address.builder()
                        .street1("100 Vanguard Blvd")
                        .city("Malvern")
                        .stateOrCountry("PA")
                        .zipCode("19355")
                        .build())
                .citizenshipOrOrganization("Pennsylvania")
                .reportingPersonTypes(List.of("IA", "IC"))
                .filerCategory("QII")
                .build();
    }

    @Nested
    @DisplayName("GET /api/form13dg/{id}")
    class GetById {

        @Test
        @DisplayName("should return Form 13D/G when found")
        void shouldReturnForm13DGWhenFound() {
            Form13DG saved = repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            webTestClient.get()
                    .uri("/api/form13dg/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo("0001234567-24-000001")
                    .jsonPath("$.filingPersonName").isEqualTo("Activist Capital")
                    .jsonPath("$.scheduleType").isEqualTo("13D");
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() {
            webTestClient.get()
                    .uri("/api/form13dg/{id}", "nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("should return Form 13D/G when found")
        void shouldReturnForm13DGWhenFound() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            webTestClient.get()
                    .uri("/api/form13dg/accession/{accessionNumber}", "0001234567-24-000001")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.issuerName").isEqualTo("ACME Corp");
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/schedule/{scheduleType}")
    class GetByScheduleType {

        @Test
        @DisplayName("should return 13D filings")
        void shouldReturn13DFilings() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13G(
                    "0002345678-25-000001", "0008765432", "Tech Inc",
                    "0002345678", "Vanguard Group",
                    "880088009", 9.8, LocalDate.of(2024, 12, 31)));

            webTestClient.get()
                    .uri("/api/form13dg/schedule/{scheduleType}", "13D")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].scheduleType").isEqualTo("13D");
        }

        @Test
        @DisplayName("should return 13G filings")
        void shouldReturn13GFilings() {
            repository.save(createTest13G(
                    "0002345678-25-000001", "0008765432", "Tech Inc",
                    "0002345678", "Vanguard Group",
                    "880088009", 9.8, LocalDate.of(2024, 12, 31)));

            webTestClient.get()
                    .uri("/api/form13dg/schedule/{scheduleType}", "13G")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].scheduleType").isEqualTo("13G");
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/issuer/cik/{issuerCik}")
    class GetByIssuerCik {

        @Test
        @DisplayName("should return filings for issuer")
        void shouldReturnFilingsForIssuer() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13G(
                    "0002345678-25-000001", "0009876543", "ACME Corp",
                    "0002345678", "Vanguard Group",
                    "001234567", 9.8, LocalDate.of(2024, 12, 31)));

            webTestClient.get()
                    .uri("/api/form13dg/issuer/cik/{issuerCik}", "0009876543")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/cusip/{cusip}")
    class GetByCusip {

        @Test
        @DisplayName("should return filings for CUSIP")
        void shouldReturnFilingsForCusip() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            webTestClient.get()
                    .uri("/api/form13dg/cusip/{cusip}", "001234567")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].cusip").isEqualTo("001234567");
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/filer/cik/{filingPersonCik}")
    class GetByFilingPersonCik {

        @Test
        @DisplayName("should return filings by beneficial owner")
        void shouldReturnFilingsByBeneficialOwner() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13D(
                    "0001234567-24-000002", "0008765432", "Tech Inc",
                    "0001234567", "Activist Capital",
                    "880088009", 6.2, LocalDate.of(2024, 12, 20)));

            webTestClient.get()
                    .uri("/api/form13dg/filer/cik/{filingPersonCik}", "0001234567")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/recent")
    class GetRecentFilings {

        @Test
        @DisplayName("should return recent filings")
        void shouldReturnRecentFilings() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13G(
                    "0002345678-25-000001", "0008765432", "Tech Inc",
                    "0002345678", "Vanguard Group",
                    "880088009", 9.8, LocalDate.of(2024, 12, 31)));

            webTestClient.get()
                    .uri("/api/form13dg/recent?limit=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/ownership/ten-percent")
    class GetTenPercentOwners {

        @Test
        @DisplayName("should return only 10%+ owners")
        void shouldReturnTenPercentOwners() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Small Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13D(
                    "0002345678-24-000001", "0008765432", "Tech Inc",
                    "0002345678", "Big Activist",
                    "880088009", 15.0, LocalDate.of(2024, 12, 20)));

            webTestClient.get()
                    .uri("/api/form13dg/ownership/ten-percent")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].filingPersonName").isEqualTo("Big Activist");
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/cusip/{cusip}/ownership")
    class GetBeneficialOwnershipSnapshot {

        @Test
        @DisplayName("should return ownership snapshot for CUSIP")
        void shouldReturnOwnershipSnapshot() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13G(
                    "0002345678-24-000001", "0009876543", "ACME Corp",
                    "0002345678", "Vanguard Group",
                    "001234567", 9.8, LocalDate.of(2024, 12, 20)));

            webTestClient.get()
                    .uri("/api/form13dg/cusip/{cusip}/ownership", "001234567")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cusip").isEqualTo("001234567")
                    .jsonPath("$.beneficialOwners.length()").isEqualTo(2)
                    .jsonPath("$.activistCount").isEqualTo(1)
                    .jsonPath("$.passiveCount").isEqualTo(1);
        }

        @Test
        @DisplayName("should return 404 when CUSIP not found")
        void shouldReturn404WhenCusipNotFound() {
            webTestClient.get()
                    .uri("/api/form13dg/cusip/{cusip}/ownership", "NOTFOUND")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("POST /api/form13dg")
    class SaveForm13DG {

        @Test
        @DisplayName("should save Form 13D/G")
        void shouldSaveForm13DG() {
            Form13DG form = createTest13D(
                    "0001234567-24-000099", "0009876543", "New Corp",
                    "0001234567", "New Activist",
                    "999888777", 5.5, LocalDate.of(2024, 12, 25));

            webTestClient.post()
                    .uri("/api/form13dg")
                    .bodyValue(form)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").exists()
                    .jsonPath("$.accessionNumber").isEqualTo("0001234567-24-000099");

            assertThat(repository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DELETE /api/form13dg/{id}")
    class DeleteForm13DG {

        @Test
        @DisplayName("should delete Form 13D/G")
        void shouldDeleteForm13DG() {
            Form13DG saved = repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            webTestClient.delete()
                    .uri("/api/form13dg/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(repository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent")
        void shouldReturn404WhenDeletingNonExistent() {
            webTestClient.delete()
                    .uri("/api/form13dg/{id}", "nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/event-date-range")
    class GetByEventDateRange {

        @Test
        @DisplayName("should return filings within date range")
        void shouldReturnFilingsWithinDateRange() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13D(
                    "0001234567-24-000002", "0008765432", "Tech Inc",
                    "0001234567", "Activist Capital",
                    "880088009", 6.2, LocalDate.of(2024, 12, 25)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/form13dg/event-date-range")
                            .queryParam("startDate", "2024-12-01")
                            .queryParam("endDate", "2024-12-20")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1);
        }

        @Test
        @DisplayName("should return 400 for invalid date format")
        void shouldReturn400ForInvalidDateFormat() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/form13dg/event-date-range")
                            .queryParam("startDate", "invalid")
                            .queryParam("endDate", "2024-12-31")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/form13dg/filer")
    class SearchByFilingPersonName {

        @Test
        @DisplayName("should search by filing person name")
        void shouldSearchByFilingPersonName() {
            repository.save(createTest13D(
                    "0001234567-24-000001", "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital Partners",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createTest13G(
                    "0002345678-25-000001", "0008765432", "Tech Inc",
                    "0002345678", "Vanguard Group",
                    "880088009", 9.8, LocalDate.of(2024, 12, 31)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/form13dg/filer")
                            .queryParam("name", "Activist")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].filingPersonName").isEqualTo("Activist Capital Partners");
        }
    }
}
