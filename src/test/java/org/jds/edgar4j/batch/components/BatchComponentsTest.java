package org.jds.edgar4j.batch.components;

import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.exception.Form4DocumentProcessingException;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchComponentsTest {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private Form4ParserService form4ParserService;

    @Mock
    private InsiderTransactionDataPort insiderTransactionDataPort;

    private EdgarFilingReader edgarFilingReader;
    private Form4DocumentProcessor form4DocumentProcessor;
    private InsiderTransactionWriter insiderTransactionWriter;

    @BeforeEach
    void setUp() {
        edgarFilingReader = new EdgarFilingReader(secApiClient);
        form4DocumentProcessor = new Form4DocumentProcessor(secApiClient, form4ParserService);
        insiderTransactionWriter = new InsiderTransactionWriter(insiderTransactionDataPort);
    }

    @DisplayName("EdgarFilingReader should read accession numbers correctly")
    @Test
    void testEdgarFilingReader() throws Exception {
        String accessionNumber1 = "0001234567-24-000001";
        String accessionNumber2 = "0000789019-24-000002";

        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class)))
            .thenReturn(Optional.of(createMasterIndex(accessionNumber1, accessionNumber2)));

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "formType", "FORM4");

        String firstItem = edgarFilingReader.read();
        String secondItem = edgarFilingReader.read();
        String thirdItem = edgarFilingReader.read();

        assertEquals(accessionNumber1, firstItem);
        assertEquals(accessionNumber2, secondItem);
        assertNull(thirdItem);

        verify(secApiClient, times(1)).fetchDailyMasterIndex(any(LocalDate.class));
    }

    @DisplayName("EdgarFilingReader should handle empty result set")
    @Test
    void testEdgarFilingReaderEmptyResults() throws Exception {
        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class))).thenReturn(Optional.empty());

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        String item = edgarFilingReader.read();

        assertNull(item);
    }

    @DisplayName("EdgarFilingReader should handle API errors gracefully")
    @Test
    void testEdgarFilingReaderApiError() {
        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class))).thenThrow(new RuntimeException("API Error"));

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        assertDoesNotThrow(() -> {
            String item = edgarFilingReader.read();
            assertNull(item);
        });
    }

    @DisplayName("Form4DocumentProcessor should process accession numbers correctly")
    @Test
    void testForm4DocumentProcessor() throws Exception {
        String accessionNumber = "0001234567-24-000001";
        String xmlContent = createTestXmlContent();
        List<InsiderTransaction> expectedTransactions = createTestTransactions();

        stubForm4Download(accessionNumber, xmlContent);
        when(form4ParserService.parseForm4Xml(xmlContent, accessionNumber)).thenReturn(expectedTransactions);

        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        assertNotNull(result);
        assertEquals(expectedTransactions.size(), result.size());
        assertEquals(expectedTransactions.get(0).getAccessionNumber(), result.get(0).getAccessionNumber());

        verify(secApiClient, times(1)).fetchFiling(eq("0001234567"), eq(accessionNumber), eq("index.json"));
        verify(secApiClient, times(1)).fetchForm4(eq("0001234567"), eq(accessionNumber), eq("doc4.xml"));
        verify(form4ParserService, times(1)).parseForm4Xml(xmlContent, accessionNumber);
    }

    @DisplayName("Form4DocumentProcessor should handle missing XML content")
    @Test
    void testForm4DocumentProcessorMissingXml() throws Exception {
        String accessionNumber = "0001234567-24-000001";

        stubForm4Download(accessionNumber, null);

        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(secApiClient, times(1)).fetchFiling(eq("0001234567"), eq(accessionNumber), eq("index.json"));
        verify(secApiClient, times(1)).fetchForm4(eq("0001234567"), eq(accessionNumber), eq("doc4.xml"));
        verify(form4ParserService, never()).parseForm4Xml(anyString(), anyString());
    }

    @DisplayName("Form4DocumentProcessor should handle parsing errors")
    @Test
    void testForm4DocumentProcessorParsingError() throws Exception {
        String accessionNumber = "0001234567-24-000001";
        String xmlContent = createTestXmlContent();

        stubForm4Download(accessionNumber, xmlContent);
        when(form4ParserService.parseForm4Xml(xmlContent, accessionNumber))
            .thenThrow(new RuntimeException("Parsing Error"));

        List<InsiderTransaction> result = form4DocumentProcessor.process(accessionNumber);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @DisplayName("Form4DocumentProcessor should skip blank accession number without service calls")
    @Test
    void testForm4DocumentProcessorSkipsBlankAccession() throws Exception {
        List<InsiderTransaction> result = form4DocumentProcessor.process("   ");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(secApiClient, form4ParserService);
    }

    @DisplayName("Form4DocumentProcessor should fail fast when configured")
    @Test
    void testForm4DocumentProcessorFailsFastWhenConfigured() throws Exception {
        String accessionNumber = "0001234567-24-000001";
        String xmlContent = createTestXmlContent();

        stubForm4Download(accessionNumber, xmlContent);
        when(form4ParserService.parseForm4Xml(xmlContent, accessionNumber))
                .thenThrow(new RuntimeException("Parsing Error"));

        ReflectionTestUtils.setField(form4DocumentProcessor, "failOnProcessingError", true);

        assertThrows(Form4DocumentProcessingException.class, () -> form4DocumentProcessor.process(accessionNumber));
    }

    @DisplayName("InsiderTransactionWriter should write transactions successfully")
    @Test
    void testInsiderTransactionWriter() throws Exception {
        List<InsiderTransaction> chunk1 = createTestTransactions();
        List<InsiderTransaction> chunk2 = createTestTransactions();
        List<List<InsiderTransaction>> chunks = Arrays.asList(chunk1, chunk2);

        when(insiderTransactionDataPort.saveAll(any())).thenReturn(chunk1).thenReturn(chunk2);

        insiderTransactionWriter.write(new Chunk<>(chunks));

        verify(insiderTransactionDataPort, times(2)).saveAll(any());
    }

    @DisplayName("InsiderTransactionWriter should handle save errors with fallback")
    @Test
    void testInsiderTransactionWriterSaveError() throws Exception {
        List<InsiderTransaction> chunk = createTestTransactions();
        List<List<InsiderTransaction>> chunks = Arrays.asList(chunk);

        when(insiderTransactionDataPort.saveAll(any())).thenThrow(new RuntimeException("Save Error"));
        when(insiderTransactionDataPort.save(any())).thenReturn(chunk.get(0));

        insiderTransactionWriter.write(new Chunk<>(chunks));

        verify(insiderTransactionDataPort, times(1)).saveAll(any());
        verify(insiderTransactionDataPort, times(chunk.size())).save(any());
    }

    @DisplayName("InsiderTransactionWriter should handle null chunks gracefully")
    @Test
    void testInsiderTransactionWriterNullChunks() {
        List<List<InsiderTransaction>> chunks = Arrays.asList(null, Arrays.asList());

        assertDoesNotThrow(() -> insiderTransactionWriter.write(new Chunk<>(chunks)));

        verify(insiderTransactionDataPort, never()).saveAll(any());
    }

    @DisplayName("Should handle date parsing in EdgarFilingReader")
    @Test
    void testDateParsingInReader() {
        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "invalid-date");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");

        assertDoesNotThrow(() -> edgarFilingReader.read());
    }

    @DisplayName("EdgarFilingReader should skip execution for unsupported formType")
    @Test
    void testEdgarFilingReaderUnsupportedFormType() throws Exception {
        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-15");
        ReflectionTestUtils.setField(edgarFilingReader, "formType", "FORM8-K");

        String item = edgarFilingReader.read();

        assertNull(item);
        verify(secApiClient, never()).fetchDailyMasterIndex(any(LocalDate.class));
    }

    @DisplayName("EdgarFilingReader should normalize reversed date ranges")
    @Test
    void testEdgarFilingReaderReversedDateRange() throws Exception {
        String accessionNumber1 = "0001234567-24-000001";
        String accessionNumber2 = "0000789019-24-000002";
        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class)))
                .thenReturn(Optional.of(createMasterIndex(accessionNumber1, accessionNumber2)));

        ReflectionTestUtils.setField(edgarFilingReader, "startDate", "2024-01-20");
        ReflectionTestUtils.setField(edgarFilingReader, "endDate", "2024-01-10");

        String first = edgarFilingReader.read();
        String second = edgarFilingReader.read();

        assertEquals(accessionNumber1, first);
        assertEquals(accessionNumber2, second);
        verify(secApiClient, atLeastOnce()).fetchDailyMasterIndex(any(LocalDate.class));
    }

    private void stubForm4Download(String accessionNumber, String xmlContent) {
        when(secApiClient.fetchFiling(eq("0001234567"), eq(accessionNumber), eq("index.json")))
                .thenReturn(createFilingIndex("doc4.xml"));
        when(secApiClient.fetchForm4(eq("0001234567"), eq(accessionNumber), eq("doc4.xml")))
                .thenReturn(xmlContent);
    }

    private String createMasterIndex(String... accessionNumbers) {
        StringBuilder builder = new StringBuilder("""
            Description: Master Index

            CIK|Company Name|Form Type|Date Filed|Filename
            """);
        for (String accessionNumber : accessionNumbers) {
            builder.append("1234567890|TEST CORP|4|2024-01-15|edgar/data/1234567890/")
                    .append(accessionNumber)
                    .append("/doc4.xml\n");
        }
        return builder.toString();
    }

    private String createFilingIndex(String documentName) {
        return """
            {
              "directory": {
                "item": [
                  { "name": "primary_doc.html" },
                  { "name": "%s" }
                ]
              }
            }
            """.formatted(documentName);
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
