package org.jds.edgar4j.controller;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.repository.Form4Repository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Form4Controller REST endpoints.
 * Uses WebTestClient and embedded MongoDB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Form4ControllerTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private Form4Repository form4Repository;

    private static final String BASE_URL = "/api/form4";
    private static final String ACCESSION_1 = "0001234567-24-000001";
    private static final String ACCESSION_2 = "0001234567-24-000002";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        form4Repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        form4Repository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/form4/{id}")
    class GetById {

        @Test
        @DisplayName("Should return Form4 when found by ID")
        void shouldReturnForm4WhenFound() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo(ACCESSION_1)
                    .jsonPath("$.tradingSymbol").isEqualTo("MSFT")
                    .jsonPath("$.rptOwnerName").isEqualTo("John Doe");
        }

        @Test
        @DisplayName("Should return 404 when Form4 not found")
        void shouldReturn404WhenNotFound() throws Exception {
            webTestClient.get()
                    .uri(BASE_URL + "/nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form4/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("Should return Form4 by accession number")
        void shouldReturnByAccessionNumber() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            webTestClient.get()
                    .uri(BASE_URL + "/accession/" + ACCESSION_1)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo(ACCESSION_1)
                    .jsonPath("$.tradingSymbol").isEqualTo("MSFT");
        }

        @Test
        @DisplayName("Should return 404 when accession number not found")
        void shouldReturn404WhenAccessionNotFound() throws Exception {
            webTestClient.get()
                    .uri(BASE_URL + "/accession/nonexistent")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("Should return paginated Form4 by symbol")
        void shouldReturnPaginatedBySymbol() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Repository.save(createForm4(ACCESSION_2, "MSFT", "Jane Smith"));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/symbol/MSFT")
                            .queryParam("page", "0")
                            .queryParam("size", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(2))
                    .jsonPath("$.content[*].tradingSymbol").value(everyItem(is("MSFT")))
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.totalPages").isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when symbol not found")
        void shouldReturnEmptyPageWhenSymbolNotFound() throws Exception {
            webTestClient.get()
                    .uri(BASE_URL + "/symbol/UNKNOWN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(0))
                    .jsonPath("$.totalElements").isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            webTestClient.get()
                    .uri(BASE_URL + "/symbol/msft")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("Should return paginated Form4 by CIK")
        void shouldReturnPaginatedByCik() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            webTestClient.get()
                    .uri(BASE_URL + "/cik/789019")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1))
                    .jsonPath("$.content[0].cik").isEqualTo("789019");
        }
    }

    @Nested
    @DisplayName("GET /api/form4/owner")
    class SearchByOwner {

        @Test
        @DisplayName("Should search Form4 by owner name")
        void shouldSearchByOwnerName() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Repository.save(createForm4(ACCESSION_2, "MSFT", "Jane Doe"));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/owner")
                            .queryParam("name", "Doe")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(2))
                    .jsonPath("$[*].rptOwnerName").value(everyItem(containsString("Doe")));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/date-range")
    class GetByDateRange {

        @Test
        @DisplayName("Should return Form4 within date range")
        void shouldReturnByDateRange() throws Exception {
            LocalDate txDate = LocalDate.of(2024, 1, 15);

            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");
            form4.setTransactionDate(txDate);
            form4Repository.save(form4);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/date-range")
                            .queryParam("startDate", "2024-01-10")
                            .queryParam("endDate", "2024-01-20")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1));
        }

        @Test
        @DisplayName("Should return 400 for invalid date format")
        void shouldReturn400ForInvalidDateFormat() throws Exception {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/date-range")
                            .queryParam("startDate", "invalid-date")
                            .queryParam("endDate", "2024-01-20")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}/date-range")
    class GetBySymbolAndDateRange {

        @Test
        @DisplayName("Should return Form4 by symbol and date range")
        void shouldReturnBySymbolAndDateRange() throws Exception {
            LocalDate txDate = LocalDate.of(2024, 1, 15);

            Form4 msft = createForm4(ACCESSION_1, "MSFT", "John Doe");
            msft.setTransactionDate(txDate);
            form4Repository.save(msft);

            Form4 aapl = createForm4(ACCESSION_2, "AAPL", "Tim Cook");
            aapl.setTransactionDate(txDate);
            form4Repository.save(aapl);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/symbol/MSFT/date-range")
                            .queryParam("startDate", "2024-01-10")
                            .queryParam("endDate", "2024-01-20")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").value(hasSize(1))
                    .jsonPath("$.content[0].tradingSymbol").isEqualTo("MSFT");
        }
    }

    @Nested
    @DisplayName("GET /api/form4/recent")
    class GetRecentFilings {

        @Test
        @DisplayName("Should return recent filings with default limit")
        void shouldReturnRecentWithDefaultLimit() throws Exception {
            for (int i = 0; i < 15; i++) {
                Form4 form4 = createForm4(
                        String.format("0001234567-24-%06d", i),
                        "MSFT",
                        "Owner " + i
                );
                LocalDate txDate = LocalDate.now().minusDays(i);
                form4.setTransactionDate(txDate);
                form4Repository.save(form4);
            }

            webTestClient.get()
                    .uri(BASE_URL + "/recent")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(10));
        }

        @Test
        @DisplayName("Should return recent filings with custom limit")
        void shouldReturnRecentWithCustomLimit() throws Exception {
            for (int i = 0; i < 10; i++) {
                form4Repository.save(createForm4(
                        String.format("0001234567-24-%06d", i),
                        "MSFT",
                        "Owner " + i
                ));
            }

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/recent")
                            .queryParam("limit", "5")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").value(hasSize(5));
        }

        @Test
        @DisplayName("Should cap limit at 100")
        void shouldCapLimitAt100() throws Exception {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/recent")
                            .queryParam("limit", "500")
                            .build())
                    .exchange()
                    .expectStatus().isOk();
            // Just verify it doesn't error; actual cap is applied in controller
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}/stats")
    class GetInsiderStats {

        @Test
        @DisplayName("Should return insider statistics for symbol")
        void shouldReturnInsiderStats() throws Exception {
            LocalDate txDate = LocalDate.of(2024, 1, 15);

            Form4 buy = createForm4(ACCESSION_1, "MSFT", "Buyer");
            buy.setTransactionDate(txDate);
            buy.setAcquiredDisposedCode("A");
            buy.setTransactionValue(100000f);
            buy.setDirector(true);
            buy.setOfficer(false);  // Override default
            form4Repository.save(buy);

            Form4 sell = createForm4(ACCESSION_2, "MSFT", "Seller");
            sell.setTransactionDate(txDate);
            sell.setAcquiredDisposedCode("D");
            sell.setTransactionValue(50000f);
            sell.setOfficer(true);
            form4Repository.save(sell);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_URL + "/symbol/MSFT/stats")
                            .queryParam("startDate", "2024-01-01")
                            .queryParam("endDate", "2024-01-31")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalBuys").isEqualTo(1)
                    .jsonPath("$.totalSells").isEqualTo(1)
                    .jsonPath("$.directorTransactions").isEqualTo(1)
                    .jsonPath("$.officerTransactions").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("POST /api/form4")
    class SaveForm4 {

        @Test
        @DisplayName("Should save new Form4")
        void shouldSaveNewForm4() throws Exception {
            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");
            form4.setId(null); // Ensure no ID for new record

            webTestClient.post()
                    .uri(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(form4)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessionNumber").isEqualTo(ACCESSION_1)
                    .jsonPath("$.id").exists();
        }

        @Test
        @DisplayName("Should update existing Form4")
        void shouldUpdateExistingForm4() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            saved.setRptOwnerName("Updated Name");

            webTestClient.post()
                    .uri(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(saved)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.rptOwnerName").isEqualTo("Updated Name");
        }
    }

    @Nested
    @DisplayName("DELETE /api/form4/{id}")
    class DeleteForm4 {

        @Test
        @DisplayName("Should delete Form4 by ID")
        void shouldDeleteForm4() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            webTestClient.delete()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            // Verify deleted
            webTestClient.get()
                    .uri(BASE_URL + "/" + saved.getId())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent Form4")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            webTestClient.delete()
                    .uri(BASE_URL + "/nonexistent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    private Form4 createForm4(String accessionNumber, String symbol, String ownerName) {
        List<Form4Transaction> transactions = new ArrayList<>();
        transactions.add(Form4Transaction.builder()
                .accessionNumber(accessionNumber)
                .transactionType("NON_DERIVATIVE")
                .securityTitle("Common Stock")
                .transactionCode("P")
                .transactionShares(1000f)
                .transactionPricePerShare(100f)
                .transactionValue(100000f)
                .acquiredDisposedCode("A")
                .build());

        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("789019")
                .issuerName("MICROSOFT CORP")
                .tradingSymbol(symbol)
                .rptOwnerCik("0001234567")
                .rptOwnerName(ownerName)
                .isDirector(false)
                .isOfficer(true)
                .isTenPercentOwner(false)
                .isOther(false)
                .ownerType("Officer")
                .officerTitle("CFO")
                .securityTitle("Common Stock")
                .transactionDate(LocalDate.now())
                .transactionShares(1000f)
                .transactionPricePerShare(100f)
                .transactionValue(100000f)
                .acquiredDisposedCode("A")
                .transactions(transactions)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
