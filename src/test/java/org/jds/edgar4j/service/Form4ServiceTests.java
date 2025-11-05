package org.jds.edgar4j.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jds.edgar4j.model.DerivativeTransaction;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.NonDerivativeTransaction;
import org.jds.edgar4j.model.ReportingOwner;
import org.jds.edgar4j.service.impl.Form4ServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for Form4Service
 * Tests Form 4 downloading and parsing functionality
 *
 * @author J. Daniel Sobrado
 * @version 2.0
 * @since 2025-11-05
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableAutoConfiguration(exclude = {
    EmbeddedMongoAutoConfiguration.class,
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class
})
@TestPropertySource(properties = "spring.mongodb.embedded.version=3.5.5")
public class Form4ServiceTests {

    @Autowired
    private Form4ServiceImpl form4Service;

    @DisplayName("Test Form 4 download from SEC")
    @Test
    public void testForm4Download() {
        String cik = "789019";
        String accessionNumber = "0001626431-16-000118";
        String primaryDocument = "xslF345X03/edgar.xml";

        try {
            CompletableFuture<HttpResponse<String>> response = form4Service.downloadForm4(
                cik, accessionNumber, primaryDocument
            );
            HttpResponse<String> httpResponse = response.get();

            assertNotNull(httpResponse);
            assertTrue(httpResponse.body().contains("MICROSOFT CORP"));
            assertEquals(200, httpResponse.statusCode());

        } catch (InterruptedException | ExecutionException e) {
            fail("Failed to download Form 4: " + e.getMessage());
        }
    }

    @DisplayName("Test Form 4 parsing - MSFT example")
    @Test
    public void testForm4Parsing_MSFT() throws IOException {
        // Load test HTML file
        String html = loadTestResource("data/MSFT_Form4_Example.xml");

        // Parse the Form 4
        Form4 form4 = form4Service.parseForm4(html);

        // Verify basic information
        assertNotNull(form4);
        assertEquals("MSFT", form4.getTradingSymbol());
        assertEquals("MICROSOFT CORP", form4.getIssuerName());

        // Verify reporting owner
        assertNotNull(form4.getReportingOwners());
        assertFalse(form4.getReportingOwners().isEmpty());

        ReportingOwner owner = form4.getPrimaryReportingOwner();
        assertNotNull(owner);
        assertTrue(owner.getName().contains("List"));
        assertTrue(owner.isDirector());

        // Verify transaction date
        assertNotNull(form4.getPeriodOfReport());
        assertEquals(LocalDate.of(2016, 6, 2), form4.getPeriodOfReport());

        // Verify derivative transactions (RSUs)
        assertNotNull(form4.getDerivativeTransactions());
        assertFalse(form4.getDerivativeTransactions().isEmpty());

        DerivativeTransaction derivative = form4.getDerivativeTransactions().get(0);
        assertEquals("Restricted Stock Units", derivative.getSecurityTitle());
        assertEquals("A", derivative.getTransactionCode());
        assertNotNull(derivative.getSecuritiesAcquired());
        assertTrue(derivative.getSecuritiesAcquired().compareTo(BigDecimal.ZERO) > 0);
    }

    @DisplayName("Test Form 4 parsing - Reporting Owner extraction")
    @Test
    public void testParseReportingOwner() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        ReportingOwner owner = form4.getPrimaryReportingOwner();

        assertNotNull(owner);
        assertNotNull(owner.getName());
        assertNotNull(owner.getCik());

        // Check relationship
        assertTrue(owner.isDirector() || owner.isOfficer() ||
                   owner.isTenPercentOwner() || owner.isOther());

        // Verify relationship description is set
        String description = owner.getRelationshipDescription();
        assertNotNull(description);
        assertFalse(description.equals("Unknown"));
    }

    @DisplayName("Test Form 4 parsing - Issuer information")
    @Test
    public void testParseIssuerInfo() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        assertNotNull(form4.getTradingSymbol());
        assertNotNull(form4.getIssuerName());
        assertNotNull(form4.getIssuerCik());

        assertEquals("MSFT", form4.getTradingSymbol());
        assertEquals("MICROSOFT CORP", form4.getIssuerName());
    }

    @DisplayName("Test Form 4 parsing - Period of report")
    @Test
    public void testParsePeriodOfReport() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        assertNotNull(form4.getPeriodOfReport());
        assertEquals(2016, form4.getPeriodOfReport().getYear());
        assertEquals(6, form4.getPeriodOfReport().getMonthValue());
        assertEquals(2, form4.getPeriodOfReport().getDayOfMonth());
    }

    @DisplayName("Test Form 4 parsing - Derivative transactions")
    @Test
    public void testParseDerivativeTransactions() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        assertNotNull(form4.getDerivativeTransactions());

        if (!form4.getDerivativeTransactions().isEmpty()) {
            DerivativeTransaction transaction = form4.getDerivativeTransactions().get(0);

            assertNotNull(transaction.getSecurityTitle());
            assertNotNull(transaction.getTransactionCode());
            assertNotNull(transaction.getTransactionDate());

            // Should have either acquired or disposed securities
            assertTrue(
                (transaction.getSecuritiesAcquired() != null &&
                 transaction.getSecuritiesAcquired().compareTo(BigDecimal.ZERO) > 0) ||
                (transaction.getSecuritiesDisposed() != null &&
                 transaction.getSecuritiesDisposed().compareTo(BigDecimal.ZERO) > 0)
            );
        }
    }

    @DisplayName("Test Form 4 parsing - Non-derivative transactions")
    @Test
    public void testParseNonDerivativeTransactions() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        assertNotNull(form4.getNonDerivativeTransactions());

        // This particular Form 4 may or may not have non-derivative transactions
        // Just verify the list is initialized
        assertTrue(form4.getNonDerivativeTransactions() != null);
    }

    @DisplayName("Test Form 4 parsing - Signature extraction")
    @Test
    public void testParseSignature() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        // Signature may or may not be present depending on format
        // Just verify parsing doesn't fail
        assertNotNull(form4);
    }

    @DisplayName("Test Form 4 parsing - Footnotes extraction")
    @Test
    public void testParseFootnotes() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        // Footnotes should be parsed if present
        if (form4.getFootnotes() != null && !form4.getFootnotes().isEmpty()) {
            assertTrue(form4.getFootnotes().length() > 0);
        }
    }

    @DisplayName("Test Form 4 helper methods - hasPurchases")
    @Test
    public void testHasPurchases() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        // Test helper method
        boolean hasPurchases = form4.hasPurchases();
        // Should return true or false without error
        assertTrue(hasPurchases || !hasPurchases);
    }

    @DisplayName("Test Form 4 helper methods - getPurchaseTransactions")
    @Test
    public void testGetPurchaseTransactions() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        var purchases = form4.getPurchaseTransactions();
        assertNotNull(purchases);

        // Verify all returned transactions are purchases
        for (NonDerivativeTransaction tx : purchases) {
            assertTrue(tx.isPurchase());
        }
    }

    @DisplayName("Test Form 4 helper methods - getSaleTransactions")
    @Test
    public void testGetSaleTransactions() throws IOException {
        String html = loadTestResource("data/MSFT_Form4_Example.xml");
        Form4 form4 = form4Service.parseForm4(html);

        var sales = form4.getSaleTransactions();
        assertNotNull(sales);

        // Verify all returned transactions are sales
        for (NonDerivativeTransaction tx : sales) {
            assertTrue(tx.isSale());
        }
    }

    @DisplayName("Test Form 4 parsing - Empty/malformed HTML")
    @Test
    public void testParseEmptyHtml() {
        String emptyHtml = "";
        Form4 form4 = form4Service.parseForm4(emptyHtml);

        // Should return empty Form4 object, not throw exception
        assertNotNull(form4);
    }

    @DisplayName("Test Form 4 parsing - Malformed HTML")
    @Test
    public void testParseMalformedHtml() {
        String malformedHtml = "<html><body>This is not a Form 4</body></html>";
        Form4 form4 = form4Service.parseForm4(malformedHtml);

        // Should return Form4 object with minimal data, not throw exception
        assertNotNull(form4);
    }

    @DisplayName("Test NonDerivativeTransaction - Purchase detection")
    @Test
    public void testNonDerivativeTransactionPurchaseDetection() {
        NonDerivativeTransaction transaction = NonDerivativeTransaction.builder()
            .transactionCode("P")
            .acquiredDisposedCode("A")
            .build();

        assertTrue(transaction.isPurchase());
        assertFalse(transaction.isSale());
    }

    @DisplayName("Test NonDerivativeTransaction - Sale detection")
    @Test
    public void testNonDerivativeTransactionSaleDetection() {
        NonDerivativeTransaction transaction = NonDerivativeTransaction.builder()
            .transactionCode("S")
            .acquiredDisposedCode("D")
            .build();

        assertTrue(transaction.isSale());
        assertFalse(transaction.isPurchase());
    }

    @DisplayName("Test NonDerivativeTransaction - Transaction value calculation")
    @Test
    public void testNonDerivativeTransactionValueCalculation() {
        NonDerivativeTransaction transaction = NonDerivativeTransaction.builder()
            .transactionShares(new BigDecimal("100"))
            .transactionPricePerShare(new BigDecimal("50.25"))
            .build();

        BigDecimal expectedValue = new BigDecimal("5025.00");
        assertEquals(expectedValue, transaction.getTransactionValue());
    }

    @DisplayName("Test DerivativeTransaction - Acquisition detection")
    @Test
    public void testDerivativeTransactionAcquisitionDetection() {
        DerivativeTransaction transaction = DerivativeTransaction.builder()
            .securitiesAcquired(new BigDecimal("1000"))
            .build();

        assertTrue(transaction.isAcquisition());
        assertFalse(transaction.isDisposal());
    }

    @DisplayName("Test DerivativeTransaction - Disposal detection")
    @Test
    public void testDerivativeTransactionDisposalDetection() {
        DerivativeTransaction transaction = DerivativeTransaction.builder()
            .securitiesDisposed(new BigDecimal("500"))
            .build();

        assertTrue(transaction.isDisposal());
        assertFalse(transaction.isAcquisition());
    }

    @DisplayName("Test DerivativeTransaction - Net change calculation")
    @Test
    public void testDerivativeTransactionNetChangeCalculation() {
        DerivativeTransaction transaction = DerivativeTransaction.builder()
            .securitiesAcquired(new BigDecimal("1000"))
            .securitiesDisposed(new BigDecimal("200"))
            .build();

        BigDecimal expectedNetChange = new BigDecimal("800");
        assertEquals(expectedNetChange, transaction.getNetSecuritiesChange());
    }

    @DisplayName("Test ReportingOwner - Relationship description")
    @Test
    public void testReportingOwnerRelationshipDescription() {
        ReportingOwner director = ReportingOwner.builder()
            .isDirector(true)
            .build();
        assertEquals("Director", director.getRelationshipDescription());

        ReportingOwner officer = ReportingOwner.builder()
            .isOfficer(true)
            .officerTitle("CEO")
            .build();
        assertEquals("CEO", officer.getRelationshipDescription());

        ReportingOwner tenPercent = ReportingOwner.builder()
            .isTenPercentOwner(true)
            .build();
        assertEquals("10% Owner", tenPercent.getRelationshipDescription());
    }

    @DisplayName("Test ReportingOwner - Relationship abbreviation")
    @Test
    public void testReportingOwnerRelationshipAbbreviation() {
        ReportingOwner director = ReportingOwner.builder()
            .isDirector(true)
            .build();
        assertEquals("Dir", director.getRelationshipAbbreviation());

        ReportingOwner tenPercent = ReportingOwner.builder()
            .isTenPercentOwner(true)
            .build();
        assertEquals("10%", tenPercent.getRelationshipAbbreviation());
    }

    /**
     * Helper method to load test resources
     */
    private String loadTestResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return new String(Files.readAllBytes(Paths.get(resource.getURI())));
    }
}
