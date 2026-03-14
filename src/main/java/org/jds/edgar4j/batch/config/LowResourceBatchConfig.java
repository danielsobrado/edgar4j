package org.jds.edgar4j.batch.config;

import javax.sql.DataSource;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("resource-low")
@ConditionalOnProperty(prefix = "edgar4j", name = "resource-mode", havingValue = "low")
public class LowResourceBatchConfig {

    @Bean(name = "batchDataSource")
    public DataSource batchDataSource(FileStorageProperties fileStorageProperties) {
        String batchMetadataPath = fileStorageProperties.resolveBaseDirectory()
                .resolve("batch")
                .resolve("batch_metadata")
                .toAbsolutePath()
                .toString()
                .replace("\\", "/");

        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:file:" + batchMetadataPath + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE")
                .username("sa")
                .password("")
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager batchTransactionManager(@Qualifier("batchDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "batchTaskExecutor")
    public ThreadPoolTaskExecutor batchTaskExecutor(Edgar4JProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int concurrency = Math.max(1, properties.getBatch().getMaxConcurrentSteps());
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(concurrency * 10);
        executor.setThreadNamePrefix("Edgar4j-Batch-Low-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
