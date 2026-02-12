package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.insider.impl.Form4ParserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Form4ParserService implementation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(MockitoExtension.class)
class Form4ParserServiceTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private InsiderService insiderService;

    private Form4ParserServiceImpl form4ParserService;

    private final String VALID_FORM4_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
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
                    <rptOwnerName>SMITH JOHN</rptOwnerName>
                </reportingOwnerId>
                <reportingOwnerAddress>
                    <rptOwnerStreet1>123 Main Street</rptOwnerStreet1>
                    <rptOwnerCity>Seattle</rptOwnerCity>
                    <rptOwnerState>WA</rptOwnerState>
                    <rptOwnerZipCode>98101</rptOwnerZipCode>
                </reportingOwnerAddress>
                <reportingOwnerRelationship>
                    <isDirector>1</isDirector>
                    <isOfficer>0</isOfficer>
                    <isTenPercentOwner>0</isTenPercentOwner>
                    <isOther>0</isOther>
                </reportingOwnerRelationship>
            </reportingOwner>
            <nonDerivativeTable>
                <nonDerivativeTransaction>
                    <securityTitle>
                        <value>Common Stock</value>
                    </securityTitle>
                    <transactionDate>
                        <value>2024-01-15</value>
                    </transactionDate>
                    <transactionCoding>
                        <transactionFormType>4</transactionFormType>
                        <transactionCode>P</transactionCode>
                    </transactionCoding>
                    <transactionAmounts>
                        <transactionShares>
                            <value>1000</value>
                        </transactionShares>
                        <transactionPricePerShare>
                            <value>350.00</value>
                        </transactionPricePerShare>
                        <transactionAcquiredDisposedCode>
                            <value>A</value>
                        </transactionAcquiredDisposedCode>
                    </transactionAmounts>
                    <postTransactionAmounts>
                        <sharesOwnedFollowingTransaction>
                            <value>15000</value>
                        </sharesOwnedFollowingTransaction>
                    </postTransactionAmounts>
                    <ownershipNature>
                        <directOrIndirectOwnership>
                            <value>D</value>
                        </directOrIndirectOwnership>
                    </ownershipNature>
                </nonDerivativeTransaction>
            </nonDerivativeTable>
        </ownershipDocument>
        """;

    private final String DERIVATIVE_FORM4_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
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
                    <rptOwnerName>SMITH JOHN</rptOwnerName>
                </reportingOwnerId>
                <reportingOwnerAddress>
                    <rptOwnerStreet1>123 Main Street</rptOwnerStreet1>
                    <rptOwnerCity>Seattle</rptOwnerCity>
                    <rptOwnerState>WA</rptOwnerState>
                    <rptOwnerZipCode>98101</rptOwnerZipCode>
                </reportingOwnerAddress>
                <reportingOwnerRelationship>
                    <isDirector>0</isDirector>
                    <isOfficer>1</isOfficer>
                    <isTenPercentOwner>0</isTenPercentOwner>
                    <isOther>0</isOther>
                    <officerTitle>Chief Executive Officer</officerTitle>
                </reportingOwnerRelationship>
            </reportingOwner>
            <derivativeTable>
                <derivativeTransaction>
                    <securityTitle>
                        <value>Stock Option</value>
                    </securityTitle>
                    <transactionDate>
                        <value>2024-01-15</value>
                    </transactionDate>
                    <transactionCoding>
                        <transactionFormType>4</transactionFormType>
                        <transactionCode>M</transactionCode>
                    </transactionCoding>
                    <transactionAmounts>
                        <transactionShares>
                            <value>5000</value>
                        </transactionShares>
                        <transactionPricePerShare>
                            <value>200.00</value>
                        </transactionPricePerShare>
                        <transactionAcquiredDisposedCode>
                            <value>A</value>
                        </transactionAcquiredDisposedCode>
                    </transactionAmounts>
                    <exerciseDate>
                        <value>2024-01-15</value>
                    </exerciseDate>
                    <expirationDate>
                        <value>2029-01-15</value>
                    </expirationDate>
                    <underlyingSecurity>
                        <underlyingSecurityTitle>Common Stock</underlyingSecurityTitle>
                        <underlyingSecurityShares>5000</underlyingSecurityShares>
                    </underlyingSecurity>
                    <postTransactionAmounts>
                        <sharesOwnedFollowingTransaction>
                            <value>10000</value>
                        </sharesOwnedFollowingTransaction>
                    </postTransactionAmounts>
                    <ownershipNature>
                        <directOrIndirectOwnership>
                            <value>D</value>
                        </directOrIndirectOwnership>
                    </ownershipNature>
                </derivativeTransaction>
            </derivativeTable>
        </ownershipDocument>
        """;

    @BeforeEach
    void setUp() {
        form4ParserService = new Form4ParserServiceImpl(companyService, insiderService);
    }

    @DisplayName("Should validate valid Form 4 XML")
    @Test
    void testValidateValidForm4Xml() {
        // When
        boolean isValid = form4ParserService.validateForm4Xml(VALID_FORM4_XML);

        // Then
        assertTrue(isValid);
    }

    @DisplayName("Should reject invalid Form 4 XML")
    @Test
    void testValidateInvalidForm4Xml() {
        // Given
        String invalidXml = "<invalid>xml</invalid>";

        // When
        boolean isValid = form4ParserService.validateForm4Xml(invalidXml);

        // Then
        assertFalse(isValid);
    }

    @DisplayName("Should extract issuer information correctly")
    @Test
    void testExtractIssuerInfo() {
        // When
        Form4ParserService.IssuerInfo issuerInfo = form4ParserService.extractIssuerInfo(VALID_FORM4_XML);

        // Then
        assertNotNull(issuerInfo);
        assertEquals("0000789019", issuerInfo.getCik());
        assertEquals("MICROSOFT CORP", issuerInfo.getName());
        assertEquals("MSFT", issuerInfo.getTradingSymbol());
    }

    @DisplayName("Should extract reporting owner information correctly")
    @Test
    void testExtractReportingOwnerInfo() {
        // When
        Form4ParserService.ReportingOwnerInfo ownerInfo = form4ParserService.extractReportingOwnerInfo(VALID_FORM4_XML);

        // Then
        assertNotNull(ownerInfo);
        assertEquals("0001234567", ownerInfo.getCik());
        assertEquals("SMITH JOHN", ownerInfo.getName());
        assertTrue(ownerInfo.isDirector());
        assertFalse(ownerInfo.isOfficer());
        assertFalse(ownerInfo.isTenPercentOwner());
        assertFalse(ownerInfo.isOther());
        assertNotNull(ownerInfo.getAddress());
        assertTrue(ownerInfo.getAddress().contains("Seattle"));
    }

    @DisplayName("Should extract non-derivative transactions correctly")
    @Test
    void testExtractNonDerivativeTransactions() {
        // When
        List<Form4ParserService.NonDerivativeTransaction> transactions = 
            form4ParserService.extractNonDerivativeTransactions(VALID_FORM4_XML);

        // Then
        assertNotNull(transactions);
        assertEquals(1, transactions.size());
        
        Form4ParserService.NonDerivativeTransaction transaction = transactions.get(0);
        assertEquals("Common Stock", transaction.getSecurityTitle());
        assertEquals("2024-01-15", transaction.getTransactionDate());
        assertEquals("P", transaction.getTransactionCode());
        assertEquals("1000", transaction.getSharesTransacted());
        assertEquals("350.00", transaction.getPricePerShare());
        assertEquals("A", transaction.getAcquiredDisposed());
        assertEquals("15000", transaction.getSharesOwnedAfter());
        assertEquals("D", transaction.getOwnershipNature());
    }

    @DisplayName("Should extract derivative transactions correctly")
    @Test
    void testExtractDerivativeTransactions() {
        // When
        List<Form4ParserService.DerivativeTransaction> transactions = 
            form4ParserService.extractDerivativeTransactions(DERIVATIVE_FORM4_XML);

        // Then
        assertNotNull(transactions);
        assertEquals(1, transactions.size());
        
        Form4ParserService.DerivativeTransaction transaction = transactions.get(0);
        assertEquals("Stock Option", transaction.getSecurityTitle());
        assertEquals("2024-01-15", transaction.getTransactionDate());
        assertEquals("M", transaction.getTransactionCode());
        assertEquals("5000", transaction.getSharesTransacted());
        assertEquals("200.00", transaction.getPricePerShare());
        assertEquals("A", transaction.getAcquiredDisposed());
        assertEquals("2024-01-15", transaction.getExerciseDate());
        assertEquals("2029-01-15", transaction.getExpirationDate());
        assertEquals("Common Stock", transaction.getUnderlyingSecurityTitle());
        assertEquals("5000", transaction.getUnderlyingShares());
        assertEquals("10000", transaction.getSharesOwnedAfter());
        assertEquals("D", transaction.getOwnershipNature());
    }

    @DisplayName("Should parse complete Form 4 XML successfully")
    @Test
    void testParseCompleteForm4Xml() {
        // Given
        String accessionNumber = "0001234567-24-000001";
        
        Company mockCompany = Company.builder()
            .cik("0000789019")
            .companyName("MICROSOFT CORP")
            .tickerSymbol("MSFT")
            .build();
        
        Insider mockInsider = Insider.builder()
            .cik("0001234567")
            .personName("SMITH JOHN")
            .build();

        when(companyService.getOrCreateCompany(anyString(), anyString(), anyString()))
            .thenReturn(mockCompany);
        when(insiderService.getOrCreateInsider(anyString(), anyString(), anyString()))
            .thenReturn(mockInsider);

        // When
        List<InsiderTransaction> transactions = form4ParserService.parseForm4Xml(VALID_FORM4_XML, accessionNumber);

        // Then
        assertNotNull(transactions);
        assertEquals(1, transactions.size());
        
        InsiderTransaction transaction = transactions.get(0);
        assertEquals(accessionNumber, transaction.getAccessionNumber());
        assertEquals(LocalDate.of(2024, 1, 15), transaction.getTransactionDate());
        assertEquals("Common Stock", transaction.getSecurityTitle());
        assertEquals("P", transaction.getTransactionCode());
        assertEquals(new BigDecimal("1000"), transaction.getSharesTransacted());
        assertEquals(new BigDecimal("350.00"), transaction.getPricePerShare());
        assertEquals(new BigDecimal("15000"), transaction.getSharesOwnedAfter());
        assertFalse(transaction.getIsDerivative());
        assertEquals(InsiderTransaction.AcquiredDisposed.ACQUIRED, transaction.getAcquiredDisposed());
        assertEquals(InsiderTransaction.OwnershipNature.DIRECT, transaction.getOwnershipNature());
    }

    @DisplayName("Should handle malformed XML gracefully")
    @Test
    void testHandleMalformedXml() {
        // Given
        String malformedXml = "<?xml version=\"1.0\"?><malformed>";
        String accessionNumber = "0001234567-24-000001";

        // When
        List<InsiderTransaction> transactions = form4ParserService.parseForm4Xml(malformedXml, accessionNumber);

        // Then
        assertNotNull(transactions);
        assertTrue(transactions.isEmpty());
    }

    @DisplayName("Should handle missing required elements")
    @Test
    void testHandleMissingRequiredElements() {
        // Given
        String incompleteXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ownershipDocument>
                <documentType>4</documentType>
            </ownershipDocument>
            """;
        String accessionNumber = "0001234567-24-000001";

        // When
        List<InsiderTransaction> transactions = form4ParserService.parseForm4Xml(incompleteXml, accessionNumber);

        // Then
        assertNotNull(transactions);
        assertTrue(transactions.isEmpty());
    }
}
