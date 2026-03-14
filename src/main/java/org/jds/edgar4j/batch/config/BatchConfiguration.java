package org.jds.edgar4j.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.batch.processor.Form4DocumentProcessor;
import org.jds.edgar4j.batch.reader.EdgarFilingReader;
import org.jds.edgar4j.batch.writer.InsiderTransactionWriter;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
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
@ConditionalOnBean(JobRepository.class)
@RequiredArgsConstructor
public class BatchConfiguration {

    private final EdgarFilingReader edgarFilingReader;
    private final Form4DocumentProcessor form4DocumentProcessor;
    private final InsiderTransactionWriter insiderTransactionWriter;
    private final JobRepository jobRepository;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    private final Edgar4JProperties edgar4JProperties;
    @Qualifier("batchTaskExecutor")
    private final TaskExecutor batchTaskExecutor;
    private final ObjectProvider<FileFlushChunkListener> fileFlushChunkListenerProvider;

    @Bean
    public Job processForm4FilingsJob() {
        return new JobBuilder("processForm4FilingsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(processForm4Step())
                .build();
    }

    @Bean
    public Step processForm4Step() {
        PlatformTransactionManager transactionManager = resolveTransactionManager();
        SimpleStepBuilder<String, List<InsiderTransaction>> stepBuilder = new StepBuilder("processForm4Step", jobRepository)
                .<String, List<InsiderTransaction>>chunk(edgar4JProperties.getBatch().getChunkSize(), transactionManager)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .taskExecutor(batchTaskExecutor);

        FileFlushChunkListener chunkListener = fileFlushChunkListenerProvider.getIfAvailable();
        if (chunkListener != null) {
            stepBuilder.listener(chunkListener);
        }

        return stepBuilder.build();
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
        PlatformTransactionManager transactionManager = resolveTransactionManager();
        SimpleStepBuilder<String, List<InsiderTransaction>> stepBuilder = new StepBuilder("bulkHistoricalDataStep", jobRepository)
                .<String, List<InsiderTransaction>>chunk(edgar4JProperties.getBatch().getChunkSize(), transactionManager)
                .reader(edgarFilingReader)
                .processor(form4DocumentProcessor)
                .writer(insiderTransactionWriter)
                .taskExecutor(batchTaskExecutor);

        FileFlushChunkListener chunkListener = fileFlushChunkListenerProvider.getIfAvailable();
        if (chunkListener != null) {
            stepBuilder.listener(chunkListener);
        }

        return stepBuilder.build();
    }

    private PlatformTransactionManager resolveTransactionManager() {
        return transactionManagerProvider.orderedStream()
                .findFirst()
                .orElseGet(ResourcelessTransactionManager::new);
    }
}
