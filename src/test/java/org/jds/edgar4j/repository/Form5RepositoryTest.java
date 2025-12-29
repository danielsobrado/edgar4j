package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.Date;

import org.jds.edgar4j.model.Form5;
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
 * Integration tests for Form5Repository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 5 Repository Tests")
class Form5RepositoryTest {

    @Autowired
    private Form5Repository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private Form5 createForm5(String accession, String cik, String symbol, Date filedDate) {
        return Form5.builder()
                .accessionNumber(accession)
                .cik(cik)
                .tradingSymbol(symbol)
                .documentType("5")
                .filedDate(filedDate)
                .issuerName("NOVA LTD")
                .rptOwnerName("John Smith")
                .build();
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm5("0000777777-25-000001", "0000777777", "NOVA", new Date()));

            assertThat(repository.findByAccessionNumber("0000777777-25-000001")).isPresent();
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createForm5("0000777777-25-000002", "0000777777", "NOVA", new Date()));
            repository.save(createForm5("0000777777-25-000003", "0000777777", "NOVA", new Date()));
            repository.save(createForm5("0000888888-25-000001", "0000888888", "ZZZ", new Date()));

            Page<Form5> results = repository.findByCik("0000777777",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "filedDate")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0000777777".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by trading symbol")
        void shouldFindByTradingSymbol() {
            repository.save(createForm5("0000777777-25-000004", "0000777777", "NOVA", new Date()));
            repository.save(createForm5("0000888888-25-000002", "0000888888", "ZZZ", new Date()));

            Page<Form5> results = repository.findByTradingSymbol("NOVA", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTradingSymbol()).isEqualTo("NOVA");
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

            repository.save(createForm5("0000777777-25-000005", "0000777777", "NOVA", nov1));
            repository.save(createForm5("0000777777-25-000006", "0000777777", "NOVA", nov10));

            Page<Form5> results = repository.findByFiledDateBetween(
                    nov2,
                    nov20,
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getAccessionNumber()).isEqualTo("0000777777-25-000006");
        }
    }
}

