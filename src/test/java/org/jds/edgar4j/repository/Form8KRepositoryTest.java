package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form8K;
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
 * Integration tests for Form8KRepository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 8-K Repository Tests")
class Form8KRepositoryTest {

    @Autowired
    private Form8KRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form8K createForm8K(String accession, String cik, String symbol, LocalDate filedDate) {
        return Form8K.builder()
                .accessionNumber(accession)
                .cik(cik)
                .tradingSymbol(symbol)
                .formType("8-K")
                .filedDate(filedDate)
                .companyName("ACME CORPORATION")
                .build();
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm8K("0001234567-25-000001", "0001234567", "ACME", LocalDate.of(2025, 10, 2)));

            assertThat(repository.findByAccessionNumber("0001234567-25-000001")).isPresent();
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createForm8K("0001234567-25-000002", "0001234567", "ACME", LocalDate.of(2025, 10, 2)));
            repository.save(createForm8K("0001234567-25-000003", "0001234567", "ACME", LocalDate.of(2025, 10, 3)));
            repository.save(createForm8K("0009999999-25-000001", "0009999999", "ZZZ", LocalDate.of(2025, 10, 2)));

            Page<Form8K> results = repository.findByCik("0001234567",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "filedDate")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0001234567".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by trading symbol")
        void shouldFindByTradingSymbol() {
            repository.save(createForm8K("0001234567-25-000004", "0001234567", "ACME", LocalDate.of(2025, 10, 2)));
            repository.save(createForm8K("0009999999-25-000002", "0009999999", "ZZZ", LocalDate.of(2025, 10, 2)));

            Page<Form8K> results = repository.findByTradingSymbol("ACME", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTradingSymbol()).isEqualTo("ACME");
        }

        @Test
        @DisplayName("should find by filed date range")
        void shouldFindByFiledDateRange() {
            repository.save(createForm8K("0001234567-25-000005", "0001234567", "ACME", LocalDate.of(2025, 10, 1)));
            repository.save(createForm8K("0001234567-25-000006", "0001234567", "ACME", LocalDate.of(2025, 10, 10)));

            Page<Form8K> results = repository.findByFiledDateBetween(
                    LocalDate.of(2025, 10, 2),
                    LocalDate.of(2025, 10, 20),
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getAccessionNumber()).isEqualTo("0001234567-25-000006");
        }
    }
}

