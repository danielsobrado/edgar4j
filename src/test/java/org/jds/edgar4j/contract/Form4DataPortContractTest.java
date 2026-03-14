package org.jds.edgar4j.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.port.Form4DataPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

abstract class Form4DataPortContractTest {

    protected abstract Form4DataPort port();

    @Test
    void saveAndFind_roundTrip() {
        Form4 form4 = TestFixtures.createTestForm4("0001234567-24-000001", "AAPL");

        port().save(form4);

        Form4 found = port().findByAccessionNumber(form4.getAccessionNumber()).orElseThrow();
        assertEquals("AAPL", found.getTradingSymbol());
        assertEquals(form4.getAccessionNumber(), found.getAccessionNumber());
    }

    @Test
    void findByAccessionNumberIn_returnsMatchingRecords() {
        Form4 first = TestFixtures.createTestForm4("0001234567-24-000001", "AAPL");
        Form4 second = TestFixtures.createTestForm4("0001234567-24-000002", "MSFT");
        port().saveAll(List.of(first, second));

        List<Form4> results = port().findByAccessionNumberIn(List.of(first.getAccessionNumber(), second.getAccessionNumber()));

        assertEquals(2, results.size());
    }

    @Test
    void findByTradingSymbol_supportsPagination() {
        for (int index = 0; index < 12; index++) {
            port().save(TestFixtures.createTestForm4("0001234567-24-1000" + index, "MSFT", LocalDate.of(2024, 1, 1).plusDays(index)));
        }

        Page<Form4> page = port().findByTradingSymbol("MSFT", PageRequest.of(0, 5, Sort.by("transactionDate")));

        assertEquals(12, page.getTotalElements());
        assertEquals(5, page.getContent().size());
        assertEquals(3, page.getTotalPages());
    }

    @Test
    void findByCik_supportsPagination() {
        for (int index = 0; index < 7; index++) {
            Form4 form4 = TestFixtures.createTestForm4("0001234567-24-2000" + index, "AAPL");
            form4.setCik("0000789019");
            port().save(form4);
        }

        Page<Form4> page = port().findByCik("0000789019", PageRequest.of(1, 3));

        assertEquals(7, page.getTotalElements());
        assertEquals(3, page.getContent().size());
    }

    @Test
    void findByOwnerNameContainingIgnoreCase_matchesSubstring() {
        Form4 first = TestFixtures.createTestForm4("0001234567-24-300001", "AAPL");
        first.setRptOwnerName("Jane Doe");
        Form4 second = TestFixtures.createTestForm4("0001234567-24-300002", "AAPL");
        second.setRptOwnerName("John Smith");
        port().saveAll(List.of(first, second));

        List<Form4> results = port().findByRptOwnerNameContainingIgnoreCase("doe");

        assertEquals(1, results.size());
        assertEquals("Jane Doe", results.get(0).getRptOwnerName());
    }

    @Test
    void findByTransactionDateBetween_filtersDateRange() {
        port().save(TestFixtures.createTestForm4("0001234567-24-400001", "AAPL", LocalDate.of(2024, 1, 5)));
        port().save(TestFixtures.createTestForm4("0001234567-24-400002", "AAPL", LocalDate.of(2024, 1, 10)));
        port().save(TestFixtures.createTestForm4("0001234567-24-400003", "AAPL", LocalDate.of(2024, 2, 1)));

        Page<Form4> page = port().findByTransactionDateBetween(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    void findBySymbolAndDateRange_combinesFilters() {
        port().save(TestFixtures.createTestForm4("0001234567-24-500001", "AAPL", LocalDate.of(2024, 1, 5)));
        port().save(TestFixtures.createTestForm4("0001234567-24-500002", "AAPL", LocalDate.of(2024, 2, 5)));
        port().save(TestFixtures.createTestForm4("0001234567-24-500003", "MSFT", LocalDate.of(2024, 1, 5)));

        Page<Form4> page = port().findBySymbolAndDateRange(
                "AAPL",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("AAPL", page.getContent().get(0).getTradingSymbol());
    }

    @Test
    void findByTradingSymbolAndTransactionDateBetween_returnsList() {
        port().save(TestFixtures.createTestForm4("0001234567-24-600001", "MSFT", LocalDate.of(2024, 3, 5)));
        port().save(TestFixtures.createTestForm4("0001234567-24-600002", "MSFT", LocalDate.of(2024, 3, 10)));
        port().save(TestFixtures.createTestForm4("0001234567-24-600003", "MSFT", LocalDate.of(2024, 4, 1)));

        List<Form4> results = port().findByTradingSymbolAndTransactionDateBetween(
                "MSFT",
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31));

        assertEquals(2, results.size());
    }

    @Test
    void findRecentAcquisitions_includesNestedPurchases() {
        Form4 nestedPurchase = TestFixtures.createTestForm4("0001234567-24-700001", "TSLA", LocalDate.of(2024, 1, 1));
        nestedPurchase.setAcquiredDisposedCode("D");
        nestedPurchase.setTransactions(List.of(Form4Transaction.builder()
                .accessionNumber(nestedPurchase.getAccessionNumber())
                .transactionCode("P")
                .transactionDate(LocalDate.of(2024, 2, 1))
                .acquiredDisposedCode("A")
                .transactionValue(1000f)
                .build()));
        port().save(nestedPurchase);

        List<Form4> acquisitions = port().findRecentAcquisitions(LocalDate.of(2024, 1, 15));

        assertEquals(1, acquisitions.size());
        assertEquals("TSLA", acquisitions.get(0).getTradingSymbol());
    }

    @Test
    void existsByAccessionNumber_reflectsPresence() {
        Form4 form4 = TestFixtures.createTestForm4("0001234567-24-800001", "AAPL");
        port().save(form4);

        assertTrue(port().existsByAccessionNumber(form4.getAccessionNumber()));
        assertFalse(port().existsByAccessionNumber("missing-accession"));
    }

    @Test
    void deleteById_removesStoredRecord() {
        Form4 form4 = TestFixtures.createTestForm4("0001234567-24-900001", "AAPL");
        Form4 saved = port().save(form4);

        port().deleteById(saved.getId());

        assertTrue(port().findByAccessionNumber(saved.getAccessionNumber()).isEmpty());
    }
}
