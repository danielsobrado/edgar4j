package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
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
 * Integration tests for Form13FRepository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 13F Repository Tests")
class Form13FRepositoryTest {

    @Autowired
    private Form13FRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("should save Form 13F with holdings")
        void shouldSaveForm13FWithHoldings() {
            List<Form13FHolding> holdings = List.of(
                    createHolding("APPLE INC", "037833100", 1500000L, 8500L),
                    createHolding("MICROSOFT CORP", "594918104", 2000000L, 5200L)
            );
            Form13F form13F = createTestForm13F("0001234567-24-000001", "0001234567",
                    "Test Investment Manager", LocalDate.of(2024, 9, 30), holdings);

            Form13F saved = repository.save(form13F);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getAccessionNumber()).isEqualTo("0001234567-24-000001");
            assertThat(saved.getHoldings()).hasSize(2);
            assertThat(saved.getTotalValue()).isEqualTo(3500000L);
        }

        @Test
        @DisplayName("should update existing Form 13F")
        void shouldUpdateExistingForm13F() {
            List<Form13FHolding> holdings = List.of(
                    createHolding("APPLE INC", "037833100", 1500000L, 8500L)
            );
            Form13F form13F = createTestForm13F("0001234567-24-000002", "0001234567",
                    "Test Manager", LocalDate.of(2024, 9, 30), holdings);
            Form13F saved = repository.save(form13F);

            saved.setFilerName("Updated Manager Name");
            Form13F updated = repository.save(saved);

            assertThat(updated.getId()).isEqualTo(saved.getId());
            assertThat(updated.getFilerName()).isEqualTo("Updated Manager Name");
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            Form13F form13F = createTestForm13F("0001234567-24-000003", "0001234567",
                    "Test Manager", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("TESLA INC", "88160R101", 500000L, 2000L)));
            repository.save(form13F);

            Optional<Form13F> found = repository.findByAccessionNumber("0001234567-24-000003");

            assertThat(found).isPresent();
            assertThat(found.get().getFilerName()).isEqualTo("Test Manager");
        }

        @Test
        @DisplayName("should find by CIK")
        void shouldFindByCik() {
            repository.save(createTestForm13F("0001234567-24-000004", "0001234567",
                    "Manager A", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F("0001234567-24-000005", "0001234567",
                    "Manager A", LocalDate.of(2024, 6, 30),
                    List.of(createHolding("APPLE INC", "037833100", 900000L, 4500L))));
            repository.save(createTestForm13F("0009999999-24-000001", "0009999999",
                    "Manager B", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("GOOGLE", "02079K305", 800000L, 400L))));

            Page<Form13F> results = repository.findByCik("0001234567",
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "reportPeriod")));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0001234567".equals(f.getCik()));
        }

        @Test
        @DisplayName("should find by filer name containing")
        void shouldFindByFilerNameContaining() {
            repository.save(createTestForm13F("0001234567-24-000006", "0001234567",
                    "Berkshire Hathaway Inc", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 150000000L, 850000L))));
            repository.save(createTestForm13F("0009999999-24-000002", "0009999999",
                    "Vanguard Group", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 200000000L, 1100000L))));

            Page<Form13F> results = repository.findByFilerNameContainingIgnoreCase("berkshire",
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getFilerName()).contains("Berkshire");
        }

        @Test
        @DisplayName("should find by report period")
        void shouldFindByReportPeriod() {
            LocalDate q3_2024 = LocalDate.of(2024, 9, 30);
            LocalDate q2_2024 = LocalDate.of(2024, 6, 30);

            repository.save(createTestForm13F("0001234567-24-000007", "0001234567",
                    "Manager A", q3_2024,
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F("0001234567-24-000008", "0001234567",
                    "Manager A", q2_2024,
                    List.of(createHolding("APPLE INC", "037833100", 900000L, 4500L))));

            Page<Form13F> results = repository.findByReportPeriod(q3_2024, PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getReportPeriod()).isEqualTo(q3_2024);
        }

        @Test
        @DisplayName("should find by holding CUSIP")
        void shouldFindByHoldingCusip() {
            repository.save(createTestForm13F("0001234567-24-000009", "0001234567",
                    "Manager A", LocalDate.of(2024, 9, 30),
                    List.of(
                            createHolding("APPLE INC", "037833100", 1000000L, 5000L),
                            createHolding("MICROSOFT CORP", "594918104", 800000L, 2000L)
                    )));
            repository.save(createTestForm13F("0009999999-24-000003", "0009999999",
                    "Manager B", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("GOOGLE", "02079K305", 500000L, 250L))));

            Page<Form13F> results = repository.findByHoldingCusip("037833100", PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getCik()).isEqualTo("0001234567");
        }

        @Test
        @DisplayName("should find by holding issuer name")
        void shouldFindByHoldingIssuerName() {
            repository.save(createTestForm13F("0001234567-24-000010", "0001234567",
                    "Manager A", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F("0009999999-24-000004", "0009999999",
                    "Manager B", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("MICROSOFT CORP", "594918104", 800000L, 2000L))));

            Page<Form13F> results = repository.findByHoldingIssuerName("APPLE",
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Existence and Delete Operations")
    class ExistenceAndDeleteOperations {

        @Test
        @DisplayName("should check existence by accession number")
        void shouldCheckExistenceByAccessionNumber() {
            repository.save(createTestForm13F("0001234567-24-000011", "0001234567",
                    "Manager", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));

            assertThat(repository.existsByAccessionNumber("0001234567-24-000011")).isTrue();
            assertThat(repository.existsByAccessionNumber("0001234567-24-999999")).isFalse();
        }

        @Test
        @DisplayName("should delete by accession number")
        void shouldDeleteByAccessionNumber() {
            Form13F saved = repository.save(createTestForm13F("0001234567-24-000012", "0001234567",
                    "Manager", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));

            repository.deleteByAccessionNumber("0001234567-24-000012");

            assertThat(repository.findById(saved.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Date Range Queries")
    class DateRangeQueries {

        @Test
        @DisplayName("should find by report period range")
        void shouldFindByReportPeriodRange() {
            repository.save(createTestForm13F("0001234567-24-000013", "0001234567",
                    "Manager", LocalDate.of(2024, 3, 31),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F("0001234567-24-000014", "0001234567",
                    "Manager", LocalDate.of(2024, 6, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1100000L, 5500L))));
            repository.save(createTestForm13F("0001234567-24-000015", "0001234567",
                    "Manager", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1200000L, 6000L))));

            Page<Form13F> results = repository.findByReportPeriodBetween(
                    LocalDate.of(2024, 4, 1),
                    LocalDate.of(2024, 9, 30),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "reportPeriod")));

            assertThat(results.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("should find by CIK and report period range")
        void shouldFindByCikAndReportPeriodRange() {
            repository.save(createTestForm13F("0001234567-24-000016", "0001234567",
                    "Manager A", LocalDate.of(2024, 6, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1000000L, 5000L))));
            repository.save(createTestForm13F("0001234567-24-000017", "0001234567",
                    "Manager A", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 1100000L, 5500L))));
            repository.save(createTestForm13F("0009999999-24-000005", "0009999999",
                    "Manager B", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("GOOGLE", "02079K305", 500000L, 250L))));

            Page<Form13F> results = repository.findByCikAndReportPeriodBetween(
                    "0001234567",
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 12, 31),
                    PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(f -> "0001234567".equals(f.getCik()));
        }
    }

    @Nested
    @DisplayName("Value-Based Queries")
    class ValueBasedQueries {

        @Test
        @DisplayName("should find by minimum total value")
        void shouldFindByMinTotalValue() {
            repository.save(createTestForm13F("0001234567-24-000018", "0001234567",
                    "Small Fund", LocalDate.of(2024, 9, 30),
                    List.of(createHolding("APPLE INC", "037833100", 100000L, 500L))));
            repository.save(createTestForm13F("0009999999-24-000006", "0009999999",
                    "Large Fund", LocalDate.of(2024, 9, 30),
                    List.of(
                            createHolding("APPLE INC", "037833100", 5000000L, 25000L),
                            createHolding("MICROSOFT CORP", "594918104", 4000000L, 10000L)
                    )));

            Page<Form13F> results = repository.findByMinTotalValue(1000000L, PageRequest.of(0, 10));

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getFilerName()).isEqualTo("Large Fund");
        }
    }
}
