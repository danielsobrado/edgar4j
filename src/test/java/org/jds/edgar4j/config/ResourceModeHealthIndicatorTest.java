package org.jds.edgar4j.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.health.contributor.Health;

import com.fasterxml.jackson.databind.ObjectMapper;

class ResourceModeHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void healthIsDownWhenLowModeBatchDatasourceIsUnavailable() {
        FileStorageEngine storageEngine = storageEngine(tempDir);
        ResourceModeHealthIndicator indicator = new ResourceModeHealthIndicator(
                new ResourceModeInfo("low", "file storage"),
                provider(FileStorageEngine.class, storageEngine),
                provider(DataSource.class, null),
                provider(com.mongodb.client.MongoClient.class, null),
                provider(org.springframework.data.redis.connection.RedisConnectionFactory.class, null));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("batchMetadataDatabase", "unavailable");
    }

    @Test
    void healthIsDownWhenLowModeStorageDirectoryIsNotWritable() throws Exception {
        Path fileBasePath = Files.createFile(tempDir.resolve("not-a-directory"));
        FileStorageEngine storageEngine = storageEngine(fileBasePath);
        DataSource dataSource = healthyDataSource();
        ResourceModeHealthIndicator indicator = new ResourceModeHealthIndicator(
                new ResourceModeInfo("low", "file storage"),
                provider(FileStorageEngine.class, storageEngine),
                provider(DataSource.class, dataSource),
                provider(com.mongodb.client.MongoClient.class, null),
                provider(org.springframework.data.redis.connection.RedisConnectionFactory.class, null));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsKey("fileStorage");
        assertThat(health.getDetails().get("fileStorage").toString()).contains("writable=false");
    }

    @Test
    void healthIsUpWhenLowModeDependenciesAreAvailable() throws Exception {
        FileStorageEngine storageEngine = storageEngine(tempDir);
        DataSource dataSource = healthyDataSource();
        ResourceModeHealthIndicator indicator = new ResourceModeHealthIndicator(
                new ResourceModeInfo("low", "file storage"),
                provider(FileStorageEngine.class, storageEngine),
                provider(DataSource.class, dataSource),
                provider(com.mongodb.client.MongoClient.class, null),
                provider(org.springframework.data.redis.connection.RedisConnectionFactory.class, null));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails().get("batchMetadataDatabase")).isEqualTo("jdbc:h2:mem:test");
    }

    private FileStorageEngine storageEngine(Path basePath) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(basePath.toString());
        properties.setCollectionsPath("collections");
        return new FileStorageEngine(properties, new ObjectMapper());
    }

    private DataSource healthyDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData metadata = mock(java.sql.DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:h2:mem:test");
        return dataSource;
    }

    private <T> ObjectProvider<T> provider(Class<T> type, T value) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (value != null) {
            beanFactory.addBean(type.getName(), value);
        }
        return beanFactory.getBeanProvider(type);
    }
}
