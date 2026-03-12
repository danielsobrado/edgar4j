package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form20F;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Form20FRepository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 20-F Repository Tests")
class Form20FRepositoryTest {

    @Autowired
    private Form20FRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form20F createForm20F(String accession, String cik, String symbol, LocalDate filedDate, Integer fiscalYear) {
        return Form20F.builder()
                .accessionNumber(accession)
                .cik(cik)
                .companyName("ACME FOREIGN LTD")
                .tradingSymbol(symbol)
                .formType("20-F")
                .filedDate(filedDate)
                .fiscalYear(fiscalYear)
                .build();
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm20F("0001234567-25-000001", "0001234567", "ACME",
                    LocalDate.of(2026, 3, 1), 2025));

            assertThat(repository.findByAccessionNumber("0001234567-25-000001")).isPresent();
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createForm20F("0001234567-25-000002", "0001234567", "ACME",
                    LocalDate.of(2026, 3, 1), 2025));
            repository.save(createForm20F("0001234567-26-000001", "0001234567", "ACME",
                    LocalDate.of(2027, 3, 1), 2026));
            repository.save(createForm20F("0009999999-25-000001", "0009999999", "ZZZ",
                    LocalDate.of(2026, 3, 1), 2025));

            Page<Form20F> results = repository.findByCik("0001234567",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "filedDate")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0001234567".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by trading symbol")
        void shouldFindByTradingSymbol() {
            repository.save(createForm20F("0001234567-25-000003", "0001234567", "ACME",
                    LocalDate.of(2026, 3, 1), 2025));
            repository.save(createForm20F("0009999999-25-000002", "0009999999", "ZZZ",
                    LocalDate.of(2026, 3, 1), 2025));

            Page<Form20F> results = repository.findByTradingSymbol("ACME", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTradingSymbol()).isEqualTo("ACME");
        }

        @Test
        @DisplayName("should find by filed date range")
        void shouldFindByFiledDateRange() {
            repository.save(createForm20F("0001234567-25-000004", "0001234567", "ACME",
                    LocalDate.of(2026, 2, 1), 2025));
            repository.save(createForm20F("0001234567-25-000005", "0001234567", "ACME",
                    LocalDate.of(2026, 4, 1), 2025));

            Page<Form20F> results = repository.findByFiledDateBetween(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(0);
        }
    }
}

