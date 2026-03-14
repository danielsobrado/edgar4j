package org.jds.edgar4j.config;

import javax.sql.DataSource;

import org.bson.Document;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;

@Component
public class ResourceModeHealthIndicator implements HealthIndicator {

    private final ResourceModeInfo resourceModeInfo;
    private final ObjectProvider<FileStorageEngine> fileStorageEngineProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<MongoClient> mongoClientProvider;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public ResourceModeHealthIndicator(
            ResourceModeInfo resourceModeInfo,
            ObjectProvider<FileStorageEngine> fileStorageEngineProvider,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<MongoClient> mongoClientProvider,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.resourceModeInfo = resourceModeInfo;
        this.fileStorageEngineProvider = fileStorageEngineProvider;
        this.dataSourceProvider = dataSourceProvider;
        this.mongoClientProvider = mongoClientProvider;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("resourceMode", resourceModeInfo.mode())
                .withDetail("description", resourceModeInfo.description());

        if ("low".equalsIgnoreCase(resourceModeInfo.mode())) {
            FileStorageEngine fileStorageEngine = fileStorageEngineProvider.getIfAvailable();
            if (fileStorageEngine != null) {
                builder.withDetail("storage", "file")
                        .withDetail("dataPath", fileStorageEngine.getProperties().getBasePath())
                        .withDetail("collectionsLoaded", fileStorageEngine.getRegisteredCollectionCount())
                        .withDetail("collectionNames", fileStorageEngine.getRegisteredCollectionNames())
                        .withDetail("totalRecordsInMemory", fileStorageEngine.getTotalRecordsInMemory());
            }

            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource != null) {
                builder.withDetail("batchMetadataDatabase", validateDataSource(dataSource));
            }
            return builder.build();
        }

        builder.withDetail("storage", "mongo-postgres-redis")
                .withDetail("mongodb", validateMongo())
                .withDetail("postgresql", validateDataSource(dataSourceProvider.getIfAvailable()))
                .withDetail("redis", validateRedis());
        return builder.build();
    }

    private Object validateDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return "unavailable";
        }

        try (java.sql.Connection connection = dataSource.getConnection()) {
            return connection.isValid(2)
                    ? connection.getMetaData().getURL()
                    : "invalid";
        } catch (Exception e) {
            return "unavailable: " + e.getClass().getSimpleName();
        }
    }

    private Object validateMongo() {
        MongoClient mongoClient = mongoClientProvider.getIfAvailable();
        if (mongoClient == null) {
            return "disabled";
        }

        try {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            return "reachable";
        } catch (Exception e) {
            return "unavailable: " + e.getClass().getSimpleName();
        }
    }

    private Object validateRedis() {
        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory == null) {
            return "disabled";
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.ping();
        } catch (Exception e) {
            return "unavailable: " + e.getClass().getSimpleName();
        }
    }
}
