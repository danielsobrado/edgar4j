package org.jds.edgar4j.controller;

import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.repository.FillingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FilingControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private FillingRepository fillingRepository;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        fillingRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        fillingRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/filings/search returns all requested form types for a CIK")
    void searchFilingsByCikAndMultipleFormTypes() {
        fillingRepository.saveAll(List.of(
                createFiling("0000789019", "0000789019-25-000001", "10-K"),
                createFiling("0000789019", "0000789019-25-000002", "10-Q"),
                createFiling("0000789019", "0000789019-25-000003", "8-K"),
                createFiling("0000320193", "0000320193-25-000001", "10-K")
        ));

        webClient.post()
                .uri("/api/filings/search")
                .bodyValue(Map.of(
                        "cik", "0000789019",
                        "formTypes", List.of("10-K", "10-Q"),
                        "page", 0,
                        "size", 10
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                .jsonPath("$.data.content[0].cik").isEqualTo("0000789019")
                .jsonPath("$.data.content[1].cik").isEqualTo("0000789019");
    }

    private Filling createFiling(String cik, String accessionNumber, String formTypeNumber) {
        return Filling.builder()
                .company("Test Company")
                .cik(cik)
                .formType(FormType.builder()
                        .number(formTypeNumber)
                        .description(formTypeNumber + " filing")
                        .build())
                .fillingDate(new java.util.Date())
                .accessionNumber(accessionNumber)
                .primaryDocument("index.htm")
                .isXBRL(true)
                .isInlineXBRL(true)
                .url("https://www.sec.gov/Archives/test/" + accessionNumber + "/index.htm")
                .build();
    }
}
