package org.jds.edgar4j.integration;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Form4Parser.
 * Tests parsing of SEC Form 4 XML documents.
 */
class Form4ParserTest {

    private Form4Parser parser;
    private static final String ACCESSION_NUMBER = "0001234567-24-000001";
    @BeforeEach
    void setUp() {
        parser = new Form4Parser();
    }

    @Nested
    @DisplayName("Parse Valid XML")
    class ParseValidXml {

        @Test
        @DisplayName("Should parse issuer information correctly")
        void shouldParseIssuerInfo() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertEquals(ACCESSION_NUMBER, form4.getAccessionNumber());
            assertEquals("0000789019", form4.getCik());
            assertEquals("MICROSOFT CORP", form4.getIssuerName());
            assertEquals("MSFT", form4.getTradingSymbol());
        }

        @Test
        @DisplayName("Should parse reporting owner information correctly")
        void shouldParseReportingOwner() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertEquals("0001234567", form4.getRptOwnerCik());
            assertEquals("Doe John A", form4.getRptOwnerName());
            assertEquals("Chief Financial Officer", form4.getOfficerTitle());
            assertTrue(form4.isOfficer());
            assertFalse(form4.isDirector());
            assertFalse(form4.isTenPercentOwner());
            assertFalse(form4.isOther());
            assertEquals("Officer", form4.getOwnerType());
        }

        @Test
        @DisplayName("Should parse non-derivative transactions correctly")
        void shouldParseNonDerivativeTransactions() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertNotNull(form4.getTransactions());

            List<Form4Transaction> nonDerivative = form4.getTransactions().stream()
                    .filter(Form4Transaction::isNonDerivative)
                    .toList();

            assertEquals(2, nonDerivative.size());

            // First transaction - Sale
            Form4Transaction sale = nonDerivative.get(0);
            assertEquals("Common Stock", sale.getSecurityTitle());
            assertEquals("S", sale.getTransactionCode());
            assertEquals(5000f, sale.getTransactionShares());
            assertEquals(400.50f, sale.getTransactionPricePerShare());
            assertEquals("D", sale.getAcquiredDisposedCode());
            assertTrue(sale.isSell());
            assertFalse(sale.isBuy());
            assertEquals("Open Market Sale", sale.getTransactionCodeDescription());

            // Second transaction - Purchase
            Form4Transaction purchase = nonDerivative.get(1);
            assertEquals("Common Stock", purchase.getSecurityTitle());
            assertEquals("P", purchase.getTransactionCode());
            assertEquals(1000f, purchase.getTransactionShares());
            assertEquals(395.25f, purchase.getTransactionPricePerShare());
            assertEquals("A", purchase.getAcquiredDisposedCode());
            assertTrue(purchase.isBuy());
            assertFalse(purchase.isSell());
            assertEquals("Open Market Purchase", purchase.getTransactionCodeDescription());
        }

        @Test
        @DisplayName("Should parse derivative transactions correctly")
        void shouldParseDerivativeTransactions() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertNotNull(form4.getTransactions());

            List<Form4Transaction> derivative = form4.getTransactions().stream()
                    .filter(Form4Transaction::isDerivative)
                    .toList();

            assertEquals(1, derivative.size());

            Form4Transaction option = derivative.get(0);
            assertEquals("Stock Option (Right to Buy)", option.getSecurityTitle());
            assertEquals("M", option.getTransactionCode());
            assertEquals(350.00f, option.getExercisePrice());
            assertEquals(2000f, option.getTransactionShares());
            assertEquals("Common Stock", option.getUnderlyingSecurityTitle());
            assertEquals(2000f, option.getUnderlyingSecurityShares());
            assertEquals("Exercise of Derivative", option.getTransactionCodeDescription());
        }

        @Test
        @DisplayName("Should calculate transaction values correctly")
        void shouldCalculateTransactionValues() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);

            List<Form4Transaction> nonDerivative = form4.getTransactions().stream()
                    .filter(Form4Transaction::isNonDerivative)
                    .toList();

            // Sale: 5000 * 400.50 = 2,002,500
            Form4Transaction sale = nonDerivative.get(0);
            assertNotNull(sale.getTransactionValue());
            assertEquals(2002500f, sale.getTransactionValue(), 0.01f);

            // Purchase: 1000 * 395.25 = 395,250
            Form4Transaction purchase = nonDerivative.get(1);
            assertNotNull(purchase.getTransactionValue());
            assertEquals(395250f, purchase.getTransactionValue(), 0.01f);
        }

        @Test
        @DisplayName("Should set primary transaction fields from first transaction")
        void shouldSetPrimaryTransactionFields() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);
            // Primary fields from first transaction
            assertEquals("Common Stock", form4.getSecurityTitle());
            assertEquals(5000f, form4.getTransactionShares());
            assertEquals(400.50f, form4.getTransactionPricePerShare());
            assertEquals("D", form4.getAcquiredDisposedCode());
        }

        @Test
        @DisplayName("Should calculate total buy and sell values")
        void shouldCalculateTotalBuySellValues() throws IOException {
            String xml = loadTestXml("data/form4-sample.xml");

            Form4 form4 = parser.parse(xml, ACCESSION_NUMBER);

            assertNotNull(form4);

            // Total sell value: 2,002,500 (from sale transaction)
            Float totalSellValue = form4.getTotalSellValue();
            assertNotNull(totalSellValue);
            assertEquals(2002500f, totalSellValue, 0.01f);

            // Total buy value: 395,250 (from purchase transaction)
            Float totalBuyValue = form4.getTotalBuyValue();
            assertNotNull(totalBuyValue);
            assertEquals(395250f, totalBuyValue, 0.01f);
        }
    }

    @Nested
    @DisplayName("Handle Edge Cases")
    class HandleEdgeCases {

        @Test
        @DisplayName("Should return null for null XML")
        void shouldReturnNullForNullXml() {
            Form4 form4 = parser.parse(null, ACCESSION_NUMBER);
            assertNull(form4);
        }

        @Test
        @DisplayName("Should return null for empty XML")
        void shouldReturnNullForEmptyXml() {
            Form4 form4 = parser.parse("", ACCESSION_NUMBER);
            assertNull(form4);
        }

        @Test
        @DisplayName("Should return null for blank XML")
        void shouldReturnNullForBlankXml() {
            Form4 form4 = parser.parse("   ", ACCESSION_NUMBER);
            assertNull(form4);
        }

        @Test
        @DisplayName("Should handle XML with missing optional fields")
        void shouldHandleMissingOptionalFields() {
            String minimalXml = """
                <?xml version="1.0"?>
                <ownershipDocument>
                    <documentType>4</documentType>
                    <issuer>
                        <issuerCik>0000789019</issuerCik>
                        <issuerName>TEST CORP</issuerName>
                        <issuerTradingSymbol>TEST</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <reportingOwnerId>
                            <rptOwnerCik>0001234567</rptOwnerCik>
                            <rptOwnerName>Test Owner</rptOwnerName>
                        </reportingOwnerId>
                    </reportingOwner>
                </ownershipDocument>
                """;

            Form4 form4 = parser.parse(minimalXml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertEquals("0000789019", form4.getCik());
            assertEquals("TEST CORP", form4.getIssuerName());
            assertEquals("TEST", form4.getTradingSymbol());
            assertEquals("Test Owner", form4.getRptOwnerName());
            // Empty transactions list
            assertTrue(form4.getTransactions() == null || form4.getTransactions().isEmpty());
        }

        @Test
        @DisplayName("Should clean HTML wrapped XML content")
        void shouldCleanHtmlWrappedXml() {
            String htmlWrappedXml = """
                <!DOCTYPE html>
                <html><body>
                <?xml version="1.0"?>
                <ownershipDocument>
                    <documentType>4</documentType>
                    <issuer>
                        <issuerCik>0000789019</issuerCik>
                        <issuerName>WRAPPED CORP</issuerName>
                        <issuerTradingSymbol>WRAP</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <reportingOwnerId>
                            <rptOwnerCik>0001234567</rptOwnerCik>
                            <rptOwnerName>Wrapped Owner</rptOwnerName>
                        </reportingOwnerId>
                    </reportingOwner>
                </ownershipDocument>
                </body></html>
                """;

            Form4 form4 = parser.parse(htmlWrappedXml, ACCESSION_NUMBER);

            assertNotNull(form4);
            assertEquals("WRAPPED CORP", form4.getIssuerName());
            assertEquals("WRAP", form4.getTradingSymbol());
        }
    }

    @Nested
    @DisplayName("Transaction Code Descriptions")
    class TransactionCodeDescriptions {

        @Test
        @DisplayName("Should return correct descriptions for all transaction codes")
        void shouldReturnCorrectDescriptions() {
            Form4Transaction tx = new Form4Transaction();

            tx.setTransactionCode("P");
            assertEquals("Open Market Purchase", tx.getTransactionCodeDescription());

            tx.setTransactionCode("S");
            assertEquals("Open Market Sale", tx.getTransactionCodeDescription());

            tx.setTransactionCode("A");
            assertEquals("Grant/Award (Rule 16b-3)", tx.getTransactionCodeDescription());

            tx.setTransactionCode("M");
            assertEquals("Exercise of Derivative", tx.getTransactionCodeDescription());

            tx.setTransactionCode("G");
            assertEquals("Gift", tx.getTransactionCodeDescription());

            tx.setTransactionCode("F");
            assertEquals("Payment via Withholding", tx.getTransactionCodeDescription());

            tx.setTransactionCode("X");
            assertEquals("Exercise (in-the-money)", tx.getTransactionCodeDescription());

            tx.setTransactionCode("J");
            assertEquals("Other", tx.getTransactionCodeDescription());

            tx.setTransactionCode(null);
            assertEquals("Unknown", tx.getTransactionCodeDescription());

            tx.setTransactionCode("ZZ");
            assertEquals("Code: ZZ", tx.getTransactionCodeDescription());
        }
    }

    private String loadTestXml(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
