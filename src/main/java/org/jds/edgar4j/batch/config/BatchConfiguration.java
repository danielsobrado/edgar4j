package org.jds.edgar4j.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Spring Batch configuration for Edgar4J processing
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchConfiguration {

    private final EdgarFilingReader edgarFilingReader;
    private final Form4DocumentProcessor form4DocumentProcessor;
    private final InsiderTransactionWriter insiderTransactionWriter;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job processForm4FilingsJob() {
        return new JobBuilder("processForm4FilingsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(processForm4Step())
                .build();
    }

    @Bean
    public Step processForm4Step() {
        return new StepBuilder("processForm4Step", jobRepository)
                .<String, List<InsiderTransaction>>chunk(10, transactionManager)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .build();
    }

    @Bean
    public Job bulkHistoricalDataJob() {
        return new JobBuilder("bulkHistoricalDataJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(bulkHistoricalDataStep())
                .build();
    }

    @Bean
    public Step bulkHistoricalDataStep() {
        return new StepBuilder("bulkHistoricalDataStep", jobRepository)
                .<String, List<InsiderTransaction>>chunk(50, transactionManager)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .build();
    }
}
