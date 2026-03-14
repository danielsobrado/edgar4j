package org.jds.edgar4j.batch;

import javax.sql.DataSource;

import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Spring Batch Form 4 processing pipeline
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBatchTest
@EnableBatchProcessing
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:batchtest",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.batch.job.enabled=false",
    "spring.batch.job.name=processForm4FilingsJob",
    "spring.batch.jdbc.initialize-schema=always",
    "spring.quartz.auto-startup=false",
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
    @Qualifier("processForm4FilingsJob")
    private Job processForm4FilingsJob;

    @Autowired
    private EdgarApiService edgarApiService;

    @Autowired
    private Form4ParserService form4ParserService;

    @Autowired
    private InsiderTransactionDataPort insiderTransactionDataPort;

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
        jobLauncherTestUtils.setJob(processForm4FilingsJob);
        reset(edgarApiService, form4ParserService, insiderTransactionDataPort);
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
        when(insiderTransactionDataPort.saveAll(any()))
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
        assertTrue(jobExecution.getJobInstanceId() > 0);
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

    @DisplayName("Should fall back to the current date when batch dates are omitted")
    @Test
    void testJobUsesDefaultDatesWhenParametersAreMissing() throws Exception {
        // Given
        when(edgarApiService.getForm4FilingsByDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        JobParameters jobParameters = new JobParametersBuilder()
            .addString("formType", "FORM4")
            .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then
        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
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

    @TestConfiguration
    static class MockBatchDependenciesConfig {

        @Bean
        @Primary
        EdgarApiService edgarApiService() {
            return mock(EdgarApiService.class);
        }

        @Bean
        @Primary
        Form4ParserService form4ParserService() {
            return mock(Form4ParserService.class);
        }

        @Bean
        @Primary
        InsiderTransactionDataPort insiderTransactionDataPort() {
            return mock(InsiderTransactionDataPort.class);
        }

        @Bean(name = "dataSource")
        @Primary
        DataSource dataSource() {
            return DataSourceBuilder.create()
                    .driverClassName("org.h2.Driver")
                    .url("jdbc:h2:mem:batchtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                    .username("sa")
                    .password("")
                    .build();
        }

        @Bean(name = "transactionManager")
        @Primary
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
