package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.Date;

import org.jds.edgar4j.model.Form3;
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
 * Integration tests for Form3Repository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 3 Repository Tests")
class Form3RepositoryTest {

    @Autowired
    private Form3Repository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form3 createForm3(String accession, String cik, String symbol, Date filedDate) {
        return Form3.builder()
                .accessionNumber(accession)
                .cik(cik)
                .tradingSymbol(symbol)
                .documentType("3")
                .filedDate(filedDate)
                .issuerName("ACME CORP")
                .rptOwnerName("Jane Doe")
                .build();
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm3("0000555555-25-000001", "0000555555", "ACME", new Date()));

            assertThat(repository.findByAccessionNumber("0000555555-25-000001")).isPresent();
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createForm3("0000555555-25-000002", "0000555555", "ACME", new Date()));
            repository.save(createForm3("0000555555-25-000003", "0000555555", "ACME", new Date()));
            repository.save(createForm3("0000999999-25-000001", "0000999999", "ZZZ", new Date()));

            Page<Form3> results = repository.findByCik("0000555555",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "filedDate")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0000555555".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by trading symbol")
        void shouldFindByTradingSymbol() {
            repository.save(createForm3("0000555555-25-000004", "0000555555", "ACME", new Date()));
            repository.save(createForm3("0000999999-25-000002", "0000999999", "ZZZ", new Date()));

            Page<Form3> results = repository.findByTradingSymbol("ACME", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTradingSymbol()).isEqualTo("ACME");
        }

        @Test
        @DisplayName("should find by filed date range")
        void shouldFindByFiledDateRange() {
            Calendar cal = Calendar.getInstance();
            cal.set(2025, Calendar.NOVEMBER, 1);
            Date nov1 = cal.getTime();
            cal.set(2025, Calendar.NOVEMBER, 10);
            Date nov10 = cal.getTime();
            cal.set(2025, Calendar.NOVEMBER, 2);
            Date nov2 = cal.getTime();
            cal.set(2025, Calendar.NOVEMBER, 20);
            Date nov20 = cal.getTime();

            repository.save(createForm3("0000555555-25-000005", "0000555555", "ACME", nov1));
            repository.save(createForm3("0000555555-25-000006", "0000555555", "ACME", nov10));

            Page<Form3> results = repository.findByFiledDateBetween(
                    nov2,
                    nov20,
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getAccessionNumber()).isEqualTo("0000555555-25-000006");
        }
    }
}

