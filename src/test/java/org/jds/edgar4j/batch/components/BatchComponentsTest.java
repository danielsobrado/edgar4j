package org.jds.edgar4j.batch.components;

import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.jds.edgar4j.service.insider.InsiderTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Spring Batch components
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(MockitoExtension.class)
class BatchComponentsTest {

    @Mock
    private EdgarApiService edgarApiService;

    @Mock
    private Form4ParserService form4ParserService;

    @Mock
    private InsiderTransactionService insiderTransactionService;

    private EdgarFilingReader edgarFilingReader;
    private Form4DocumentProcessor form4DocumentProcessor;
    private InsiderTransactionWriter insiderTransactionWriter;

    @BeforeEach
    void setUp() {
        edgarFilingReader = new EdgarFilingReader(edgarApiService);
        form4DocumentProcessor = new Form4DocumentProcessor(edgarApiService, form4ParserService);
        insiderTransactionWriter = new InsiderTransactionWriter(insiderTransactionService);
    }

    @DisplayName("EdgarFilingReader should read accession numbers correctly")
    @Test
    void testEdgarFilingReader() throws Exception {
        // Given
        String accessionNumber1 = "0001234567-24-000001";
        String accessionNumber2 = "0000789019-24-000002";
        
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Arrays.asList(accessionNumber1, accessionNumber2));

        // Set up job parameters using reflection
        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "formType", "FORM4");

        // When
        String firstItem = edgarFilingReader.read();
        String secondItem = edgarFilingReader.read();
        String thirdItem = edgarFilingReader.read(); // Should be null (end of data)

        // Then
        assertEquals(accessionNumber1, firstItem);
        assertEquals(accessionNumber2, secondItem);
        assertNull(thirdItem);
        
        verify(edgarApiService, times(1)).getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class));
    }

    @DisplayName("EdgarFilingReader should handle empty result set")
    @Test
    void testEdgarFilingReaderEmptyResults() throws Exception {
        // Given
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Arrays.asList());

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        // When
        String item = edgarFilingReader.read();

        // Then
        assertNull(item);
    }

    @DisplayName("EdgarFilingReader should handle API errors gracefully")
    @Test
    void testEdgarFilingReaderApiError() throws Exception {
        // Given
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenThrow(new RuntimeException("API Error"));

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        // When & Then
        assertDoesNotThrow(() -> {
            String item = edgarFilingReader.read();
            assertNull(item); // Should return null when error occurs
        });
    }

    @DisplayName("Form4DocumentProcessor should process accession numbers correctly")
    @Test
    void testForm4DocumentProcessor() throws Exception {
        // Given
        String accessionNumber = "0001234567-24-000001";
        String xmlContent = createTestXmlContent();
        List<InsiderTransaction> expectedTransactions = createTestTransactions();

        when(edgarApiService.getForm4Document(accessionNumber)).thenReturn(xmlContent);
        when(form4ParserService.parseForm4Xml(xmlContent, accessionNumber)).thenReturn(expectedTransactions);

        // When
        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        // Then
        assertNotNull(result);
        assertEquals(expectedTransactions.size(), result.size());
        assertEquals(expectedTransactions.get(0).getAccessionNumber(), result.get(0).getAccessionNumber());
        
        verify(edgarApiService, times(1)).getForm4Document(accessionNumber);
        verify(form4ParserService, times(1)).parseForm4Xml(xmlContent, accessionNumber);
    }

    @DisplayName("Form4DocumentProcessor should handle missing XML content")
    @Test
    void testForm4DocumentProcessorMissingXml() throws Exception {
        // Given
        String accessionNumber = "0001234567-24-000001";
        
        when(edgarApiService.getForm4Document(accessionNumber)).thenReturn(null);

        // When
        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(edgarApiService, times(1)).getForm4Document(accessionNumber);
        verify(form4ParserService, never()).parseForm4Xml(anyString(), anyString());
    }

    @DisplayName("Form4DocumentProcessor should handle parsing errors")
    @Test
    void testForm4DocumentProcessorParsingError() throws Exception {
        // Given
        String accessionNumber = "0001234567-24-000001";
        String xmlContent = createTestXmlContent();

        when(edgarApiService.getForm4Document(accessionNumber)).thenReturn(xmlContent);
        when(form4ParserService.parseForm4Xml(xmlContent, accessionNumber))
            .thenThrow(new RuntimeException("Parsing Error"));

        // When
        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @DisplayName("InsiderTransactionWriter should write transactions successfully")
    @Test
    void testInsiderTransactionWriter() throws Exception {
        // Given
        List<InsiderTransaction> chunk1 = createTestTransactions();
        List<InsiderTransaction> chunk2 = createTestTransactions();
        List<List<InsiderTransaction>> chunks = Arrays.asList(chunk1, chunk2);

        when(insiderTransactionService.saveAll(any())).thenReturn(chunk1).thenReturn(chunk2);

        // When
        insiderTransactionWriter.write(chunks);

        // Then
        verify(insiderTransactionService, times(2)).saveAll(any());
    }

    @DisplayName("InsiderTransactionWriter should handle save errors with fallback")
    @Test
    void testInsiderTransactionWriterSaveError() throws Exception {
        // Given
        List<InsiderTransaction> chunk = createTestTransactions();
        List<List<InsiderTransaction>> chunks = Arrays.asList(chunk);

        when(insiderTransactionService.saveAll(any())).thenThrow(new RuntimeException("Save Error"));
        when(insiderTransactionService.save(any())).thenReturn(chunk.get(0));

        // When
        insiderTransactionWriter.write(chunks);

        // Then
        verify(insiderTransactionService, times(1)).saveAll(any());
        verify(insiderTransactionService, times(chunk.size())).save(any());
    }

    @DisplayName("InsiderTransactionWriter should handle null chunks gracefully")
    @Test
    void testInsiderTransactionWriterNullChunks() throws Exception {
        // Given
        List<List<InsiderTransaction>> chunks = Arrays.asList(null, Arrays.asList());

        // When & Then
        assertDoesNotThrow(() -> {
            insiderTransactionWriter.write(chunks);
        });

        verify(insiderTransactionService, never()).saveAll(any());
    }

    @DisplayName("Should handle date parsing in EdgarFilingReader")
    @Test
    void testDateParsingInReader() {
        // Given
        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "invalid-date");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            String item = edgarFilingReader.read();
        });
    }

    private String createTestXmlContent() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <ownershipDocument>
                <documentType>4</documentType>
                <periodOfReport>2024-01-15</periodOfReport>
                <issuer>
                    <issuerCik>0000789019</issuerCik>
                    <issuerName>TEST CORP</issuerName>
                    <issuerTradingSymbol>TEST</issuerTradingSymbol>
                </issuer>
                <reportingOwner>
                    <reportingOwnerId>
                        <rptOwnerCik>0001234567</rptOwnerCik>
                        <rptOwnerName>TEST INSIDER</rptOwnerName>
                    </reportingOwnerId>
                </reportingOwner>
            </ownershipDocument>
            """;
    }

    private List<InsiderTransaction> createTestTransactions() {
        InsiderTransaction transaction = InsiderTransaction.builder()
            .accessionNumber("0001234567-24-000001")
            .transactionDate(LocalDate.of(2024, 1, 15))
            .filingDate(LocalDate.of(2024, 1, 16))
            .securityTitle("Common Stock")
            .transactionCode("P")
            .isDerivative(false)
            .build();

        return Arrays.asList(transaction);
    }
}
