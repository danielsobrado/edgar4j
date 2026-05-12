package org.jds.edgar4j.batch;

import javax.sql.DataSource;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
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
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

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
    "spring.quartz.auto-startup=false"
})
@Transactional
@SuppressWarnings("removal")
public class SpringBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("processForm4FilingsJob")
    private Job processForm4FilingsJob;

    @Autowired
    private SecApiClient secApiClient;

    @Autowired
    private Form4ParserService form4ParserService;

    @Autowired
    private InsiderTransactionDataPort insiderTransactionDataPort;

    private static final String TEST_XML_CONTENT = """
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
            </reportingOwner>
        </ownershipDocument>
        """;

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        jobLauncherTestUtils.setJob(processForm4FilingsJob);
        reset(secApiClient, form4ParserService, insiderTransactionDataPort);
    }

    @DisplayName("Should process Form 4 batch job successfully")
    @Test
    void testForm4BatchJobExecution() throws Exception {
        String accessionNumber = "0001234567-24-000001";

        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class)))
            .thenReturn(Optional.of(createMasterIndex(accessionNumber)));
        when(secApiClient.fetchFiling(eq("0001234567"), eq(accessionNumber), eq("index.json")))
            .thenReturn(createFilingIndex("doc4.xml"));
        when(secApiClient.fetchForm4(eq("0001234567"), eq(accessionNumber), eq("doc4.xml")))
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

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
        assertTrue(jobExecution.getJobInstanceId() > 0);
        assertTrue(jobExecution.getStepExecutions().size() > 0);
    }

    @DisplayName("Should handle empty result set gracefully")
    @Test
    void testBatchJobWithNoData() throws Exception {
        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class))).thenReturn(Optional.empty());

        JobParameters jobParameters = new JobParametersBuilder()
            .addString("startDate", "2024-01-01")
            .addString("endDate", "2024-01-01")
            .addString("formType", "FORM4")
            .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
    }

    @DisplayName("Should fall back to the current date when batch dates are omitted")
    @Test
    void testJobUsesDefaultDatesWhenParametersAreMissing() throws Exception {
        when(secApiClient.fetchDailyMasterIndex(any(LocalDate.class))).thenReturn(Optional.empty());

        JobParameters jobParameters = new JobParametersBuilder()
            .addString("formType", "FORM4")
            .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
    }

    private String createMasterIndex(String accessionNumber) {
        return """
            Description: Master Index

            CIK|Company Name|Form Type|Date Filed|Filename
            1234567890|TEST CORP|4|2024-01-15|edgar/data/1234567890/%s/doc4.xml
            """.formatted(accessionNumber);
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
        SecApiClient secApiClient() {
            return mock(SecApiClient.class);
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
