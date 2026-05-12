package org.jds.edgar4j.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.Sp500Constituent;
import org.jds.edgar4j.repository.CompanyMarketDataRepository;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.repository.Sp500ConstituentRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")`r`n@SuppressWarnings("removal")`r`nclass InsiderPurchaseControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private Form4Repository form4Repository;

    @Autowired
    private CompanyMarketDataRepository companyMarketDataRepository;

    @Autowired
    private Sp500ConstituentRepository sp500ConstituentRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        cleanRepositories();
    }

    @AfterEach
    void tearDown() {
        cleanRepositories();
    }

    @Nested
    @DisplayName("GET /api/insider-purchases")
    class GetInsiderPurchases {

        @Test
        @DisplayName("Should return filtered insider purchases wrapped in ApiResponse")
        void shouldReturnFilteredInsiderPurchases() {
            seedPurchaseData();

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/insider-purchases")
                            .queryParam("lookbackDays", "30")
                            .queryParam("sp500Only", "true")
                            .queryParam("minMarketCap", "1000000000")
                            .queryParam("minTransactionValue", "1000")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.content").value(hasSize(1))
                    .jsonPath("$.data.content[0].ticker").isEqualTo("AAPL")
                    .jsonPath("$.data.content[0].sp500").isEqualTo(true)
                    .jsonPath("$.data.content[0].percentChange").value(closeTo(20d, 0.0001d))
                    .jsonPath("$.data.page").isEqualTo(0)
                    .jsonPath("$.data.hasNext").isEqualTo(false)
                    .jsonPath("$.data.totalElements").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /api/insider-purchases/top")
    class GetTopInsiderPurchases {

        @Test
        @DisplayName("Should return top insider purchases by percent change")
        void shouldReturnTopInsiderPurchases() {
            seedPurchaseData();

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/insider-purchases/top")
                            .queryParam("limit", "2")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").value(hasSize(2))
                    .jsonPath("$.data[0].ticker").isEqualTo("AAPL");
        }
    }

    @Nested
    @DisplayName("GET /api/insider-purchases/summary")
    class GetSummary {

        @Test
        @DisplayName("Should return summary statistics for recent purchases")
        void shouldReturnSummaryStatistics() {
            seedPurchaseData();

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/insider-purchases/summary")
                            .queryParam("lookbackDays", "30")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.totalPurchases").isEqualTo(2)
                    .jsonPath("$.data.uniqueCompanies").isEqualTo(2)
                    .jsonPath("$.data.totalPurchaseValue").isEqualTo(1300d)
                    .jsonPath("$.data.averagePercentChange").isEqualTo(15d);
        }
    }

    private void seedPurchaseData() {
        form4Repository.saveAll(List.of(
                createForm4(
                        "0001234567-26-000001",
                        "AAPL",
                        "Apple Inc.",
                        List.of(createTransaction("P", "A", 10f, 100f, LocalDate.now().minusDays(4)))),
                createForm4(
                        "0001234567-26-000002",
                        "OTHR",
                        "Other Inc.",
                        List.of(createTransaction("P", "A", 15f, 20f, LocalDate.now().minusDays(3))))
        ));

        companyMarketDataRepository.saveAll(List.of(
                CompanyMarketData.builder()
                        .ticker("AAPL")
                        .cik("0000320193")
                        .marketCap(3_000_000_000_000d)
                        .currentPrice(120d)
                        .previousClose(119d)
                        .currency("USD")
                        .lastUpdated(Instant.now())
                        .build(),
                CompanyMarketData.builder()
                        .ticker("OTHR")
                        .cik("0000009999")
                        .marketCap(500_000_000d)
                        .currentPrice(22d)
                        .previousClose(21.5d)
                        .currency("USD")
                        .lastUpdated(Instant.now())
                        .build()
        ));

        sp500ConstituentRepository.save(Sp500Constituent.builder()
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .cik("0000320193")
                .lastUpdated(Instant.now())
                .build());
    }

    private Form4 createForm4(
            String accessionNumber,
            String ticker,
            String issuerName,
            List<Form4Transaction> transactions) {
        Form4Transaction primaryTransaction = transactions.get(0);

        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("0000123456")
                .issuerName(issuerName)
                .tradingSymbol(ticker)
                .rptOwnerCik("0000000001")
                .rptOwnerName("Test Insider")
                .ownerType("Officer")
                .officerTitle("Chief Financial Officer")
                .isOfficer(true)
                .transactionDate(primaryTransaction.getTransactionDate())
                .transactionShares(primaryTransaction.getTransactionShares())
                .transactionPricePerShare(primaryTransaction.getTransactionPricePerShare())
                .transactionValue(primaryTransaction.getTransactionValue())
                .acquiredDisposedCode(primaryTransaction.getAcquiredDisposedCode())
                .transactions(transactions)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Form4Transaction createTransaction(
            String transactionCode,
            String acquiredDisposedCode,
            float shares,
            float price,
            LocalDate transactionDate) {
        return Form4Transaction.builder()
                .accessionNumber("ignored")
                .transactionType("NON_DERIVATIVE")
                .securityTitle("Common Stock")
                .transactionCode(transactionCode)
                .transactionShares(shares)
                .transactionPricePerShare(price)
                .transactionValue(shares * price)
                .acquiredDisposedCode(acquiredDisposedCode)
                .transactionDate(transactionDate)
                .build();
    }

    private void cleanRepositories() {
        form4Repository.deleteAll();
        companyMarketDataRepository.deleteAll();
        sp500ConstituentRepository.deleteAll();
    }
}

