package org.jds.edgar4j.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FillingDataPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

abstract class FillingDataPortContractTest {

    protected abstract FillingDataPort port();

    @Test
    void saveAndFindByAccessionNumber_roundTrip() {
        Filling filling = TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 2, 1));
        port().save(filling);

        assertEquals("10-K", port().findByAccessionNumber(filling.getAccessionNumber()).orElseThrow().getFormType().getNumber());
    }

    @Test
    void findByCik_supportsPagination() {
        for (int index = 0; index < 6; index++) {
            port().save(TestFixtures.createTestFilling("0000320193-24-00010" + index, "0000320193", "10-K", LocalDate.of(2024, 2, 1).plusDays(index)));
        }

        Page<Filling> page = port().findByCik("0000320193", PageRequest.of(0, 4));

        assertEquals(6, page.getTotalElements());
        assertEquals(4, page.getContent().size());
    }

    @Test
    void findByCompany_matchesCompanyName() {
        Filling first = TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 2, 1));
        first.setCompany("Apple Inc.");
        Filling second = TestFixtures.createTestFilling("0000789019-24-000001", "0000789019", "8-K", LocalDate.of(2024, 2, 2));
        second.setCompany("Microsoft Corp.");
        port().saveAll(List.of(first, second));

        Page<Filling> page = port().findByCompany("Apple Inc.", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void findByFormTypeNumber_filtersByForm() {
        port().save(TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 2, 1)));
        port().save(TestFixtures.createTestFilling("0000320193-24-000002", "0000320193", "8-K", LocalDate.of(2024, 2, 2)));

        Page<Filling> page = port().findByFormTypeNumber("10-K", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void findByCikAndFormType_combinesFilters() {
        port().save(TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 2, 1)));
        port().save(TestFixtures.createTestFilling("0000320193-24-000002", "0000320193", "8-K", LocalDate.of(2024, 2, 2)));

        Page<Filling> page = port().findByCikAndFormType("0000320193", "10-K", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void searchFillings_filtersDateAndForm() {
        port().save(TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 1, 5)));
        port().save(TestFixtures.createTestFilling("0000320193-24-000002", "0000320193", "8-K", LocalDate.of(2024, 2, 5)));

        Page<Filling> page = port().searchFillings(
                java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)),
                java.sql.Date.valueOf(LocalDate.of(2024, 1, 31)),
                List.of("10-K"),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void searchByCompanyOrCik_matchesEitherField() {
        Filling filling = TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 2, 1));
        filling.setCompany("Apple Inc.");
        port().save(filling);

        assertEquals(1, port().searchByCompanyOrCik("Apple", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, port().searchByCompanyOrCik("0000320193", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void findAllByOrderByFillingDateDesc_returnsMostRecentFirst() {
        port().save(TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 1, 1)));
        port().save(TestFixtures.createTestFilling("0000320193-24-000002", "0000320193", "10-Q", LocalDate.of(2024, 3, 1)));

        Page<Filling> page = port().findAllByOrderByFillingDateDesc(PageRequest.of(0, 10));

        assertEquals("0000320193-24-000002", page.getContent().get(0).getAccessionNumber());
    }

    @Test
    void findTop10ByOrderByFillingDateDesc_limitsResults() {
        for (int index = 0; index < 12; index++) {
            port().save(TestFixtures.createTestFilling("0000320193-24-1000" + index, "0000320193", "10-Q", LocalDate.of(2024, 1, 1).plusDays(index)));
        }

        assertEquals(10, port().findTop10ByOrderByFillingDateDesc().size());
    }

    @Test
    void countByFormTypeNumber_countsMatches() {
        port().save(TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 1, 1)));
        port().save(TestFixtures.createTestFilling("0000320193-24-000002", "0000320193", "10-K", LocalDate.of(2024, 2, 1)));

        assertEquals(2L, port().countByFormTypeNumber("10-K"));
    }

    @Test
    void findRecentXbrlFilingsByCik_returnsXbrlRecords() {
        Filling filling = TestFixtures.createTestFilling("0000320193-24-000001", "0000320193", "10-K", LocalDate.of(2024, 1, 1));
        filling.setXBRL(true);
        port().save(filling);

        Page<Filling> page = port().findRecentXbrlFilingsByCik("0000320193", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).isXBRL());
    }
}
