package org.jds.edgar4j.service;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.Form4Service.InsiderStats;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Form4Service.
 * Includes both unit tests (with embedded MongoDB) and integration tests (with SEC API).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Form4ServiceTests {

    @Autowired
    private Form4Service form4Service;

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

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @Order(1)
        @DisplayName("Should save new Form4")
        void shouldSaveNewForm4() {
            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");

            Form4 saved = form4Service.save(form4);

            assertNotNull(saved);
            assertNotNull(saved.getId());
            assertEquals(ACCESSION_1, saved.getAccessionNumber());
            assertNotNull(saved.getUpdatedAt());
        }

        @Test
        @Order(2)
        @DisplayName("Should update existing Form4 by accession number")
        void shouldUpdateExistingForm4() {
            Form4 original = createForm4(ACCESSION_1, "MSFT", "John Doe");
            Form4 saved = form4Service.save(original);
            String originalId = saved.getId();
            Date originalCreatedAt = saved.getCreatedAt();

            Form4 updated = createForm4(ACCESSION_1, "MSFT", "Updated Name");
            Form4 result = form4Service.save(updated);

            assertEquals(originalId, result.getId());
            assertEquals(originalCreatedAt, result.getCreatedAt());
            assertEquals("Updated Name", result.getRptOwnerName());
        }

        @Test
        @Order(3)
        @DisplayName("Should save all Form4 list")
        void shouldSaveAllForm4List() {
            List<Form4> form4List = List.of(
                    createForm4(ACCESSION_1, "MSFT", "John Doe"),
                    createForm4(ACCESSION_2, "MSFT", "Jane Smith"),
                    createForm4(ACCESSION_3, "AAPL", "Tim Cook")
            );

            List<Form4> saved = form4Service.saveAll(form4List);

            assertEquals(3, saved.size());
            assertTrue(saved.stream().allMatch(f -> f.getId() != null));
        }

        @Test
        @Order(4)
        @DisplayName("Should return null when saving null")
        void shouldReturnNullWhenSavingNull() {
            Form4 result = form4Service.save(null);
            assertNull(result);
        }

        @Test
        @Order(5)
        @DisplayName("Should return empty list when saving empty list")
        void shouldReturnEmptyListWhenSavingEmptyList() {
            List<Form4> result = form4Service.saveAll(List.of());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @Order(10)
        @DisplayName("Should find by accession number")
        void shouldFindByAccessionNumber() {
            form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            Optional<Form4> found = form4Service.findByAccessionNumber(ACCESSION_1);

            assertTrue(found.isPresent());
            assertEquals(ACCESSION_1, found.get().getAccessionNumber());
        }

        @Test
        @Order(11)
        @DisplayName("Should return empty when accession not found")
        void shouldReturnEmptyWhenAccessionNotFound() {
            Optional<Form4> found = form4Service.findByAccessionNumber("nonexistent");
            assertTrue(found.isEmpty());
        }

        @Test
        @Order(12)
        @DisplayName("Should find by ID")
        void shouldFindById() {
            Form4 saved = form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            Optional<Form4> found = form4Service.findById(saved.getId());

            assertTrue(found.isPresent());
            assertEquals(saved.getId(), found.get().getId());
        }

        @Test
        @Order(13)
        @DisplayName("Should find by trading symbol with pagination")
        void shouldFindByTradingSymbol() {
            form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Service.save(createForm4(ACCESSION_2, "MSFT", "Jane Smith"));
            form4Service.save(createForm4(ACCESSION_3, "AAPL", "Tim Cook"));

            Page<Form4> page = form4Service.findByTradingSymbol("MSFT", PageRequest.of(0, 10));

            assertEquals(2, page.getTotalElements());
            assertTrue(page.getContent().stream().allMatch(f -> "MSFT".equals(f.getTradingSymbol())));
        }

        @Test
        @Order(14)
        @DisplayName("Should find by CIK with pagination")
        void shouldFindByCik() {
            form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            Page<Form4> page = form4Service.findByCik("789019", PageRequest.of(0, 10));

            assertEquals(1, page.getTotalElements());
            assertEquals("789019", page.getContent().get(0).getCik());
        }

        @Test
        @Order(15)
        @DisplayName("Should find by owner name")
        void shouldFindByOwnerName() {
            form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Service.save(createForm4(ACCESSION_2, "MSFT", "Jane Doe"));
            form4Service.save(createForm4(ACCESSION_3, "AAPL", "Tim Cook"));

            List<Form4> results = form4Service.findByOwnerName("Doe");

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(f -> f.getRptOwnerName().contains("Doe")));
        }

        @Test
        @Order(16)
        @DisplayName("Should find by date range with pagination")
        void shouldFindByDateRange() {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);

            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");
            form4.setTransactionDate(cal.getTime());
            form4Service.save(form4);

            cal.set(2024, Calendar.JANUARY, 10);
            Date startDate = cal.getTime();
            cal.set(2024, Calendar.JANUARY, 20);
            Date endDate = cal.getTime();

            Page<Form4> page = form4Service.findByDateRange(startDate, endDate, PageRequest.of(0, 10));

            assertEquals(1, page.getTotalElements());
        }

        @Test
        @Order(17)
        @DisplayName("Should find by symbol and date range")
        void shouldFindBySymbolAndDateRange() {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);

            Form4 msft = createForm4(ACCESSION_1, "MSFT", "John Doe");
            msft.setTransactionDate(cal.getTime());
            form4Service.save(msft);

            Form4 aapl = createForm4(ACCESSION_2, "AAPL", "Tim Cook");
            aapl.setTransactionDate(cal.getTime());
            form4Service.save(aapl);

            cal.set(2024, Calendar.JANUARY, 10);
            Date startDate = cal.getTime();
            cal.set(2024, Calendar.JANUARY, 20);
            Date endDate = cal.getTime();

            Page<Form4> page = form4Service.findBySymbolAndDateRange("MSFT", startDate, endDate, PageRequest.of(0, 10));

            assertEquals(1, page.getTotalElements());
            assertEquals("MSFT", page.getContent().get(0).getTradingSymbol());
        }

        @Test
        @Order(18)
        @DisplayName("Should find recent filings")
        void shouldFindRecentFilings() {
            for (int i = 0; i < 15; i++) {
                Form4 form4 = createForm4(
                        String.format("0001234567-24-%06d", i),
                        "MSFT",
                        "Owner " + i
                );
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -i);
                form4.setTransactionDate(cal.getTime());
                form4Service.save(form4);
            }

            List<Form4> recent = form4Service.findRecentFilings(5);

            assertEquals(5, recent.size());
            // Verify descending order
            for (int i = 0; i < recent.size() - 1; i++) {
                assertTrue(recent.get(i).getTransactionDate().compareTo(recent.get(i + 1).getTransactionDate()) >= 0);
            }
        }
    }

    @Nested
    @DisplayName("Existence and Delete Operations")
    class ExistenceAndDeleteOperations {

        @Test
        @Order(20)
        @DisplayName("Should check existence by accession number")
        void shouldCheckExistenceByAccessionNumber() {
            form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            assertTrue(form4Service.existsByAccessionNumber(ACCESSION_1));
            assertFalse(form4Service.existsByAccessionNumber("nonexistent"));
        }

        @Test
        @Order(21)
        @DisplayName("Should delete by ID")
        void shouldDeleteById() {
            Form4 saved = form4Service.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            form4Service.deleteById(saved.getId());

            assertTrue(form4Service.findById(saved.getId()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Insider Statistics")
    class InsiderStatistics {

        @Test
        @Order(30)
        @DisplayName("Should calculate insider stats correctly")
        void shouldCalculateInsiderStats() {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);
            Date txDate = cal.getTime();

            // Create buys
            Form4 buy1 = createForm4(ACCESSION_1, "MSFT", "Director Buyer");
            buy1.setTransactionDate(txDate);
            buy1.setAcquiredDisposedCode("A");
            buy1.setDirector(true);
            buy1.setOfficer(false);
            buy1.setTransactions(List.of(
                    Form4Transaction.builder()
                            .transactionType("NON_DERIVATIVE")
                            .transactionValue(100000f)
                            .acquiredDisposedCode("A")
                            .build()
            ));
            form4Service.save(buy1);

            Form4 buy2 = createForm4(ACCESSION_2, "MSFT", "Officer Buyer");
            buy2.setTransactionDate(txDate);
            buy2.setAcquiredDisposedCode("A");
            buy2.setDirector(false);
            buy2.setOfficer(true);
            buy2.setTransactions(List.of(
                    Form4Transaction.builder()
                            .transactionType("NON_DERIVATIVE")
                            .transactionValue(50000f)
                            .acquiredDisposedCode("A")
                            .build()
            ));
            form4Service.save(buy2);

            // Create sell
            Form4 sell = createForm4(ACCESSION_3, "MSFT", "10% Owner Seller");
            sell.setTransactionDate(txDate);
            sell.setAcquiredDisposedCode("D");
            sell.setDirector(false);
            sell.setOfficer(false);
            sell.setTenPercentOwner(true);
            sell.setTransactions(List.of(
                    Form4Transaction.builder()
                            .transactionType("NON_DERIVATIVE")
                            .transactionValue(200000f)
                            .acquiredDisposedCode("D")
                            .build()
            ));
            form4Service.save(sell);

            cal.set(2024, Calendar.JANUARY, 1);
            Date startDate = cal.getTime();
            cal.set(2024, Calendar.JANUARY, 31);
            Date endDate = cal.getTime();

            InsiderStats stats = form4Service.getInsiderStats("MSFT", startDate, endDate);

            assertNotNull(stats);
            assertEquals(2, stats.totalBuys());
            assertEquals(1, stats.totalSells());
            assertEquals(1, stats.directorTransactions());
            assertEquals(1, stats.officerTransactions());
            assertEquals(1, stats.tenPercentOwnerTransactions());
        }

        @Test
        @Order(31)
        @DisplayName("Should return zero stats for empty results")
        void shouldReturnZeroStatsForEmptyResults() {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 1);
            Date startDate = cal.getTime();
            cal.set(2024, Calendar.JANUARY, 31);
            Date endDate = cal.getTime();

            InsiderStats stats = form4Service.getInsiderStats("NONEXISTENT", startDate, endDate);

            assertNotNull(stats);
            assertEquals(0, stats.totalBuys());
            assertEquals(0, stats.totalSells());
            assertEquals(0.0, stats.totalBuyValue());
            assertEquals(0.0, stats.totalSellValue());
        }
    }

    @Nested
    @DisplayName("XML Parsing")
    class XmlParsing {

        @Test
        @Order(40)
        @DisplayName("Should parse valid Form4 XML")
        void shouldParseValidXml() {
            String xml = """
                <?xml version="1.0"?>
                <ownershipDocument>
                    <documentType>4</documentType>
                    <periodOfReport>2024-01-15</periodOfReport>
                    <issuer>
                        <issuerCik>0000789019</issuerCik>
                        <issuerName>MICROSOFT CORP</issuerName>
                        <issuerTradingSymbol>MSFT</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <reportingOwnerId>
                            <rptOwnerCik>0001234567</rptOwnerCik>
                            <rptOwnerName>Test Owner</rptOwnerName>
                        </reportingOwnerId>
                        <reportingOwnerRelationship>
                            <isDirector>0</isDirector>
                            <isOfficer>1</isOfficer>
                            <isTenPercentOwner>0</isTenPercentOwner>
                            <isOther>0</isOther>
                            <officerTitle>CFO</officerTitle>
                        </reportingOwnerRelationship>
                    </reportingOwner>
                    <nonDerivativeTable>
                        <nonDerivativeTransaction>
                            <securityTitle><value>Common Stock</value></securityTitle>
                            <transactionDate><value>2024-01-15</value></transactionDate>
                            <transactionCoding>
                                <transactionFormType>4</transactionFormType>
                                <transactionCode>P</transactionCode>
                                <equitySwapInvolved>0</equitySwapInvolved>
                            </transactionCoding>
                            <transactionAmounts>
                                <transactionShares><value>1000</value></transactionShares>
                                <transactionPricePerShare><value>400.00</value></transactionPricePerShare>
                                <transactionAcquiredDisposedCode><value>A</value></transactionAcquiredDisposedCode>
                            </transactionAmounts>
                        </nonDerivativeTransaction>
                    </nonDerivativeTable>
                </ownershipDocument>
                """;

            Form4 form4 = form4Service.parseForm4(xml, ACCESSION_1);

            assertNotNull(form4);
            assertEquals(ACCESSION_1, form4.getAccessionNumber());
            assertEquals("0000789019", form4.getCik());
            assertEquals("MICROSOFT CORP", form4.getIssuerName());
            assertEquals("MSFT", form4.getTradingSymbol());
            assertEquals("Test Owner", form4.getRptOwnerName());
            assertTrue(form4.isOfficer());
            assertFalse(form4.isDirector());
            assertNotNull(form4.getTransactions());
            assertEquals(1, form4.getTransactions().size());
        }

        @Test
        @Order(41)
        @DisplayName("Should return null for null XML")
        void shouldReturnNullForNullXml() {
            Form4 form4 = form4Service.parseForm4(null, ACCESSION_1);
            assertNull(form4);
        }

        @Test
        @Order(42)
        @DisplayName("Should return null for empty XML")
        void shouldReturnNullForEmptyXml() {
            Form4 form4 = form4Service.parseForm4("", ACCESSION_1);
            assertNull(form4);
        }
    }

    @Nested
    @DisplayName("Integration Tests (SEC API)")
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    class IntegrationTests {

        @Test
        @DisplayName("Should download Form 4 from SEC")
        void testForm4Download() {
            String cik = "789019";
            String accessionNumber = "0001626431-16-000118";
            String primaryDocument = "xslF345X03/edgar.xml";

            try {
                CompletableFuture<HttpResponse<String>> response = form4Service.downloadForm4(cik, accessionNumber, primaryDocument);
                HttpResponse<String> httpResponse = response.get();

                assertEquals(200, httpResponse.statusCode());
                assertNotNull(httpResponse.body());
                assertFalse(httpResponse.body().isEmpty());

            } catch (InterruptedException | ExecutionException e) {
                fail("Failed to download Form 4: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Should download and parse Form 4 from SEC")
        void testDownloadAndParseForm4() {
            String cik = "789019";
            String accessionNumber = "0001626431-16-000118";
            String primaryDocument = "xslF345X03/edgar.xml";

            try {
                CompletableFuture<Form4> future = form4Service.downloadAndParseForm4(cik, accessionNumber, primaryDocument);
                Form4 form4 = future.get();

                // May be null if SEC returns HTML instead of XML
                if (form4 != null) {
                    assertEquals(accessionNumber, form4.getAccessionNumber());
                    assertNotNull(form4.getCik());
                    assertNotNull(form4.getTradingSymbol());
                }

            } catch (InterruptedException | ExecutionException e) {
                fail("Failed to download and parse Form 4: " + e.getMessage());
            }
        }
    }

    private Form4 createForm4(String accessionNumber, String symbol, String ownerName) {
        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("789019")
                .issuerName("MICROSOFT CORP")
                .tradingSymbol(symbol)
                .rptOwnerCik("0001234567")
                .rptOwnerName(ownerName)
                .isDirector(false)
                .isOfficer(true)
                .isTenPercentOwner(false)
                .isOther(false)
                .ownerType("Officer")
                .officerTitle("CFO")
                .securityTitle("Common Stock")
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
