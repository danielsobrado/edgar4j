package org.jds.edgar4j.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfiguration {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final EdgarFilingReader edgarFilingReader;
    private final Form4DocumentProcessor form4DocumentProcessor;
    private final InsiderTransactionWriter insiderTransactionWriter;

    @Bean
    public Job processForm4FilingsJob() {
        return jobBuilderFactory.get("processForm4FilingsJob")
                .incrementer(new RunIdIncrementer())
                .start(processForm4Step())
                .build();
    }

    @Bean
    public Step processForm4Step() {
        return stepBuilderFactory.get("processForm4Step")
                .<String, List<InsiderTransaction>>chunk(10)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .build();
    }

    @Bean
    public Job bulkHistoricalDataJob() {
        return jobBuilderFactory.get("bulkHistoricalDataJob")
                .incrementer(new RunIdIncrementer())
                .start(bulkHistoricalDataStep())
                .build();
    }

    @Bean
    public Step bulkHistoricalDataStep() {
        return stepBuilderFactory.get("bulkHistoricalDataStep")
                .<String, List<InsiderTransaction>>chunk(50)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .build();
    }
}