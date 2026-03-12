package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form6K;
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
 * Integration tests for Form6KRepository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 6-K Repository Tests")
class Form6KRepositoryTest {

    @Autowired
    private Form6KRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form6K createForm6K(String accession, String cik, String symbol, LocalDate filedDate) {
        return Form6K.builder()
                .accessionNumber(accession)
                .cik(cik)
                .tradingSymbol(symbol)
                .formType("6-K")
                .filedDate(filedDate)
                .companyName("NOVA LTD")
                .build();
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm6K("0001111111-25-000001", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));

            assertThat(repository.findByAccessionNumber("0001111111-25-000001")).isPresent();
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createForm6K("0001111111-25-000002", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));
            repository.save(createForm6K("0001111111-25-000003", "0001111111", "NOVA", LocalDate.of(2025, 11, 2)));
            repository.save(createForm6K("0002222222-25-000001", "0002222222", "ZZZ", LocalDate.of(2025, 11, 1)));

            Page<Form6K> results = repository.findByCik("0001111111",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "filedDate")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0001111111".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by trading symbol")
        void shouldFindByTradingSymbol() {
            repository.save(createForm6K("0001111111-25-000004", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));
            repository.save(createForm6K("0002222222-25-000002", "0002222222", "ZZZ", LocalDate.of(2025, 11, 1)));

            Page<Form6K> results = repository.findByTradingSymbol("NOVA", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTradingSymbol()).isEqualTo("NOVA");
        }

        @Test
        @DisplayName("should find by filed date range")
        void shouldFindByFiledDateRange() {
            repository.save(createForm6K("0001111111-25-000005", "0001111111", "NOVA", LocalDate.of(2025, 11, 1)));
            repository.save(createForm6K("0001111111-25-000006", "0001111111", "NOVA", LocalDate.of(2025, 11, 10)));

            Page<Form6K> results = repository.findByFiledDateBetween(
                    LocalDate.of(2025, 11, 2),
                    LocalDate.of(2025, 11, 20),
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getAccessionNumber()).isEqualTo("0001111111-25-000006");
        }
    }
}

