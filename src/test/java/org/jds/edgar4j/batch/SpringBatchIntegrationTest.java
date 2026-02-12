package org.jds.edgar4j.batch;

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
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Spring Batch Form 4 processing pipeline
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@SpringBatchTest
@EnableBatchProcessing
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:batchtest",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "edgar4j.urls.submissionsCIKUrl=https://data.sec.gov/submissions/CIK",
    "edgar4j.urls.edgarDataArchivesUrl=https://www.sec.gov/Archives/edgar/data",
    "edgar4j.urls.companyTickersUrl=https://www.sec.gov/files/company_tickers.json"
})
@Transactional
public class SpringBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job processForm4FilingsJob;

    @Mock
    private EdgarApiService edgarApiService;

    @Mock
    private Form4ParserService form4ParserService;

    @Mock
    private InsiderTransactionService insiderTransactionService;

    private final String TEST_XML_CONTENT = """
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

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @DisplayName("Should process Form 4 batch job successfully")
    @Test
    void testForm4BatchJobExecution() throws Exception {
        // Given
        String accessionNumber = "0001234567-24-000001";
        
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Arrays.asList(accessionNumber));
        when(edgarApiService.getForm4Document(anyString()))
            .thenReturn(TEST_XML_CONTENT);
        when(form4ParserService.parseForm4Xml(anyString(), anyString()))
            .thenReturn(createTestTransactions());
        when(insiderTransactionService.saveAll(any()))
            .thenReturn(createTestTransactions());

        JobParameters jobParameters = new JobParametersBuilder()
            .addString("startDate", "2024-01-15")
            .addString("endDate", "2024-01-15")
            .addString("formType", "FORM4")
            .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then
        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
        assertNotNull(jobExecution.getJobId());
        assertTrue(jobExecution.getStepExecutions().size() > 0);
    }

    @DisplayName("Should handle empty result set gracefully")
    @Test
    void testBatchJobWithNoData() throws Exception {
        // Given
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Arrays.asList());

        JobParameters jobParameters = new JobParametersBuilder()
            .addString("startDate", "2024-01-01")
            .addString("endDate", "2024-01-01")
            .addString("formType", "FORM4")
            .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then
        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
    }

    @DisplayName("Should handle job parameter validation")
    @Test
    void testJobParameterValidation() throws Exception {
        // Given - Missing required parameters
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("formType", "FORM4")
            .toJobParameters();

        // When & Then
        assertThrows(Exception.class, () -> {
            jobLauncherTestUtils.launchJob(jobParameters);
        });
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
