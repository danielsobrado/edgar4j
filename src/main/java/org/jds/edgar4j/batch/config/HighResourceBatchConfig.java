package org.jds.edgar4j.batch.config;

import javax.sql.DataSource;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("resource-high")
@ConditionalOnProperty(prefix = "edgar4j", name = "resource-mode", havingValue = "high", matchIfMissing = true)
public class HighResourceBatchConfig {

    @Bean(name = "batchDataSource")
    @ConditionalOnBean(name = "dataSource")
    public DataSource batchDataSource(@Qualifier("dataSource") DataSource dataSource) {
        return dataSource;
    }

    @Bean
    @ConditionalOnBean(name = "batchDataSource")
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
        executor.setQueueCapacity(concurrency * 25);
        executor.setThreadNamePrefix("Edgar4j-Batch-High-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}

