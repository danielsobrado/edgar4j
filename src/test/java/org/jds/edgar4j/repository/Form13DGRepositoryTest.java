package org.jds.edgar4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13DG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Form13DGRepository.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 13D/G Repository Tests")
class Form13DGRepositoryTest {

    @Autowired
    private Form13DGRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private Form13DG createForm13DG(String accessionNumber, String scheduleType, String issuerCik,
                                     String issuerName, String filingPersonCik, String filingPersonName,
                                     String cusip, Double percentOfClass, LocalDate eventDate) {
        return Form13DG.builder()
                .accessionNumber(accessionNumber)
                .formType("SCHEDULE " + scheduleType)
                .scheduleType(scheduleType)
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
                .build();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("should save and find by ID")
        void shouldSaveAndFindById() {
            Form13DG form = createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15));

            Form13DG saved = repository.save(form);

            Optional<Form13DG> found = repository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getAccessionNumber()).isEqualTo("0001234567-24-000001");
        }

        @Test
        @DisplayName("should find by accession number")
        void shouldFindByAccessionNumber() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            Optional<Form13DG> found = repository.findByAccessionNumber("0001234567-24-000001");
            assertThat(found).isPresent();
            assertThat(found.get().getIssuerName()).isEqualTo("ACME Corp");
        }

        @Test
        @DisplayName("should delete by accession number")
        void shouldDeleteByAccessionNumber() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            repository.deleteByAccessionNumber("0001234567-24-000001");

            assertThat(repository.findByAccessionNumber("0001234567-24-000001")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Schedule Type Queries")
    class ScheduleTypeQueries {

        @Test
        @DisplayName("should find by schedule type")
        void shouldFindByScheduleType() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0008765432", "Tech Inc",
                    "0002345678", "Vanguard",
                    "880088009", 9.8, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings13D = repository.findByScheduleType("13D");
            assertThat(filings13D).hasSize(1);
            assertThat(filings13D.get(0).getFilingPersonName()).isEqualTo("Activist");

            List<Form13DG> filings13G = repository.findByScheduleType("13G");
            assertThat(filings13G).hasSize(1);
            assertThat(filings13G.get(0).getFilingPersonName()).isEqualTo("Vanguard");
        }

        @Test
        @DisplayName("should count by schedule type")
        void shouldCountByScheduleType() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0001234567-24-000002", "13D",
                    "0008765432", "Tech Inc",
                    "0001234567", "Activist",
                    "880088009", 6.0, LocalDate.of(2024, 12, 20)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0007654321", "Bank Corp",
                    "0002345678", "Vanguard",
                    "770077007", 9.8, LocalDate.of(2024, 12, 25)));

            assertThat(repository.countByScheduleType("13D")).isEqualTo(2);
            assertThat(repository.countByScheduleType("13G")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Issuer Queries")
    class IssuerQueries {

        @Test
        @DisplayName("should find by issuer CIK")
        void shouldFindByIssuerCik() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist 1",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0009876543", "ACME Corp",
                    "0002345678", "Activist 2",
                    "001234567", 9.8, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByIssuerCik("0009876543");
            assertThat(filings).hasSize(2);
        }

        @Test
        @DisplayName("should find by issuer name containing")
        void shouldFindByIssuerNameContaining() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corporation",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0008765432", "Tech Innovations Inc",
                    "0002345678", "Vanguard",
                    "880088009", 9.8, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByIssuerNameContainingIgnoreCase("acme");
            assertThat(filings).hasSize(1);
            assertThat(filings.get(0).getIssuerName()).isEqualTo("ACME Corporation");
        }
    }

    @Nested
    @DisplayName("CUSIP Queries")
    class CusipQueries {

        @Test
        @DisplayName("should find by CUSIP")
        void shouldFindByCusip() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist 1",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0009876543", "ACME Corp",
                    "0002345678", "Activist 2",
                    "001234567", 9.8, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByCusip("001234567");
            assertThat(filings).hasSize(2);
        }

        @Test
        @DisplayName("should count by CUSIP")
        void shouldCountByCusip() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            assertThat(repository.countByCusip("001234567")).isEqualTo(1);
            assertThat(repository.countByCusip("NOTFOUND")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Ownership Queries")
    class OwnershipQueries {

        @Test
        @DisplayName("should find by minimum percent of class")
        void shouldFindByMinPercentOfClass() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Small Activist",
                    "001234567", 5.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13D",
                    "0008765432", "Tech Inc",
                    "0002345678", "Big Activist",
                    "880088009", 15.0, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByMinPercentOfClass(10.0);
            assertThat(filings).hasSize(1);
            assertThat(filings.get(0).getFilingPersonName()).isEqualTo("Big Activist");
        }

        @Test
        @DisplayName("should find ten percent owners")
        void shouldFindTenPercentOwners() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Small Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13D",
                    "0008765432", "Tech Inc",
                    "0002345678", "Big Activist",
                    "880088009", 12.0, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findTenPercentOwners();
            assertThat(filings).hasSize(1);
            assertThat(filings.get(0).getPercentOfClass()).isEqualTo(12.0);
        }
    }

    @Nested
    @DisplayName("Date Range Queries")
    class DateRangeQueries {

        @Test
        @DisplayName("should find by event date between")
        void shouldFindByEventDateBetween() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 10)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13D",
                    "0008765432", "Tech Inc",
                    "0002345678", "Big Activist",
                    "880088009", 12.0, LocalDate.of(2024, 12, 25)));

            List<Form13DG> filings = repository.findByEventDateBetween(
                    LocalDate.of(2024, 12, 1),
                    LocalDate.of(2024, 12, 15));

            assertThat(filings).hasSize(1);
            assertThat(filings.get(0).getEventDate()).isEqualTo(LocalDate.of(2024, 12, 10));
        }
    }

    @Nested
    @DisplayName("Amendment Queries")
    class AmendmentQueries {

        @Test
        @DisplayName("should find amendments")
        void shouldFindAmendments() {
            Form13DG initial = createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15));
            initial.setAmendmentType("INITIAL");
            repository.save(initial);

            Form13DG amendment = createForm13DG(
                    "0001234567-24-000002", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 8.5, LocalDate.of(2024, 12, 20));
            amendment.setAmendmentType("AMENDMENT");
            amendment.setAmendmentNumber(1);
            repository.save(amendment);

            List<Form13DG> amendments = repository.findAmendments();
            assertThat(amendments).hasSize(1);
            assertThat(amendments.get(0).getAmendmentNumber()).isEqualTo(1);

            List<Form13DG> initials = repository.findInitialFilings();
            assertThat(initials).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Filing Person Queries")
    class FilingPersonQueries {

        @Test
        @DisplayName("should find by filing person CIK")
        void shouldFindByFilingPersonCik() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0001234567-24-000002", "13D",
                    "0008765432", "Tech Inc",
                    "0001234567", "Activist Capital",
                    "880088009", 6.0, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByFilingPersonCik("0001234567");
            assertThat(filings).hasSize(2);
        }

        @Test
        @DisplayName("should find by filing person name containing")
        void shouldFindByFilingPersonNameContaining() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist Capital Partners LP",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));
            repository.save(createForm13DG(
                    "0002345678-24-000001", "13G",
                    "0008765432", "Tech Inc",
                    "0002345678", "Vanguard Group Inc",
                    "880088009", 9.8, LocalDate.of(2024, 12, 20)));

            List<Form13DG> filings = repository.findByFilingPersonNameContainingIgnoreCase("activist");
            assertThat(filings).hasSize(1);
            assertThat(filings.get(0).getFilingPersonName()).isEqualTo("Activist Capital Partners LP");
        }
    }

    @Nested
    @DisplayName("Pagination")
    class PaginationTests {

        @Test
        @DisplayName("should paginate results")
        void shouldPaginateResults() {
            for (int i = 0; i < 25; i++) {
                repository.save(createForm13DG(
                        String.format("0001234567-24-0000%02d", i), "13D",
                        "0009876543", "ACME Corp",
                        String.format("000123456%d", i % 10), "Activist " + i,
                        "001234567", 5.0 + (i * 0.1), LocalDate.of(2024, 12, 1).plusDays(i)));
            }

            Page<Form13DG> page1 = repository.findByIssuerCik("0009876543", PageRequest.of(0, 10));
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page1.getTotalElements()).isEqualTo(25);
            assertThat(page1.getTotalPages()).isEqualTo(3);

            Page<Form13DG> page3 = repository.findByIssuerCik("0009876543", PageRequest.of(2, 10));
            assertThat(page3.getContent()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Existence Checks")
    class ExistenceChecks {

        @Test
        @DisplayName("should check existence by accession number")
        void shouldCheckExistenceByAccessionNumber() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            assertThat(repository.existsByAccessionNumber("0001234567-24-000001")).isTrue();
            assertThat(repository.existsByAccessionNumber("NOTFOUND")).isFalse();
        }

        @Test
        @DisplayName("should check existence by issuer and filing person CIK")
        void shouldCheckExistenceByIssuerAndFilingPersonCik() {
            repository.save(createForm13DG(
                    "0001234567-24-000001", "13D",
                    "0009876543", "ACME Corp",
                    "0001234567", "Activist",
                    "001234567", 7.5, LocalDate.of(2024, 12, 15)));

            assertThat(repository.existsByIssuerCikAndFilingPersonCik("0009876543", "0001234567")).isTrue();
            assertThat(repository.existsByIssuerCikAndFilingPersonCik("0009876543", "NOTFOUND")).isFalse();
        }
    }
}
