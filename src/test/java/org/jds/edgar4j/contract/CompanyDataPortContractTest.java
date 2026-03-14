package org.jds.edgar4j.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.port.CompanyDataPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

abstract class CompanyDataPortContractTest {

    protected abstract CompanyDataPort port();

    @Test
    void saveAndFind_roundTrip() {
        Company company = TestFixtures.createTestCompany("0000320193", "AAPL");

        port().save(company);

        Company found = port().findById(company.getId()).orElseThrow();
        assertEquals("AAPL", found.getTicker());
    }

    @Test
    void findAll_supportsPaginationAndSorting() {
        port().saveAll(List.of(
                TestFixtures.createTestCompany("0000320193", "AAPL"),
                TestFixtures.createTestCompany("0000789019", "MSFT"),
                TestFixtures.createTestCompany("0001652044", "GOOG")));

        Page<Company> page = port().findAll(PageRequest.of(0, 2, Sort.by("ticker")));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void findAllById_returnsRequestedCompanies() {
        Company first = TestFixtures.createTestCompany("0000320193", "AAPL");
        Company second = TestFixtures.createTestCompany("0000789019", "MSFT");
        port().saveAll(List.of(first, second));

        List<Company> companies = port().findAllById(List.of(first.getId(), second.getId()));

        assertEquals(2, companies.size());
    }

    @Test
    void existsById_reflectsStoredEntity() {
        Company company = TestFixtures.createTestCompany("0000320193", "AAPL");
        port().save(company);

        assertTrue(port().existsById(company.getId()));
        assertFalse(port().existsById("missing-company"));
    }

    @Test
    void deleteAllById_removesEntities() {
        Company first = TestFixtures.createTestCompany("0000320193", "AAPL");
        Company second = TestFixtures.createTestCompany("0000789019", "MSFT");
        port().saveAll(List.of(first, second));

        port().deleteAllById(List.of(first.getId()));

        assertEquals(1, port().count());
        assertTrue(port().findById(first.getId()).isEmpty());
    }
}
