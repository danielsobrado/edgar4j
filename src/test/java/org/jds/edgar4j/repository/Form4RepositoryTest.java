package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Form4Repository.
 * Uses embedded MongoDB for testing.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Form4RepositoryTest {

    @Autowired
    private Form4Repository form4Repository;

    private static final String ACCESSION_1 = "0001234567-24-000001";
    private static final String ACCESSION_2 = "0001234567-24-000002";
    private static final String ACCESSION_3 = "0001234567-24-000003";

    @BeforeEach
    void setUp() {
        form4Repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        form4Repository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Should save and find Form4 by accession number")
    void shouldSaveAndFindByAccessionNumber() {
        Form4 form4 = createForm4(ACCESSION_1, "MSFT", "789019", "John Doe");
        form4Repository.save(form4);

        Optional<Form4> found = form4Repository.findByAccessionNumber(ACCESSION_1);

        assertTrue(found.isPresent());
        assertEquals(ACCESSION_1, found.get().getAccessionNumber());
        assertEquals("MSFT", found.get().getTradingSymbol());
        assertEquals("MICROSOFT CORP", found.get().getIssuerName());
    }

    @Test
    @Order(2)
    @DisplayName("Should find Form4 by trading symbol")
    void shouldFindByTradingSymbol() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));
        form4Repository.save(createForm4(ACCESSION_2, "MSFT", "789019", "Jane Smith"));
        form4Repository.save(createForm4(ACCESSION_3, "AAPL", "320193", "Tim Cook"));

        List<Form4> msftFilings = form4Repository.findByTradingSymbol("MSFT");

        assertEquals(2, msftFilings.size());
        assertTrue(msftFilings.stream().allMatch(f -> "MSFT".equals(f.getTradingSymbol())));
    }

    @Test
    @Order(3)
    @DisplayName("Should find Form4 by trading symbol with pagination")
    void shouldFindByTradingSymbolPaginated() {
        for (int i = 0; i < 25; i++) {
            form4Repository.save(createForm4(
                    String.format("0001234567-24-%06d", i),
                    "MSFT",
                    "789019",
                    "Owner " + i
            ));
        }

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<Form4> page = form4Repository.findByTradingSymbol("MSFT", pageRequest);

        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(10, page.getContent().size());
    }

    @Test
    @Order(4)
    @DisplayName("Should find Form4 by CIK")
    void shouldFindByCik() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));
        form4Repository.save(createForm4(ACCESSION_2, "AAPL", "320193", "Tim Cook"));

        List<Form4> msftFilings = form4Repository.findByCik("789019");

        assertEquals(1, msftFilings.size());
        assertEquals("789019", msftFilings.get(0).getCik());
    }

    @Test
    @Order(5)
    @DisplayName("Should find Form4 by owner name (case insensitive)")
    void shouldFindByOwnerNameContaining() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));
        form4Repository.save(createForm4(ACCESSION_2, "MSFT", "789019", "Jane Doe"));
        form4Repository.save(createForm4(ACCESSION_3, "AAPL", "320193", "Tim Cook"));

        List<Form4> doeFilings = form4Repository.findByRptOwnerNameContainingIgnoreCase("doe");

        assertEquals(2, doeFilings.size());
        assertTrue(doeFilings.stream().allMatch(f -> f.getRptOwnerName().toLowerCase().contains("doe")));
    }

    @Test
    @Order(6)
    @DisplayName("Should find Form4 by transaction date range")
    void shouldFindByTransactionDateBetween() {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 15);
        Date midDate = cal.getTime();

        cal.set(2024, Calendar.JANUARY, 10);
        Date startDate = cal.getTime();

        cal.set(2024, Calendar.JANUARY, 20);
        Date endDate = cal.getTime();

        Form4 form4 = createForm4(ACCESSION_1, "MSFT", "789019", "John Doe");
        form4.setTransactionDate(midDate);
        form4Repository.save(form4);

        List<Form4> filings = form4Repository.findByTransactionDateBetween(startDate, endDate);

        assertEquals(1, filings.size());
    }

    @Test
    @Order(7)
    @DisplayName("Should find directors only")
    void shouldFindDirectors() {
        Form4 director = createForm4(ACCESSION_1, "MSFT", "789019", "Director Person");
        director.setDirector(true);
        director.setOfficer(false);
        form4Repository.save(director);

        Form4 officer = createForm4(ACCESSION_2, "MSFT", "789019", "Officer Person");
        officer.setDirector(false);
        officer.setOfficer(true);
        form4Repository.save(officer);

        List<Form4> directors = form4Repository.findByIsDirectorTrue();

        assertEquals(1, directors.size());
        assertTrue(directors.get(0).isDirector());
        assertEquals("Director Person", directors.get(0).getRptOwnerName());
    }

    @Test
    @Order(8)
    @DisplayName("Should find officers only")
    void shouldFindOfficers() {
        Form4 director = createForm4(ACCESSION_1, "MSFT", "789019", "Director Person");
        director.setDirector(true);
        director.setOfficer(false);
        form4Repository.save(director);

        Form4 officer = createForm4(ACCESSION_2, "MSFT", "789019", "Officer Person");
        officer.setDirector(false);
        officer.setOfficer(true);
        form4Repository.save(officer);

        List<Form4> officers = form4Repository.findByIsOfficerTrue();

        assertEquals(1, officers.size());
        assertTrue(officers.get(0).isOfficer());
        assertEquals("Officer Person", officers.get(0).getRptOwnerName());
    }

    @Test
    @Order(9)
    @DisplayName("Should find 10% owners")
    void shouldFindTenPercentOwners() {
        Form4 owner = createForm4(ACCESSION_1, "MSFT", "789019", "Big Owner");
        owner.setTenPercentOwner(true);
        form4Repository.save(owner);

        Form4 smallOwner = createForm4(ACCESSION_2, "MSFT", "789019", "Small Owner");
        smallOwner.setTenPercentOwner(false);
        form4Repository.save(smallOwner);

        List<Form4> bigOwners = form4Repository.findByIsTenPercentOwnerTrue();

        assertEquals(1, bigOwners.size());
        assertTrue(bigOwners.get(0).isTenPercentOwner());
    }

    @Test
    @Order(10)
    @DisplayName("Should find top 10 recent filings")
    void shouldFindTop10Recent() {
        for (int i = 0; i < 15; i++) {
            Form4 form4 = createForm4(
                    String.format("0001234567-24-%06d", i),
                    "MSFT",
                    "789019",
                    "Owner " + i
            );
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -i);
            form4.setTransactionDate(cal.getTime());
            form4Repository.save(form4);
        }

        List<Form4> recent = form4Repository.findTop10ByOrderByTransactionDateDesc();

        assertEquals(10, recent.size());
        // Verify descending order
        for (int i = 0; i < recent.size() - 1; i++) {
            assertTrue(recent.get(i).getTransactionDate().compareTo(recent.get(i + 1).getTransactionDate()) >= 0);
        }
    }

    @Test
    @Order(11)
    @DisplayName("Should count buys and sells")
    void shouldCountBuysAndSells() {
        Form4 buy1 = createForm4(ACCESSION_1, "MSFT", "789019", "Buyer 1");
        buy1.setAcquiredDisposedCode("A");
        form4Repository.save(buy1);

        Form4 buy2 = createForm4(ACCESSION_2, "MSFT", "789019", "Buyer 2");
        buy2.setAcquiredDisposedCode("A");
        form4Repository.save(buy2);

        Form4 sell = createForm4(ACCESSION_3, "MSFT", "789019", "Seller 1");
        sell.setAcquiredDisposedCode("D");
        form4Repository.save(sell);

        assertEquals(2, form4Repository.countBuys());
        assertEquals(1, form4Repository.countSells());
    }

    @Test
    @Order(12)
    @DisplayName("Should count buys and sells by symbol")
    void shouldCountBuysAndSellsBySymbol() {
        Form4 msftBuy = createForm4(ACCESSION_1, "MSFT", "789019", "MSFT Buyer");
        msftBuy.setAcquiredDisposedCode("A");
        form4Repository.save(msftBuy);

        Form4 msftSell = createForm4(ACCESSION_2, "MSFT", "789019", "MSFT Seller");
        msftSell.setAcquiredDisposedCode("D");
        form4Repository.save(msftSell);

        Form4 aaplBuy = createForm4(ACCESSION_3, "AAPL", "320193", "AAPL Buyer");
        aaplBuy.setAcquiredDisposedCode("A");
        form4Repository.save(aaplBuy);

        assertEquals(1, form4Repository.countBuysBySymbol("MSFT"));
        assertEquals(1, form4Repository.countSellsBySymbol("MSFT"));
        assertEquals(1, form4Repository.countBuysBySymbol("AAPL"));
        assertEquals(0, form4Repository.countSellsBySymbol("AAPL"));
    }

    @Test
    @Order(13)
    @DisplayName("Should check existence by accession number")
    void shouldCheckExistenceByAccessionNumber() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));

        assertTrue(form4Repository.existsByAccessionNumber(ACCESSION_1));
        assertFalse(form4Repository.existsByAccessionNumber("nonexistent"));
    }

    @Test
    @Order(14)
    @DisplayName("Should find large transactions by symbol")
    void shouldFindLargeTransactionsBySymbol() {
        Form4 large = createForm4(ACCESSION_1, "MSFT", "789019", "Big Trader");
        large.setTransactionValue(1000000f);
        form4Repository.save(large);

        Form4 small = createForm4(ACCESSION_2, "MSFT", "789019", "Small Trader");
        small.setTransactionValue(10000f);
        form4Repository.save(small);

        List<Form4> largeTransactions = form4Repository.findLargeTransactionsBySymbol("MSFT", 500000f);

        assertEquals(1, largeTransactions.size());
        assertEquals("Big Trader", largeTransactions.get(0).getRptOwnerName());
    }

    @Test
    @Order(15)
    @DisplayName("Should find by symbol and owner name")
    void shouldFindBySymbolAndOwnerName() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));
        form4Repository.save(createForm4(ACCESSION_2, "MSFT", "789019", "Jane Smith"));
        form4Repository.save(createForm4(ACCESSION_3, "AAPL", "320193", "John Apple"));

        List<Form4> results = form4Repository.findBySymbolAndOwnerName("MSFT", "John");

        assertEquals(1, results.size());
        assertEquals("John Doe", results.get(0).getRptOwnerName());
    }

    @Test
    @Order(16)
    @DisplayName("Should enforce unique accession number constraint")
    void shouldEnforceUniqueAccessionNumber() {
        form4Repository.save(createForm4(ACCESSION_1, "MSFT", "789019", "John Doe"));

        Form4 duplicate = createForm4(ACCESSION_1, "AAPL", "320193", "Different Person");

        assertThrows(Exception.class, () -> form4Repository.save(duplicate));
    }

    @Test
    @Order(17)
    @DisplayName("Should save Form4 with transactions")
    void shouldSaveWithTransactions() {
        Form4 form4 = createForm4(ACCESSION_1, "MSFT", "789019", "John Doe");

        List<Form4Transaction> transactions = new ArrayList<>();
        Form4Transaction tx1 = Form4Transaction.builder()
                .accessionNumber(ACCESSION_1)
                .transactionType("NON_DERIVATIVE")
                .securityTitle("Common Stock")
                .transactionCode("S")
                .transactionShares(1000f)
                .transactionPricePerShare(400f)
                .transactionValue(400000f)
                .acquiredDisposedCode("D")
                .build();
        transactions.add(tx1);

        Form4Transaction tx2 = Form4Transaction.builder()
                .accessionNumber(ACCESSION_1)
                .transactionType("DERIVATIVE")
                .securityTitle("Stock Option")
                .transactionCode("M")
                .transactionShares(500f)
                .exercisePrice(350f)
                .acquiredDisposedCode("D")
                .build();
        transactions.add(tx2);

        form4.setTransactions(transactions);
        form4Repository.save(form4);

        Optional<Form4> found = form4Repository.findByAccessionNumber(ACCESSION_1);
        assertTrue(found.isPresent());
        assertNotNull(found.get().getTransactions());
        assertEquals(2, found.get().getTransactions().size());
    }

    private Form4 createForm4(String accessionNumber, String symbol, String cik, String ownerName) {
        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik(cik)
                .issuerName(symbol.equals("MSFT") ? "MICROSOFT CORP" : "APPLE INC")
                .tradingSymbol(symbol)
                .rptOwnerCik("000" + ownerName.hashCode())
                .rptOwnerName(ownerName)
                .isDirector(false)
                .isOfficer(true)
                .isTenPercentOwner(false)
                .isOther(false)
                .ownerType("Officer")
                .officerTitle("CFO")
                .transactionDate(new Date())
                .transactionShares(1000f)
                .transactionPricePerShare(100f)
                .transactionValue(100000f)
                .acquiredDisposedCode("A")
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }
}
